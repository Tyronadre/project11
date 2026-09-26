package de.tyro.project11.portal;

import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.calendar.Holiday;
import de.tyro.project11.calendar.HolidayRepository;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class TravelService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMAN);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm z", Locale.GERMAN);
    private final TravelApplicationRepository applications;
    private final HolidayRepository holidays;
    private final TravelPhotoRepository photos;
    private final UserRepository users;
    private final Clock clock;
    private final TravelPenaltyService penalties;
    public TravelService(TravelApplicationRepository applications, HolidayRepository holidays, TravelPhotoRepository photos,
                         UserRepository users, Clock clock, TravelPenaltyService penalties) {
        this.applications = applications; this.holidays = holidays; this.photos = photos; this.users = users; this.clock = clock;
        this.penalties = penalties;
    }

    public record HolidayOption(long id, String label, String deadline, boolean overdue) {}
    public record ReportReminder(long holidayId, String title, String period, String deadline, Instant dueAt,
                                 boolean overdue, boolean canFile, String availableOn) {}

    @Transactional(readOnly = true)
    public List<ReportReminder> reportReminders(String email) {
        return holidays.findByUserIdOrderByEndsOnAscIdAsc(user(email).getId()).stream()
                .filter(this::mayFileReport)
                .filter(holiday -> !applications.existsByKindAndHolidayId(TravelKind.REPORT, holiday.getId()))
                .map(holiday -> new ReportReminder(holiday.getId(), holiday.getTitle(), period(holiday), deadlineLabel(holiday),
                        deadline(holiday).toInstant(), !clock.instant().isBefore(deadline(holiday).toInstant()),
                        holiday.getEndsOn().isBefore(today()), holiday.getEndsOn().plusDays(1).format(DAY)))
                .toList();
    }
    public record TravelFile(TravelKind kind, PortalViews.Summary summary, List<PortalViews.Section> sections,
                             List<TravelPhotoRepository.Info> photos, String holidayPeriod, String deadline,
                             boolean recognized, boolean late, Long leaveId, Long reportId, long holidayId, boolean mayReport) {}

    @Transactional(readOnly = true)
    public List<HolidayOption> reportableHolidays(String email) {
        return holidays.findByUserIdAndEndsOnBeforeOrderByEndsOnDesc(user(email).getId(), today()).stream()
                .filter(this::mayFileReport)
                .filter(holiday -> !applications.existsByKindAndHolidayId(TravelKind.REPORT, holiday.getId()))
                .map(holiday -> new HolidayOption(holiday.getId(), holiday.getTitle() + " · " + period(holiday),
                        deadlineLabel(holiday), !clock.instant().isBefore(deadline(holiday).toInstant())))
                .toList();
    }

    @Transactional(readOnly = true)
    public void validate(TravelDraft draft, String email, BindingResult errors) {
        var form = draft.getForm();
        for (var field : TravelFields.forKind(draft.getKind())) {
            String value = form.value(field.key());
            String path = "values[" + field.key() + "]";
            if (value.isEmpty()) {
                errors.rejectValue(path, "required", field.label() + ": Pflichtangabe fehlt.");
                continue;
            }
            if (field.type().equals("number")) {
                try {
                    int number = Integer.parseInt(value);
                    if (number < field.min() || number > field.max()) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    errors.rejectValue(path, "range", field.label() + ": Ganze Zahl zwischen " + field.min() + " und " + field.max() + " erforderlich.");
                }
            } else if (value.length() < field.min() || value.length() > field.max()) {
                errors.rejectValue(path, "length", field.label() + ": " + field.min() + " bis " + field.max() + " Zeichen erforderlich.");
            }
            if (!field.options().isEmpty() && !field.options().contains(value)) {
                errors.rejectValue(path, "option", field.label() + ": Bitte eine amtlich vorgesehene Antwort auswählen.");
            }
        }
        if (draft.getKind() == TravelKind.LEAVE) {
            LocalDate start = validDate(form, "startsOn", errors), end = validDate(form, "endsOn", errors);
            if (start != null && end != null && end.isBefore(start)) {
                errors.rejectValue("values[endsOn]", "order", "Das Urlaubsende muss auf oder nach dem Beginn liegen.");
            }
        } else {
            try { reportHoliday(form, email); }
            catch (ResponseStatusException exception) { errors.rejectValue("values[holidayId]", "holiday", exception.getReason()); }
        }
        var q = draft.getQuestions();
        if (!q.knowledge().correctAnswer().equals(form.getKnowledgeAnswer())) errors.rejectValue("knowledgeAnswer", "incorrect", "Wissensnachweis nicht erbracht. Bitte Antwort prüfen.");
        if (form.getLoyaltyAnswer() == null || !PortalQuestions.LOYALTY_OPTIONS.contains(form.getLoyaltyAnswer())) errors.rejectValue("loyaltyAnswer", "option", "Bitte eine amtliche Loyalitätseinschätzung auswählen.");
        if (q.audit() && form.getAuditAnswer().isBlank()) errors.rejectValue("auditAnswer", "required", "Die seltene Aktualitätsprüfung fehlt.");
    }

    @Transactional
    public long submit(TravelDraft draft, String email) {
        var owner = user(email);
        owner = users.findLockedById(owner.getId()).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
        var alreadyFiled = applications.findByFilingKeyAndApplicantId(draft.getId(), owner.getId());
        if (alreadyFiled.isPresent()) return alreadyFiled.get().getId();
        var form = draft.getForm();
        if (!form.isAcceptedTerms()) throw badRequest("Bitte die Allgemeinen Gruppenbedingungen im Entwurf akzeptieren und erneut prüfen.");
        var now = OffsetDateTime.now(clock.withZone(CalendarTime.BERLIN));
        Holiday holiday;
        if (draft.getKind() == TravelKind.LEAVE) {
            holiday = holidays.save(new Holiday(owner, form.value("destination"), form.value("purpose"),
                    LocalDate.parse(form.value("startsOn")), LocalDate.parse(form.value("endsOn")), now));
        } else {
            long holidayId = holidayId(form);
            var existing = applications.findByKindAndHolidayId(TravelKind.REPORT, holidayId);
            if (existing.isPresent() && existing.get().getApplicant().getId().equals(owner.getId())) return existing.get().getId();
            holiday = reportHoliday(form, email);
            int count = photos.findByDraftKeyAndOwnerIdAndApplicationIsNullOrderByIdAsc(draft.getId(), owner.getId()).size();
            if (count < 3 || count > 6) throw badRequest("Der Bericht benötigt drei bis sechs Fotos. Bitte den Entwurf erneut prüfen.");
        }
        var application = applications.saveAndFlush(new TravelApplication(draft.getKind(), draft.getId(), owner, holiday,
                holiday.getTitle() + " · " + period(holiday), now, answers(draft, holiday)));
        if (draft.getKind() == TravelKind.REPORT) {
            int filedPhotos = photos.fileDraft(draft.getId(), owner.getId(), application);
            if (filedPhotos < 3 || filedPhotos > 6) throw badRequest("Entwurfsfotos sind inzwischen abgelaufen. Bitte den Entwurf erneut prüfen und Fotos ergänzen.");
            penalties.reconcile(owner.getId());
        }
        return application.getId();
    }

    @Transactional(readOnly = true)
    public List<PortalViews.Section> preview(TravelDraft draft, String email) {
        Holiday holiday = draft.getKind() == TravelKind.REPORT ? reportHoliday(draft.getForm(), email) : null;
        return sections(answers(draft, holiday));
    }

    @Transactional(readOnly = true)
    public TravelFile file(long id, String email) {
        var application = applications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Reiseakte wurde nicht gefunden."));
        var holiday = application.getHoliday();
        var leave = applications.findByKindAndHolidayId(TravelKind.LEAVE, holiday.getId());
        var report = applications.findByKindAndHolidayId(TravelKind.REPORT, holiday.getId());
        boolean late = report.isPresent() && !report.get().getSubmittedAt().toInstant().isBefore(deadline(holiday).toInstant());
        return new TravelFile(application.getKind(), summary(application), sections(application.getAnswers()),
                photos.findByApplicationIdOrderByIdAsc(id), period(holiday), deadlineLabel(holiday),
                !holiday.isInvalidated() && report.filter(r -> TravelRules.timely(r) && r.getDecision() == TravelApplication.Decision.ACCEPTED).isPresent(), late,
                leave.map(TravelApplication::getId).orElse(null), report.map(TravelApplication::getId).orElse(null), holiday.getId(),
                report.isEmpty() && mayFileReport(holiday) && holiday.getUser().getId().equals(user(email).getId()) && holiday.getEndsOn().isBefore(today()));
    }

    public PortalViews.Summary summary(TravelApplication application) {
        var kind = application.getKind();
        return new PortalViews.Summary(application.getId(), CaseReference.of(application), application.getApplicant().getId(),
                application.getApplicant().getDisplayName(), application.getSubject(), application.getSubmittedAt().atZoneSameInstant(CalendarTime.BERLIN).format(STAMP),
                CaseReference.status(application), kind.code, "/amt/reisen/" + application.getId(), application.getSubmittedAt().toInstant());
    }

    private List<ApplicationAnswer> answers(TravelDraft draft, Holiday holiday) {
        var form = draft.getForm();
        List<ApplicationAnswer> result = new ArrayList<>();
        String main = draft.getKind() == TravelKind.LEAVE ? "A. Beurlaubungsangelegenheiten" : "A. Reiseberichterstattung";
        if (holiday != null) result.add(new ApplicationAnswer(main, "Zugeordnete Beurlaubung", holiday.getTitle() + " · " + period(holiday)));
        TravelFields.forKind(draft.getKind()).forEach(field -> result.add(new ApplicationAnswer(main, field.label(), form.value(field.key()))));
        var q = draft.getQuestions();
        for (int i = 0; i < 3; i++) result.add(new ApplicationAnswer("C. Sachlich entbehrliche Angaben", q.personal().get(i), form.getPersonalAnswers().get(i).strip()));
        result.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", q.knowledge().question(), form.getKnowledgeAnswer()));
        result.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", q.absurd(), form.getAbsurdAnswer()));
        result.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", q.loyalty(), form.getLoyaltyAnswer()));
        if (q.audit()) result.add(new ApplicationAnswer("D. Eignungs- und Loyalitätsprüfung", "Erneute Bestätigung Ihrer bevorzugten Nudelform", form.getAuditAnswer()));
        result.add(new ApplicationAnswer("E. Bestätigungswesen", "Bestätigung der Bestätigungen", "Vierfach bestätigt."));
        result.add(PortalTerms.acceptedSnapshot());
        return result;
    }

    private List<PortalViews.Section> sections(List<ApplicationAnswer> answers) {
        var grouped = new LinkedHashMap<String, List<ApplicationAnswer>>();
        answers.forEach(answer -> grouped.computeIfAbsent(answer.getSection(), ignored -> new ArrayList<>()).add(answer));
        return grouped.entrySet().stream().map(entry -> new PortalViews.Section(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    private LocalDate validDate(TravelForm form, String key, BindingResult errors) {
        try {
            String value = form.value(key);
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("format");
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1 || date.getYear() > 9998) throw new DateTimeException("year");
            return date;
        } catch (DateTimeException exception) { errors.rejectValue("values[" + key + "]", "date", "Bitte ein gültiges Datum (Jahr 0001–9998) angeben."); return null; }
    }

    private long holidayId(TravelForm form) {
        try { return Long.parseLong(form.value("holidayId")); }
        catch (NumberFormatException exception) { throw badRequest("Bitte eine eigene, bereits beendete Beurlaubung auswählen."); }
    }

    private Holiday reportHoliday(TravelForm form, String email) {
        var holiday = holidays.findById(holidayId(form)).orElseThrow(() -> badRequest("Die Beurlaubung wurde nicht gefunden."));
        if (!holiday.getUser().getId().equals(user(email).getId())) throw badRequest("Reiseberichte können nur für Ihre eigenen Beurlaubungen eingereicht werden.");
        if (!holiday.getEndsOn().isBefore(today())) throw badRequest("Der Reisebericht ist erst nach dem letzten Urlaubstag möglich.");
        if (!mayFileReport(holiday)) throw badRequest("Für einen abgelehnten AaB kann kein Reisebericht eingereicht werden.");
        if (applications.existsByKindAndHolidayId(TravelKind.REPORT, holiday.getId())) throw badRequest("Für diese Beurlaubung liegt bereits ein Reisebericht vor.");
        return holiday;
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(CalendarTime.BERLIN)); }
    private boolean mayFileReport(Holiday holiday) {
        // Filing while the AaB is still pending preserves the deadline despite admin delays.
        // Confirmation of that report still requires acceptance of its AaB.
        return applications.findByKindAndHolidayId(TravelKind.LEAVE, holiday.getId())
                .map(a -> a.getDecision() != TravelApplication.Decision.REJECTED).orElse(true);
    }
    private ZonedDateTime deadline(Holiday holiday) { return TravelRules.deadline(holiday); }
    private String deadlineLabel(Holiday holiday) { return holiday.getEndsOn().plusDays(7).format(DAY) + ", 23:59 Uhr (Berlin)"; }
    private String period(Holiday holiday) { return holiday.getStartsOn().format(DAY) + " – " + holiday.getEndsOn().format(DAY); }
    private ResponseStatusException badRequest(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
}
