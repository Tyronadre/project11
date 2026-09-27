package de.tyro.project11.calendar;

import de.tyro.project11.attendance.AttendanceRepository;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class EventDetailsService {
    private final ActivityRepository activities;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final Clock clock;

    public EventDetailsService(ActivityRepository activities, AttendanceRepository attendance, UserRepository users, Clock clock) {
        this.activities = activities; this.attendance = attendance; this.users = users; this.clock = clock;
    }

    public record Member(long id, String name) {}
    public record Details(long id, String title, String description, String location, String organizer, String period,
                          String month, String status, boolean ended, boolean recorded, boolean canEditAttendance,
                          List<Member> attendees, List<Member> absent, String recordedAt, String recordedBy, boolean canManage, boolean cancelled, String cancellationReason, long revision) {}

    @Transactional(readOnly = true)
    public Details load(long id, String email) {
        var viewer = users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
        var activity = activities.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event nicht gefunden."));
        var entry = CalendarService.activityEntry(activity);
        var now = clock.instant();
        boolean ended = !activity.getEndsAt().toInstant().isAfter(now);
        String status = activity.isCancelled() ? "Abgesagt" : ended ? "Vergangenes Event" : activity.hasStartTime() && !activity.getStartsAt().toInstant().isAfter(now)
                ? "Findet gerade statt" : "Anstehendes Event";
        var sheet = ended ? attendance.findById(id).orElse(null) : null;
        List<Member> attended = List.of(), absent = List.of();
        String recordedAt = null, recordedBy = null;
        if (sheet != null) {
            var attendeeIds = sheet.getAttendees().stream().map(AppUser::getId).collect(Collectors.toSet());
            var roster = sheet.getRoster().stream()
                    .sorted(Comparator.comparing(AppUser::getDisplayName).thenComparing(AppUser::getId)).toList();
            attended = roster.stream().filter(user -> attendeeIds.contains(user.getId())).map(this::member).toList();
            absent = roster.stream().filter(user -> !attendeeIds.contains(user.getId())).map(this::member).toList();
            recordedAt = sheet.getSavedAt().atZoneSameInstant(CalendarTime.BERLIN)
                    .format(DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z", Locale.GERMAN));
            recordedBy = sheet.getReviewedBy().getDisplayName();
        }
        return new Details(id, entry.title(), entry.description(), entry.location(), entry.owner(), entry.period(),
                YearMonth.from(entry.firstDay()).toString(), status, ended && !activity.isCancelled(), sheet != null, !activity.isCancelled() && (viewer.isAdmin() || activity.getCreatedBy().getId().equals(viewer.getId())),
                attended, absent, recordedAt, recordedBy, viewer.isAdmin() || activity.getCreatedBy().getId().equals(viewer.getId()),
                activity.isCancelled(), activity.getCancellationReason(), activity.getRevision());
    }

    private Member member(AppUser user) { return new Member(user.getId(), user.getDisplayName()); }
}
