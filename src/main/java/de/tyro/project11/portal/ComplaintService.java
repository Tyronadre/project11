package de.tyro.project11.portal;
import de.tyro.project11.registration.*;
import de.tyro.project11.calendar.CalendarTime;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Deliberately isolated entertainment: no attendance, tally, mail or application services. */
@Service
public class ComplaintService {
    private final ComplaintRepository complaints;
    private final UserRepository users;
    private final Clock clock;
    private final Validator validator;
    public ComplaintService(ComplaintRepository complaints, UserRepository users, Clock clock, Validator validator) {
        this.complaints = complaints; this.users = users; this.clock = clock; this.validator = validator;
    }
    public record View(long id, String reference, String subject, String text, String submitted, String closed,
                       Integer rating, Long parentId, boolean owner) {}
    @Transactional(readOnly = true)
    public Page<View> list(String email, int page) {
        var user = user(email);
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Ungültige Aktenseite.");
        return complaints.findByOwnerIdOrderBySubmittedAtDescIdDesc(user.getId(), PageRequest.of(page, 20)).map(c -> view(c, user));
    }
    @Transactional(readOnly = true)
    public View load(long id, String email) { return view(find(id), user(email)); }
    @Transactional
    public long submit(ComplaintForm form, String email) {
        var user = user(email);
        if (!validator.validate(form).isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Bitte die Beschwerdeangaben prüfen.");
        if (form.getParentId() != null) requireOwner(find(form.getParentId()), user);
        return complaints.save(new Complaint(user.getId(), form.getSubject(), form.getText(), form.getParentId(), clock.instant())).getId();
    }
    @Transactional
    public void close(long id, String email) {
        var user = user(email); var c = locked(id); requireOwner(c, user); c.close(clock.instant());
    }
    @Transactional
    public void rate(long id, int rating, String email) {
        var user = user(email); var c = locked(id); requireOwner(c, user);
        if (c.getClosedAt() == null) throw error(HttpStatus.CONFLICT, "Bitte zunächst die Beschwerde abschließen.");
        if (rating < 1 || rating > 5) throw error(HttpStatus.BAD_REQUEST, "Bitte eine bis fünf Büroklammern vergeben.");
        c.rate(rating);
    }
    private View view(Complaint c, AppUser user) {
        return new View(c.getId(), CaseReference.of("BüB", c.getSubmittedAt().atOffset(ZoneOffset.UTC), c.getId()),
                c.getSubject(), c.getText(), stamp(c.getSubmittedAt()), c.getClosedAt() == null ? null : stamp(c.getClosedAt()),
                c.getRating(), c.getParentId(), c.getOwnerId() == user.getId());
    }
    private String stamp(Instant at) { return DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm", Locale.GERMAN).withZone(CalendarTime.BERLIN).format(at); }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private void requireOwner(Complaint c, AppUser u) { if (c.getOwnerId() != u.getId()) throw new AccessDeniedException("Nur eigene Beschwerden dürfen bearbeitet werden."); }
    private Complaint find(long id) { return complaints.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Beschwerde nicht gefunden.")); }
    private Complaint locked(long id) { return complaints.lock(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Beschwerde nicht gefunden.")); }
    private ResponseStatusException error(HttpStatus s, String message) { return new ResponseStatusException(s, message); }
}
