package de.tyro.project11.attendance;
import de.tyro.project11.calendar.*;
import de.tyro.project11.registration.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.util.*;
@Service
public class AfeaService {
    public static final List<String> EVIDENCE = List.of("Ich erinnere mich an die Kartoffeln.", "Mindestens eine Person hat mich gesehen.", "Ich war nach bestem Wissen körperlich anwesend.");
    private final AfeaRepository declarations;
    private final ActivityRepository activities;
    private final UserRepository users;
    private final AttendanceRepository attendance;
    private final Clock clock;
    public AfeaService(AfeaRepository declarations, ActivityRepository activities, UserRepository users, AttendanceRepository attendance, Clock clock) {
        this.declarations = declarations; this.activities = activities; this.users = users; this.attendance = attendance; this.clock = clock;
    }
    public record View(boolean available, boolean submitted, boolean alreadyRecorded, String reference, String evidence, List<String> choices) {}
    @Transactional(readOnly = true)
    public View load(long id, String email) {
        var user = user(email); var event = activities.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Event nicht gefunden."));
        var own = declarations.findByActivityIdAndUserId(id, user.getId());
        return new View(eligible(event, user), own.isPresent(), attendance.existsById(id),
                own.map(AfeaDeclaration::reference).orElse(""), own.map(AfeaDeclaration::getEvidence).orElse(""), EVIDENCE);
    }
    @Transactional
    public void submit(long id, int evidence, String email) {
        var user = user(email);
        var event = activities.findLockedById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Event nicht gefunden."));
        if (!eligible(event, user)) throw error(HttpStatus.CONFLICT, "Ein AFeA ist nur für beendete, nicht abgesagte Events aus deiner Mitgliedszeit möglich.");
        if (evidence < 0 || evidence >= EVIDENCE.size()) throw error(HttpStatus.BAD_REQUEST, "Bitte einen Anwesenheitsnachweis auswählen.");
        if (declarations.findByActivityIdAndUserId(id, user.getId()).isEmpty())
            declarations.save(new AfeaDeclaration(id, user.getId(), clock.instant(), EVIDENCE.get(evidence)));
    }
    @Transactional
    public void withdraw(long id, String email) {
        var user = user(email);
        activities.findLockedById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Event nicht gefunden."));
        declarations.findByActivityIdAndUserId(id, user.getId()).ifPresent(declarations::delete);
    }
    private boolean eligible(Activity event, AppUser user) {
        return !event.isCancelled() && !event.getEndsAt().toInstant().isAfter(clock.instant())
                && !user.getCreatedAt().toInstant().isAfter(event.getEndsAt().toInstant());
    }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
