package de.tyro.project11.portal;

import de.tyro.project11.attendance.AttendanceRules;
import de.tyro.project11.calendar.Activity;
import de.tyro.project11.calendar.ActivityRepository;
import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class PortalService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm z", Locale.GERMAN);
    private final UserRepository users;
    private final ActivityRepository activities;
    private final AbsenceApplicationRepository applications;
    private final Clock clock;
    private final TravelApplicationRepository travelApplications;
    private final TravelService travel;

    public PortalService(UserRepository users, ActivityRepository activities,
                         AbsenceApplicationRepository applications, Clock clock,
                         TravelApplicationRepository travelApplications, TravelService travel) {
        this.users = users;
        this.activities = activities;
        this.applications = applications;
        this.clock = clock;
        this.travelApplications = travelApplications;
        this.travel = travel;
    }

    @Transactional(readOnly = true)
    public PortalViews.Citizen citizen(String email) {
        var user = user(email);
        long count = applications.countByApplicantId(user.getId());
        return new PortalViews.Citizen(user.getId(), user.getDisplayName(), participantNumber(user.getId()),
                count + travelApplications.countByApplicantId(user.getId()),
                (int) Math.max(0, 87 - Math.min(count + travelApplications.countByApplicantIdAndKind(user.getId(), TravelKind.LEAVE), 25) * 4));
    }

    @Transactional(readOnly = true)
    public List<PortalViews.Member> members() {
        return users.findAllByOrderByDisplayNameAsc().stream()
                .map(user -> new PortalViews.Member(user.getId(), user.getDisplayName(), participantNumber(user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PortalViews.Member member(long id) {
        var user = users.findById(id).orElseThrow(() -> missing("Die Teilnehmerakte wurde nicht gefunden."));
        return new PortalViews.Member(user.getId(), user.getDisplayName(), participantNumber(id));
    }

    @Transactional(readOnly = true)
    public Page<PortalViews.Summary> archive(Long memberId, int page) {
        if (page < 0 || page > 100000) {
            throw badRequest("Die angeforderte Aktenseite ist unzulässig.");
        }
        var pageable = PageRequest.of(page, 20);
        long total = memberId == null ? applications.count() + travelApplications.count()
                : applications.countByApplicantId(memberId) + travelApplications.countByApplicantId(memberId);
        if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        // Fetch the leading slice of both registries before applying the shared page boundary.
        var leading = PageRequest.of(0, (page + 1) * 20);
        var absences = memberId == null ? applications.findAllByOrderBySubmittedAtDescIdDesc(leading)
                : applications.findByApplicantIdOrderBySubmittedAtDescIdDesc(memberId, leading);
        var trips = memberId == null ? travelApplications.findAllByOrderBySubmittedAtDescIdDesc(leading)
                : travelApplications.findByApplicantIdOrderBySubmittedAtDescIdDesc(memberId, leading);
        var records = Stream.concat(absences.stream().map(this::summary), trips.stream().map(travel::summary))
                .sorted(Comparator.comparing(PortalViews.Summary::submittedInstant).reversed()
                        .thenComparing(PortalViews.Summary::kind).thenComparing(Comparator.comparingLong(PortalViews.Summary::id).reversed()))
                .skip(pageable.getOffset()).limit(20).toList();
        return new PageImpl<>(records, pageable, total);
    }

    @Transactional(readOnly = true)
    public PortalViews.File file(long id) {
        var application = applications.findById(id).orElseThrow(() -> missing("Das Aktenzeichen wurde nicht gefunden."));
        return new PortalViews.File(summary(application), sections(application.getAnswers()));
    }

    @Transactional(readOnly = true)
    public List<PortalViews.ActivityOption> availableActivities(String email) {
        long userId = user(email).getId();
        var now = ZonedDateTime.now(clock.withZone(CalendarTime.BERLIN));
        // Load a broad time window, then apply the exact Berlin calendar-date cutoff.
        return activities.findByEndsAtAfterOrderByStartsAtAscIdAsc(now.minusWeeks(1).minusDays(1).toOffsetDateTime()).stream()
                .filter(activity -> !activity.isCancelled() && now.isBefore(deadline(activity)))
                .filter(activity -> applications.findByApplicantIdAndActivityId(userId, activity.getId()).isEmpty())
                .map(activity -> new PortalViews.ActivityOption(activity.getId(), activity.getTitle(),
                        activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).toLocalDate().toString(),
                        (activity.isAllDay() ? activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).toLocalDate() + " · ganztägig"
                                : !activity.hasStartTime() ? "Bis " + stamp(activity.getEndsAt())
                                : stamp(activity.getStartsAt()) + (activity.hasEndTime() ? " – " + stamp(activity.getEndsAt()) : "")), deadline(activity).format(STAMP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public void validate(AbsenceForm form, PortalQuestions.Questionnaire questions, String email, BindingResult errors) {
        if (form.getActivityId() != null) {
            var activity = activities.findById(form.getActivityId()).orElse(null);
            if (activity == null || activity.isCancelled()) {
                errors.rejectValue("activityId", "unknown", "A.2: Diese Gruppenaktivität ist nicht aktenkundig.");
            } else {
                if (!ZonedDateTime.now(clock.withZone(CalendarTime.BERLIN)).isBefore(deadline(activity))) {
                    errors.rejectValue("activityId", "expired", "A.2: Die Einreichungsfrist ist abgelaufen. Nachreichungen sind ausgeschlossen.");
                }
                if (applications.findByApplicantIdAndActivityId(user(email).getId(), activity.getId()).isPresent()) {
                    errors.rejectValue("activityId", "duplicate", "A.2: Für diese Aktivität liegt bereits Ihr Antrag vor.");
                }
                if (form.getActivityDate() != null && !form.getActivityDate().equals(
                        activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).toLocalDate())) {
                    errors.rejectValue("activityDate", "mismatch", "A.3: Das Datum muss dem Beginn der ausgewählten Aktivität entsprechen (Berlin).");
                }
            }
        }
        if (form.getKnowledgeAnswer() != null && !questions.knowledge().correctAnswer().equals(form.getKnowledgeAnswer())) {
            errors.rejectValue("knowledgeAnswer", "incorrect", "D.1: Wissensnachweis nicht erbracht. Bitte prüfen Sie Ihre Antwort erneut.");
        }
        if (form.getLoyaltyAnswer() != null && !PortalQuestions.LOYALTY_OPTIONS.contains(form.getLoyaltyAnswer())) {
            errors.rejectValue("loyaltyAnswer", "invalid", "D.3: Bitte eine der amtlich zugelassenen Antworten auswählen.");
        }
        if (questions.audit() && form.getAuditAnswer().isBlank()) {
            errors.rejectValue("auditAnswer", "required", "D.4: Die seltene Aktualitätsprüfung ist ebenfalls auszufüllen.");
        }
        if (questions.extraSeventeen() && form.getDetailedReason() != null
                && form.getDetailedReason().length() >= 50 && form.getDetailedReason().length() < 67) {
            errors.rejectValue("detailedReason", "seventeen", "B.2: Formale Unstimmigkeit: In Ihrem Vorgang verlangt Referat D 17 zusätzliche Zeichen (mindestens 67 insgesamt).");
        }

    }

    @Transactional
    public long submit(String email, AbsenceForm form, PortalQuestions.Questionnaire questions) {
        if (!form.isAcceptedTerms()) throw badRequest("Bitte die Allgemeinen Gruppenbedingungen im Entwurf akzeptieren und erneut prüfen.");
        var applicant = user(email);
        // Match the lock order of settlement and reviews, including at the deadline boundary.
        var activity = activities.findLockedById(form.getActivityId()).orElseThrow(() -> missing("Die Aktivität wurde nicht gefunden."));
        if (activity.isCancelled()) throw badRequest("Dieses Event wurde abgesagt. Ein AaA ist nicht erforderlich.");
        applicant = users.findLockedById(applicant.getId()).orElseThrow(() -> new AccessDeniedException("Das Konto besteht nicht mehr."));
        var existing = applications.findByApplicantIdAndActivityId(applicant.getId(), form.getActivityId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        var submittedAt = OffsetDateTime.now(clock.withZone(CalendarTime.BERLIN));
        if (!submittedAt.toInstant().isBefore(deadline(activity).toInstant())) {
            throw badRequest("Die Einreichungsfrist ist inzwischen abgelaufen. Eine nachträgliche Einreichung ist nicht möglich.");
        }
        if (!form.getActivityDate().equals(activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).toLocalDate())) {
            throw badRequest("Der Aktivitätstermin hat sich geändert. Bitte den Entwurf erneut prüfen.");
        }
        return applications.saveAndFlush(new AbsenceApplication(applicant, activity, submittedAt, answers(form, questions, activity))).getId();
    }

    @Transactional(readOnly = true)
    public List<PortalViews.Section> preview(AbsenceForm form, PortalQuestions.Questionnaire questions) {
        var activity = activities.findById(form.getActivityId()).orElseThrow(() -> missing("Die Aktivität wurde nicht gefunden."));
        return sections(answers(form, questions, activity));
    }

    private List<ApplicationAnswer> answers(AbsenceForm form, PortalQuestions.Questionnaire questions, Activity activity) {
        List<ApplicationAnswer> answers = new ArrayList<>();
        String core = "A. Angaben zum Abwesenheitsvorgang";
        answers.add(new ApplicationAnswer(core, "Name", form.getApplicantName()));
        answers.add(new ApplicationAnswer(core, "Aktivität", activity.getTitle()));
        answers.add(new ApplicationAnswer(core, "Datum der Aktivität (Berlin)", form.getActivityDate().format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))));
        answers.add(new ApplicationAnswer(core, "Zielort", form.getDestination()));
        answers.add(new ApplicationAnswer(core, "Begleitpersonen", form.getCompanions()));
        answers.add(new ApplicationAnswer(core, "Grund der Abwesenheit", form.getReason()));
        answers.add(new ApplicationAnswer(core, "Verkehrsmittel", form.getTransport()));
        answers.add(new ApplicationAnswer(core, "Geplante Verpflegung", form.getCatering()));
        answers.add(new ApplicationAnswer("B. Begründungswesen", "Warum ist diese Aktivität wichtiger als die Teilnahme an der Gruppenaktivität?", form.getPriorityReason()));
        answers.add(new ApplicationAnswer("B. Begründungswesen", "Ausführliche Begründung der privaten Prioritätensetzung", form.getDetailedReason()));
        for (int i = 0; i < 3; i++) {
            answers.add(new ApplicationAnswer("C. Ergänzende, sachlich entbehrliche Angaben", questions.personal().get(i), form.getPersonalAnswers().get(i).strip()));
        }
        answers.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", questions.knowledge().question(), form.getKnowledgeAnswer()));
        answers.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", questions.absurd(), form.getAbsurdAnswer()));
        answers.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", questions.loyalty(), form.getLoyaltyAnswer()));
        if (questions.audit()) {
            answers.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", "Aktualitätsprüfung: Bitte Ihre bevorzugte Nudelform erneut bestätigen.", form.getAuditAnswer()));
        }
        answers.add(new ApplicationAnswer("E. Bestätigungswesen", "Bestätigung der Angaben, der Bestätigung, des Verständnisses und aller drei Bestätigungen", "Vierfach bestätigt."));
        answers.add(PortalTerms.acceptedSnapshot());
        return answers;
    }

    private List<PortalViews.Section> sections(List<ApplicationAnswer> answers) {
        var grouped = new LinkedHashMap<String, List<ApplicationAnswer>>();
        answers.forEach(answer -> grouped.computeIfAbsent(answer.getSection(), key -> new ArrayList<>()).add(answer));
        return grouped.entrySet().stream().map(entry -> new PortalViews.Section(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    private PortalViews.Summary summary(AbsenceApplication application) {
        return new PortalViews.Summary(application.getId(), CaseReference.of(application), application.getApplicant().getId(),
                application.getApplicant().getDisplayName(), application.getActivityTitle(), stamp(application.getSubmittedAt()),
                CaseReference.status(application), "AaA", "/amt/antraege/" + application.getId(), application.getSubmittedAt().toInstant());
    }

    private AppUser user(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Das angemeldete Konto besteht nicht mehr."));
    }

    private ZonedDateTime deadline(Activity activity) {
        return AttendanceRules.deadline(activity);
    }

    private String stamp(OffsetDateTime value) { return value.atZoneSameInstant(CalendarTime.BERLIN).format(STAMP); }
    private String participantNumber(long id) { return String.format(Locale.ROOT, "TN-%06d", id); }
    private ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
