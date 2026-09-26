package de.tyro.project11.portal;

import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** A history of persisted facts, including old applications; viewing never advances the workflow. */
@Service
@Transactional(readOnly = true)
public class CaseFileService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm z", Locale.GERMAN);
    private final AbsenceApplicationRepository absences;
    private final TravelApplicationRepository travels;
    private final UserRepository users;
    public CaseFileService(AbsenceApplicationRepository absences, TravelApplicationRepository travels, UserRepository users) {
        this.absences = absences; this.travels = travels; this.users = users;
    }
    public record Entry(OffsetDateTime at, String title, String actor, String detail, String url) {
        public String stamp() { return format(at); }
    }
    public record File(String reference, String title, String applicant, String subject, String submitted,
                       String status, String decision, String decided, String official, String reason,
                       String effect, String url, List<Entry> history) {
        public boolean issued() { return !decision.equals("PENDING"); }
        public String outcome() { return CaseReference.decision(decision); }
    }
    public File absence(long id, String email) {
        requireMember(email);
        var a = absences.findById(id).orElseThrow(() -> missing());
        var history = new ArrayList<Entry>();
        history.add(new Entry(a.getSubmittedAt(), "Antrag eingegangen", a.getApplicant().getDisplayName(),
                "Unveränderlich unter " + CaseReference.of(a) + " registriert.", null));
        addDecision(history, a.getDecision().name(), a.getDecidedAt(), a.getDecidedBy(), a.getDecisionReason(), null);
        if (a.getActivity().isCancelled()) history.add(new Entry(a.getActivity().getCancelledAt(), "Event abgesagt", "Eventverwaltung",
                "Ein AaA ist für dieses Event nicht mehr erforderlich. " + a.getActivity().getCancellationReason(), "/events/" + a.getActivity().getId()));
        String effect = a.getDecision() == AbsenceApplication.Decision.ACCEPTED
                ? "Die Abwesenheit ist für die bezeichnete Aktivität entschuldigt."
                : "Der Antrag entschuldigt die Abwesenheit nicht. Es gelten die Anwesenheits- und Fristenregeln der Gruppe.";
        if (a.getActivity().isCancelled()) effect = "Das betroffene Event wurde inzwischen abgesagt. Ein AaA ist nicht mehr erforderlich; es entstehen keine Event-Striche. Die ursprüngliche Entscheidung bleibt dokumentiert.";
        return new File(CaseReference.of(a), "Antrag auf Abwesenheit", a.getApplicant().getDisplayName(), a.getActivityTitle(),
                format(a.getSubmittedAt()), CaseReference.status(a), a.getDecision().name(), format(a.getDecidedAt()),
                official(a.getDecidedBy()), a.getDecisionReason(), effect, "/amt/antraege/" + id, sorted(history));
    }
    public File travel(long id, String email) {
        requireMember(email);
        var a = travels.findById(id).orElseThrow(() -> missing());
        var history = new ArrayList<Entry>();
        history.add(new Entry(a.getSubmittedAt(), "Vorgang eingegangen", a.getApplicant().getDisplayName(),
                "Unveränderlich unter " + CaseReference.of(a) + " registriert.", null));
        addDecision(history, a.getDecision().name(), a.getDecidedAt(), a.getDecidedBy(), a.getDecisionReason(), null);
        var relatedKind = a.getKind() == TravelKind.LEAVE ? TravelKind.REPORT : TravelKind.LEAVE;
        travels.findByKindAndHolidayId(relatedKind, a.getHoliday().getId()).ifPresent(related -> {
            String url = "/amt/reisen/" + related.getId();
            history.add(new Entry(related.getSubmittedAt(), "Zugehöriger " + related.getKind().code + " eingegangen",
                    related.getApplicant().getDisplayName(), CaseReference.of(related), url));
            addDecision(history, related.getDecision().name(), related.getDecidedAt(), related.getDecidedBy(),
                    CaseReference.of(related) + (related.getDecisionReason().isBlank() ? "" : ": " + related.getDecisionReason()), url);
        });
        if (a.getHoliday().isInvalidated()) history.add(new Entry(a.getHoliday().getInvalidatedAt(), "Beurlaubung verfallen", "Automatische Fristenprüfung",
                "Die Beurlaubung wurde wegen der Berichtsregeln ungültig. Es gelten die Tagesstriche der Gruppe.", null));
        String effect = a.getKind() == TravelKind.LEAVE
                ? "Eine angenommene Beurlaubung steht unter dem Vorbehalt eines fristgerecht eingereichten und angenommenen Reiseberichts. Bei einem abgelehnten AaB gelten die AaA-Regeln für betroffene Events."
                : "Die Entscheidung betrifft den Reisebericht. Eine verspätete Einreichung wird durch die Annahme nicht nachträglich fristgerecht.";
        if (a.getHoliday().isInvalidated()) effect += " Die zugehörige Beurlaubung ist inzwischen verfallen; diese Ausfertigung hebt den Verfall nicht auf.";
        return new File(CaseReference.of(a), a.getKind().title, a.getApplicant().getDisplayName(), a.getSubject(),
                format(a.getSubmittedAt()), CaseReference.status(a), a.getDecision().name(), format(a.getDecidedAt()),
                official(a.getDecidedBy()), a.getDecisionReason(), effect, "/amt/reisen/" + id, sorted(history));
    }
    public File requireNotice(File file) {
        if (!file.issued()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Noch kein Bescheid: Dieser Vorgang wurde noch nicht entschieden. Der Eingang ist in der Akte dokumentiert.");
        return file;
    }
    private void addDecision(List<Entry> history, String decision, OffsetDateTime at, AppUser official, String reason, String url) {
        if (!decision.equals("PENDING")) history.add(new Entry(at, (url == null ? "Entscheidung: " : "Entscheidung zur verknüpften Akte: ")
                + CaseReference.decision(decision), official(official), reason.isBlank() ? "Keine zusätzliche Begründung hinterlegt." : reason, url));
    }
    private static List<Entry> sorted(List<Entry> history) {
        history.sort(Comparator.comparing(e -> e.at() == null ? java.time.Instant.MIN : e.at().toInstant()));
        return List.copyOf(history);
    }
    private static String format(OffsetDateTime at) { return at == null ? "Nicht dokumentiert" : at.atZoneSameInstant(CalendarTime.BERLIN).format(STAMP); }
    private static String official(AppUser user) { return user == null ? "Nicht dokumentiert" : user.getDisplayName(); }
    private void requireMember(String email) {
        if (users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).isEmpty()) throw new AccessDeniedException("Konto nicht gefunden.");
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Akte wurde nicht gefunden."); }
}
