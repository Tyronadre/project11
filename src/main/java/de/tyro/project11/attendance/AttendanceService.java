package de.tyro.project11.attendance;

import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.AbsenceApplication;
import de.tyro.project11.portal.AbsenceApplicationRepository;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z", Locale.GERMAN);
    private final ActivityRepository activities;
    private final AttendanceRepository sheets;
    private final UserRepository users;
    private final AbsenceApplicationRepository absences;
    private final AfeaRepository declarations;
    private final Clock clock;
    private final AttendanceMailQueue mailQueue;
    private final AttendanceEmailRepository emails;
    private final AttendanceRules rules;
    private final AttendancePenaltyService penalties;
    private final AttendancePenaltyRepository penaltyRows;

    public AttendanceService(ActivityRepository activities, AttendanceRepository sheets, UserRepository users,
                             AbsenceApplicationRepository absences, Clock clock, AttendanceMailQueue mailQueue,
                             AttendanceEmailRepository emails, AttendanceRules rules, AttendancePenaltyService penalties,
                             AttendancePenaltyRepository penaltyRows, AfeaRepository declarations) {
        this.declarations = declarations;
        this.activities = activities; this.sheets = sheets; this.users = users; this.absences = absences;
        this.clock = clock; this.mailQueue = mailQueue; this.emails = emails; this.rules = rules;
        this.penalties = penalties; this.penaltyRows = penaltyRows;
    }
    public record Member(long id, String name, String email, boolean attended, String excuse, boolean notifyByEmail, String notification, String selfReport) {}
    public record Page(long id, String title, String ended, String month, String deadline, boolean expired,
                       long version, List<Member> members, String savedAt, String reviewedBy, boolean canConfirm, boolean confirmed) {
        public List<Long> recipientIds() { return members.stream().filter(Member::notifyByEmail).map(Member::id).toList(); }
    }
    public record Reminder(long activityId, String title, String deadline, boolean expired, Instant dueAt) {}
    public record Pending(Map<Long, Integer> counts, List<Reminder> reminders) {}
    public record AdminEvent(long id, String title, String ended, boolean recorded) {}
    public record MailReminder(String email, String name, String title, String ended, String deadline, boolean expired) {}

    @Transactional(readOnly = true)
    public Page page(long id, String email) {
        var activity = activities.findById(id).orElseThrow(this::missing);
        var editor = requireEditor(activity, email);
        requireEnded(activity);
        var sheet = sheets.findById(id).orElse(null);
        var roster = sheet == null ? eligibleMembers(activity) : sheet.getRoster();
        var suggestions = declarations.findByActivityId(id).stream().collect(Collectors.toMap(AfeaDeclaration::getUserId, AfeaDeclaration::getEvidence));
        var attended = sheet == null ? suggestions.keySet() : ids(sheet.getAttendees());
        var excuses = excuses(activity);
        var sent = emails.findByActivityId(id).stream().filter(e -> e.getStatus() == AttendanceEmail.Status.SENT)
                .map(AttendanceEmail::getUserId).collect(Collectors.toSet());
        boolean expired = expired(activity);
        var members = roster.stream().sorted(Comparator.comparing(AppUser::getDisplayName).thenComparing(AppUser::getId))
                .map(member -> {
                    String excuse = excuses.getOrDefault(member.getId(), "");
                    String notification = attended.contains(member.getId()) ? "Anwesend · keine E-Mail"
                            : !excuse.isEmpty() ? excuse + " · keine Erinnerung"
                            : sent.contains(member.getId()) ? "Bereits benachrichtigt · keine erneute E-Mail"
                            : expired ? "Frist abgelaufen · keine Erinnerung" : "Erhält eine AaA-Erinnerung";
                    boolean notify = !attended.contains(member.getId()) && excuse.isEmpty() && !sent.contains(member.getId()) && !expired;
                    return new Member(member.getId(), member.getDisplayName(), member.getEmail(), attended.contains(member.getId()), excuse, notify, notification, suggestions.getOrDefault(member.getId(), ""));
                }).toList();
        return new Page(id, activity.getTitle(), stamp(activity.getEndsAt()), month(activity), AttendanceRules.deadline(activity).format(STAMP),
                expired, sheet == null ? -1 : sheet.getVersion(), members,
                sheet == null ? null : stamp(sheet.getSavedAt()), sheet == null ? null : sheet.getReviewedBy().getDisplayName(),
                editor.isAdmin(), sheet != null && sheet.isConfirmed());
    }

    @Transactional
    public void save(long id, long expectedVersion, Set<Long> attendeeIds, String email) {
        var activity = activities.findLockedById(id).orElseThrow(this::missing);
        var editor = requireEditor(activity, email);
        requireEnded(activity);
        var sheet = sheets.findById(id).orElse(null);
        if (expectedVersion != (sheet == null ? -1 : sheet.getVersion())) throw conflict();
        var roster = sheet == null ? eligibleMembers(activity) : sheet.getRoster();
        if (!ids(roster).containsAll(attendeeIds)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte wähle Anwesende aus der Mitgliederliste dieses Events.");
        if (sheet == null) sheet = new AttendanceSheet(activity, roster);
        sheet.record(roster.stream().filter(member -> attendeeIds.contains(member.getId())).collect(Collectors.toSet()), editor, OffsetDateTime.now(clock));
        sheets.saveAndFlush(sheet);
        // Every edit requires a new explicit admin confirmation. Cancel unsent reminders immediately.
        mailQueue.sync(id, Set.of());
    }

    @Transactional
    public void confirm(long id, long expectedVersion, Set<Long> expectedRecipientIds, String email) {
        var activity = activities.findLockedById(id).orElseThrow(this::missing);
        var admin = user(email);
        if (!admin.isAdmin()) throw new AccessDeniedException("Nur Admins können Anwesenheit und E-Mail-Empfänger bestätigen.");
        requireEnded(activity);
        var sheet = sheets.findById(id).orElseThrow(this::missing);
        if (sheet.getVersion() != expectedVersion) throw conflict();
        // An AaA/holiday or sent email may have changed the recipient list since the review page opened.
        var recipients = new HashSet<>(page(id, email).recipientIds());
        if (!recipients.equals(expectedRecipientIds)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Die E-Mail-Empfänger haben sich geändert. Lade die Seite neu und prüfe die Empfängerliste vor der Bestätigung.");
        if (sheet.isConfirmed()) return;
        sheet.confirm(admin, OffsetDateTime.now(clock));
        sheets.saveAndFlush(sheet);
        mailQueue.sync(id, recipients);
        penalties.reconcile(id);
    }

    @Transactional(readOnly = true)
    public Pending pending(String email) {
        long currentId = user(email).getId();
        Map<Long, Integer> counts = new HashMap<>();
        List<Reminder> reminders = new ArrayList<>();
        for (var sheet : sheets.findAllByOrderBySavedAtDesc()) {
            if (!sheet.isConfirmed()) continue;
            var activity = sheet.getActivity();
            if (activity.isCancelled()) continue;
            var attended = ids(sheet.getAttendees());
            var holiday = rules.holidayMembers(activity);
            var applications = absences.findByActivityId(activity.getId()).stream().filter(AttendanceRules::timely)
                    .collect(Collectors.toMap(a -> a.getApplicant().getId(), a -> a));
            var applied = penaltyRows.findByActivityId(activity.getId()).stream().filter(AttendancePenalty::isApplied)
                    .map(AttendancePenalty::getUserId).collect(Collectors.toSet());
            for (var member : sheet.getRoster()) {
                var application = applications.get(member.getId());
                if (attended.contains(member.getId()) || holiday.contains(member.getId()) || applied.contains(member.getId())
                        || (application != null && application.getDecision() != AbsenceApplication.Decision.REJECTED)) continue;
                counts.merge(member.getId(), 1, Integer::sum);
                if (member.getId() == currentId && application == null) reminders.add(new Reminder(activity.getId(), activity.getTitle(),
                        AttendanceRules.deadline(activity).format(STAMP), expired(activity), AttendanceRules.deadline(activity).toInstant()));
            }
        }
        return new Pending(Map.copyOf(counts), List.copyOf(reminders));
    }

    @Transactional(readOnly = true)
    public Optional<MailReminder> mailReminder(long activityId, long userId) {
        var sheet = sheets.findById(activityId).orElse(null);
        if (sheet == null || sheet.getActivity().isCancelled() || !sheet.isConfirmed() || expired(sheet.getActivity())) return Optional.empty();
        var excluded = excuses(sheet.getActivity());
        var attended = ids(sheet.getAttendees());
        return sheet.getRoster().stream().filter(member -> member.getId() == userId && !attended.contains(userId) && !excluded.containsKey(userId))
                .findFirst().map(member -> new MailReminder(member.getEmail(), member.getDisplayName(), sheet.getActivity().getTitle(),
                        stamp(sheet.getActivity().getEndsAt()), AttendanceRules.deadline(sheet.getActivity()).format(STAMP), false));
    }
    @Transactional(readOnly = true)
    public List<AdminEvent> recent(String email) {
        var viewer = user(email);
        return activities.findByEndsAtLessThanEqualOrderByEndsAtDescIdDesc(OffsetDateTime.now(clock)).stream()
                .filter(activity -> !activity.isCancelled())
                .filter(activity -> viewer.isAdmin() || activity.getCreatedBy().getId().equals(viewer.getId())).limit(6)
                .map(activity -> new AdminEvent(activity.getId(), activity.getTitle(), stamp(activity.getEndsAt()), sheets.existsById(activity.getId()))).toList();
    }
    @Transactional(readOnly = true)
    public Map<String, Long> calendarLinks(String requestedMonth, String email) {
        var viewer = user(email);
        var month = YearMonth.parse(requestedMonth);
        Map<String, Long> links = new HashMap<>();
        activities.findByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAscIdAsc(
                month.plusMonths(1).atDay(1).atStartOfDay(CalendarTime.BERLIN).toOffsetDateTime(),
                month.atDay(1).atStartOfDay(CalendarTime.BERLIN).toOffsetDateTime()).stream()
                .filter(activity -> !activity.getEndsAt().toInstant().isAfter(clock.instant()))
                .filter(activity -> !activity.isCancelled())
                .filter(activity -> viewer.isAdmin() || activity.getCreatedBy().getId().equals(viewer.getId()))
                .forEach(activity -> links.put("activity-" + activity.getId(), activity.getId()));
        return links;
    }
    private Map<Long, String> excuses(Activity activity) {
        Map<Long, String> result = new HashMap<>();
        absences.findByActivityId(activity.getId()).stream().filter(AttendanceRules::timely).forEach(application ->
                result.put(application.getApplicant().getId(), switch (application.getDecision()) {
                    case PENDING -> "AaA wartet auf Prüfung";
                    case ACCEPTED -> "AaA angenommen";
                    case REJECTED -> "AaA abgelehnt";
                }));
        rules.holidayMembers(activity).forEach(id -> result.put(id, "Durch Urlaub abgedeckt"));
        return result;
    }
    private Set<AppUser> eligibleMembers(Activity activity) {
        return users.findAllByOrderByDisplayNameAsc().stream()
                .filter(member -> !member.getCreatedAt().toInstant().isAfter(activity.getEndsAt().toInstant())).collect(Collectors.toSet());
    }
    private Set<Long> ids(Set<AppUser> members) { return members.stream().map(AppUser::getId).collect(Collectors.toSet()); }
    private boolean expired(Activity activity) { return !clock.instant().isBefore(AttendanceRules.deadline(activity).toInstant()); }
    private String month(Activity activity) { return YearMonth.from(activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN)).toString(); }
    private String stamp(OffsetDateTime time) { return time.atZoneSameInstant(CalendarTime.BERLIN).format(STAMP); }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private AppUser requireEditor(Activity activity, String email) {
        var editor = user(email);
        if (!editor.isAdmin() && !activity.getCreatedBy().getId().equals(editor.getId())) throw new AccessDeniedException("Nur Admins und der Eventersteller können die Anwesenheit erfassen.");
        return editor;
    }
    private void requireEnded(Activity activity) {
        if (activity.isCancelled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Für abgesagte Events wird keine Anwesenheit erfasst.");
        if (activity.getEndsAt().toInstant().isAfter(clock.instant())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Anwesenheit kann nach Ende des Events erfasst werden.");
    }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Die Anwesenheit wurde geändert. Lade die Seite vor dem Speichern oder Bestätigen neu."); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event oder Anwesenheitsliste nicht gefunden."); }
}
