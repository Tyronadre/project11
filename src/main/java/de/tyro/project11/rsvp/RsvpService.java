package de.tyro.project11.rsvp;

import de.tyro.project11.calendar.*;
import de.tyro.project11.registration.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RsvpService {
    private final ActivityRepository activities;
    private final UserRepository users;
    private final EventRsvpRepository responses;
    private final Clock clock;
    public RsvpService(ActivityRepository activities, UserRepository users, EventRsvpRepository responses, Clock clock) {
        this.activities = activities; this.users = users; this.responses = responses; this.clock = clock;
    }
    public record Member(long id, String name, String note) {}
    public record Group(String label, List<Member> members) {}
    public record Page(boolean open, String closedReason, String mine, boolean outdated, long version, long eventRevision,
                       List<EventRsvp.Answer> choices, List<Group> groups) {}
    @Transactional(readOnly = true)
    public Page load(long eventId, String email) {
        var viewer = user(email);
        var event = activities.findById(eventId).orElseThrow(RsvpService::missing);
        var rows = responses.findByActivityId(eventId).stream().collect(Collectors.toMap(r -> r.getUser().getId(), r -> r));
        var grouped = new LinkedHashMap<String, List<Member>>();
        for (var answer : EventRsvp.Answer.values()) grouped.put(answer.getLabel(), new ArrayList<>());
        grouped.put("Noch offen", new ArrayList<>());
        for (var member : users.findAllByOrderByDisplayNameAsc()) {
            var row = rows.get(member.getId());
            // Do not add members who joined after planning closed to the historical unanswered list.
            if (row == null && !open(event) && member.getCreatedAt().toInstant().isAfter(event.getStartsAt().toInstant())) continue;
            boolean current = row != null && row.current(event);
            String note = row != null && !current ? "Termin geändert · zuvor: " + row.getAnswer().getLabel() : "";
            grouped.get(current ? row.getAnswer().getLabel() : "Noch offen").add(new Member(member.getId(), member.getDisplayName(), note));
        }
        var mine = rows.get(viewer.getId());
        return new Page(open(event), event.isCancelled() ? "Das Event wurde abgesagt." : "Die Rückmeldung ist seit Eventbeginn geschlossen.",
                mine != null && mine.current(event) ? mine.getAnswer().name() : "", mine != null && !mine.current(event),
                mine == null ? -1 : mine.getVersion(), event.getRevision(), List.of(EventRsvp.Answer.values()),
                grouped.entrySet().stream().map(e -> new Group(e.getKey(), List.copyOf(e.getValue()))).toList());
    }
    @Transactional
    public void respond(long eventId, EventRsvp.Answer answer, long version, long eventRevision, String email) {
        var event = activities.findLockedById(eventId).orElseThrow(RsvpService::missing);
        var user = user(email);
        if (!open(event)) throw conflict(event.isCancelled() ? "Dieses Event wurde abgesagt." : "Rückmeldungen sind nur vor Eventbeginn möglich.");
        if (event.getRevision() != eventRevision) throw conflict("Das Event wurde geändert. Bitte die aktuellen Angaben prüfen und erneut antworten.");
        if (answer == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Rückmeldung wählen.");
        var row = responses.findByActivityIdAndUserId(eventId, user.getId()).orElse(null);
        if (row != null && row.current(event) && row.getAnswer() == answer) return;
        if ((row == null ? -1 : row.getVersion()) != version) throw conflict("Deine Rückmeldung wurde inzwischen geändert. Bitte neu laden.");
        if (row == null) row = new EventRsvp(event, user, answer, OffsetDateTime.now(clock));
        else row.respond(answer, OffsetDateTime.now(clock));
        responses.saveAndFlush(row);
    }
    private boolean open(Activity event) { return !event.isCancelled() && clock.instant().isBefore(event.getStartsAt().toInstant()); }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event nicht gefunden."); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
