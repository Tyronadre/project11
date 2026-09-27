package de.tyro.project11.portal.leisure;

import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.CaseReference;
import de.tyro.project11.registration.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** These procedures only write their own records. Activities are read for optional retrospective certificates. */
@Service
public class LeisureService {
    public static final List<String> ENTHUSIASM = List.of("erheblich", "kaum in Worte zu fassen", "vorsichtig optimistisch", "erst im Nachhinein erkennbar");
    private static final List<String> ROUTE = List.of("Referat C: Eingang und ungeprüfte Weitergabe",
            "Unterausschuss Freizeit: Zuständigkeit vorsorglich verneint",
            "Zurück an Referat C: Rückleitung zur abschließenden Nichtzuständigkeit",
            "Die Nichtzuständigkeit wurde zweifelsfrei festgestellt.");
    private final LeisureCaseRepository cases;
    private final LostPropertyRepository property;
    private final UserRepository users;
    private final ActivityRepository activities;
    private final Clock clock;
    private final Validator validator;
    public LeisureService(LeisureCaseRepository cases, LostPropertyRepository property, UserRepository users,
                          ActivityRepository activities, Clock clock, Validator validator) {
        this.cases = cases; this.property = property; this.users = users; this.activities = activities;
        this.clock = clock; this.validator = validator;
    }
    public record EventChoice(long id, String title, String ended) {}
    public record CaseView(long id, String reference, LeisureKind kind, String applicant, String subject,
                           String explanation, Long activityId, String created, String completed, String outcome,
                           boolean owner, int stage, List<String> route, int ahead, boolean waitingReady,
                           boolean leftWaitingRoom) {
        public boolean finished() { return completed != null; }
        public boolean waiting() { return kind == LeisureKind.WAITING; }
        public boolean jurisdiction() { return kind == LeisureKind.JURISDICTION; }
        public boolean printable() { return finished() && !waiting(); }
    }
    public record PropertyView(long id, String reference, String applicant, boolean lost, String subject,
                               String description, Long parentId, String created, String resolved, boolean owner) {}
    public record Statistics(long forms, long finished, long waitingTickets, long propertyNotices, long paperclips,
                             String effort, String insight) {}

    @Transactional(readOnly = true)
    public List<EventChoice> events(String email) {
        var user = user(email);
        return activities.findByEndsAtLessThanEqualOrderByEndsAtDescIdDesc(OffsetDateTime.now(clock)).stream()
                .filter(e -> eligible(e, user)).map(e -> new EventChoice(e.getId(), e.getTitle(), stamp(e.getEndsAt().toInstant()))).toList();
    }
    public String formProblem(LeisureKind kind, LeisureForm form) {
        if (!validator.validate(form).isEmpty()) return "Bitte die Formularangaben prüfen und die Zeichenbegrenzungen beachten.";
        if ((kind == LeisureKind.NICKNAME || kind == LeisureKind.JURISDICTION) && form.getSubject().isBlank())
            return kind == LeisureKind.NICKNAME ? "Bitte den gewünschten Spitznamen angeben." : "Bitte ein Anliegen benennen.";
        if (kind == LeisureKind.NICKNAME && form.getExplanation().isBlank()) return "Bitte die besondere Eignung dieses Spitznamens begründen.";
        if (kind == LeisureKind.ANTICIPATION && (form.getActivityId() == null || !ENTHUSIASM.contains(form.getEnthusiasm())))
            return "Bitte ein vergangenes Treffen und das Ausmaß der Vorfreude auswählen.";
        return null;
    }
    @Transactional
    public long submit(LeisureKind kind, LeisureForm form, String email) {
        String problem = formProblem(kind, form);
        if (problem != null) throw error(HttpStatus.BAD_REQUEST, problem);
        if (kind == LeisureKind.WAITING) throw error(HttpStatus.BAD_REQUEST, "Bitte am Wartezimmer eine Marke ziehen.");
        var user = lockedUser(email);
        var existing = cases.findByOwnerIdAndSubmissionToken(user.getId(), form.getToken());
        if (existing.isPresent()) {
            if (existing.get().getKind() != kind) throw error(HttpStatus.CONFLICT, "Dieses Formular wurde bereits für einen anderen Vorgang verwendet.");
            return existing.get().getId();
        }
        Long activityId = null;
        String enthusiasm = "";
        String subject = switch (kind) {
            case PERMISSION -> "Nachträgliche Berechtigung zur Stellung dieses Antrags";
            case CERTIFICATE -> "Nichtvorliegen eines Bescheinigungsbedarfs";
            default -> form.getSubject();
        };
        if (kind == LeisureKind.ANTICIPATION) {
            var event = activities.findById(form.getActivityId()).orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Das ausgewählte Treffen wurde nicht gefunden."));
            if (!eligible(event, user)) throw error(HttpStatus.BAD_REQUEST, "Bitte ein beendetes, nicht abgesagtes Treffen aus deiner Mitgliedszeit auswählen.");
            activityId = event.getId(); subject = event.getTitle(); enthusiasm = form.getEnthusiasm();
        }
        return cases.save(new LeisureCase(user.getId(), form.getToken(), kind, user.getDisplayName(), subject,
                form.getExplanation(), activityId, enthusiasm, clock.instant())).getId();
    }
    @Transactional
    public long takeTicket(String email) {
        var user = lockedUser(email);
        return cases.findFirstByOwnerIdAndKindAndCompletedAtIsNull(user.getId(), LeisureKind.WAITING)
                .orElseGet(() -> cases.save(new LeisureCase(user.getId(), UUID.randomUUID().toString(), LeisureKind.WAITING,
                        user.getDisplayName(), "Freiwilliger Aufenthalt an Schalter 3", "", null, "", clock.instant()))).getId();
    }
    @Transactional(readOnly = true)
    public Page<CaseView> list(String email, int page) {
        var user = user(email);
        return cases.findByOwnerIdOrderByCreatedAtDescIdDesc(user.getId(), page(page)).map(c -> view(c, user));
    }
    @Transactional(readOnly = true)
    public CaseView load(long id, String email) {
        var user = user(email);
        var record = find(id);
        if (record.getKind() == LeisureKind.WAITING) requireOwner(record.getOwnerId(), user);
        return view(record, user);
    }
    @Transactional
    public void advance(long id, int expectedStage, String email) {
        var user = user(email); var c = locked(id); requireOwner(c.getOwnerId(), user);
        if (c.getKind() != LeisureKind.JURISDICTION) throw error(HttpStatus.BAD_REQUEST, "Dieser Vorgang besitzt keinen Dienstweg.");
        // A double-click or stale browser tab cannot skip a station.
        if (c.getStage() != expectedStage || c.getCompletedAt() != null) return;
        c.advance(clock.instant());
    }
    @Transactional
    public void endWaiting(long id, boolean leave, String email) {
        var user = user(email); var c = locked(id); requireOwner(c.getOwnerId(), user);
        if (c.getKind() != LeisureKind.WAITING) throw error(HttpStatus.BAD_REQUEST, "Diese Akte ist keine Wartemarke.");
        if (c.getCompletedAt() != null) return;
        if (!leave && elapsed(c) < 45) throw error(HttpStatus.CONFLICT, "Schalter 7 nimmt die Weiterleitung noch nicht entgegen. Du kannst jederzeit gehen.");
        c.endWaiting(clock.instant(), leave);
    }
    @Transactional(readOnly = true)
    public Statistics statistics(String email) {
        var user = user(email); long id = user.getId();
        long forms = cases.countByOwnerId(id), notices = property.countByOwnerId(id);
        long finished = cases.countByOwnerIdAndCompletedAtIsNotNull(id) + property.countByOwnerIdAndResolvedAtIsNotNull(id);
        return new Statistics(forms + notices, finished, cases.countByOwnerIdAndKind(id, LeisureKind.WAITING), notices,
                3 * forms + 2 * notices, forms + notices == 0 ? "noch ausbaufähig" : "beträchtlich", "in Prüfung");
    }
    @Transactional(readOnly = true)
    public Page<PropertyView> propertyList(String email, int page) {
        var user = user(email); return property.findAllByOrderByCreatedAtDescIdDesc(page(page)).map(p -> view(p, user));
    }
    @Transactional(readOnly = true)
    public PropertyView property(long id, String email) { return view(findProperty(id), user(email)); }
    @Transactional(readOnly = true)
    public Page<PropertyView> replies(long id, String email, int page) {
        var user = user(email); findProperty(id);
        return property.findByParentIdOrderByCreatedAtDescIdDesc(id, page(page)).map(p -> view(p, user));
    }
    @Transactional
    public long postProperty(LostPropertyForm form, String email) {
        var user = user(email);
        if (!validator.validate(form).isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Bitte die Fundbüroangaben prüfen.");
        if (form.getParentId() != null) {
            var parent = property.lock(form.getParentId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Die Verlustanzeige wurde nicht gefunden."));
            if (form.getKind() != LostProperty.Kind.FOUND || parent.getKind() != LostProperty.Kind.LOST || parent.getResolvedAt() != null)
                throw error(HttpStatus.CONFLICT, "Ein passender Fund kann nur zu einer offenen Verlustanzeige gemeldet werden.");
        }
        return property.save(new LostProperty(user.getId(), user.getDisplayName(), form.getKind(), form.getSubject(),
                form.getDescription(), form.getParentId(), clock.instant())).getId();
    }
    @Transactional
    public void resolveProperty(long id, String email) {
        var user = user(email); var p = property.lock(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Anzeige nicht gefunden."));
        requireOwner(p.getOwnerId(), user); p.resolve(clock.instant());
    }
    private CaseView view(LeisureCase c, AppUser user) {
        String outcome = switch (c.getKind()) {
            case PERMISSION -> "Sie waren zur Antragstellung berechtigt. Vielen Dank für die nachträgliche Klärung. Diese Erlaubnis gilt ausschließlich für diesen bereits gestellten Antrag.";
            case NICKNAME -> "Die Führung der Zusatzbezeichnung »" + c.getSubject() + "« wird wohlwollend zur Kenntnis genommen. Ihr tatsächlicher Kontoname bleibt unverändert.";
            case ANTICIPATION -> "Die Vorfreude auf »" + c.getSubject() + "« wird rückwirkend auf »" + c.getEnthusiasm() + "« festgesetzt. Eine tatsächliche Teilnahme wird damit nicht bescheinigt.";
            case CERTIFICATE -> "Hiermit wird bescheinigt, dass kein Bescheinigungsbedarf vorliegt. Diese Bescheinigung ist auf Verlangen unaufgefordert vorzulegen.";
            case JURISDICTION -> c.getCompletedAt() == null ? "Die Zuständigkeit wird durch Weitergabe sorgfältig vermieden." : ROUTE.getLast();
            case WAITING -> c.getCompletedAt() == null ? "Vor Ihnen: " + Math.max(0, 3 - elapsed(c) / 15) + " Personen und eine ungeklärte Zuständigkeit."
                    : c.isLeftWaitingRoom() ? "Wartezimmer auf eigenen Wunsch verlassen. Es entstehen keinerlei Nachteile."
                    : "Schalter 7 hat festgestellt: Zuständig wäre Schalter 3 gewesen. Ihr Anliegen gilt als ausreichend weitergeleitet.";
        };
        return new CaseView(c.getId(), CaseReference.of(c.getKind().getCode(), c.getCreatedAt().atOffset(ZoneOffset.UTC), c.getId()),
                c.getKind(), c.getApplicant(), c.getSubject(), c.getExplanation(), c.getActivityId(), stamp(c.getCreatedAt()),
                c.getCompletedAt() == null ? null : stamp(c.getCompletedAt()), outcome, c.getOwnerId() == user.getId(),
                c.getStage(), c.getKind() == LeisureKind.JURISDICTION ? ROUTE.subList(0, c.getStage() + 1) : List.of(),
                (int) Math.max(0, 3 - elapsed(c) / 15), elapsed(c) >= 45, c.isLeftWaitingRoom());
    }
    private PropertyView view(LostProperty p, AppUser user) {
        return new PropertyView(p.getId(), CaseReference.of("FB", p.getCreatedAt().atOffset(ZoneOffset.UTC), p.getId()),
                p.getApplicant(), p.getKind() == LostProperty.Kind.LOST, p.getSubject(), p.getDescription(), p.getParentId(),
                stamp(p.getCreatedAt()), p.getResolvedAt() == null ? null : stamp(p.getResolvedAt()), p.getOwnerId() == user.getId());
    }
    private long elapsed(LeisureCase c) { return Math.max(0, Duration.between(c.getCreatedAt(), clock.instant()).getSeconds()); }
    private boolean eligible(Activity event, AppUser user) {
        return !event.isCancelled() && !event.getEndsAt().toInstant().isAfter(clock.instant())
                && !event.getEndsAt().toInstant().isBefore(user.getCreatedAt().toInstant());
    }
    private PageRequest page(int page) {
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Ungültige Aktenseite.");
        return PageRequest.of(page, 20);
    }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private AppUser lockedUser(String email) { return users.findLockedById(user(email).getId()).orElseThrow(); }
    private void requireOwner(long id, AppUser user) { if (id != user.getId()) throw new AccessDeniedException("Nur eigene Spaßvorgänge können bearbeitet werden."); }
    private LeisureCase find(long id) { return cases.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Spaßakte nicht gefunden.")); }
    private LeisureCase locked(long id) { return cases.lock(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Spaßakte nicht gefunden.")); }
    private LostProperty findProperty(long id) { return property.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fundbüroanzeige nicht gefunden.")); }
    private String stamp(Instant time) { return DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm", Locale.GERMAN).withZone(CalendarTime.BERLIN).format(time); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
