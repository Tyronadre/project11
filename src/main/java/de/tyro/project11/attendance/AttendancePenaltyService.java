package de.tyro.project11.attendance;

import de.tyro.project11.calendar.ActivityRepository;
import de.tyro.project11.portal.AbsenceApplication;
import de.tyro.project11.portal.AbsenceApplicationRepository;
import de.tyro.project11.registration.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendancePenaltyService {
    private final ActivityRepository activities;
    private final AttendanceRepository sheets;
    private final AttendancePenaltyRepository penalties;
    private final AbsenceApplicationRepository applications;
    private final UserRepository users;
    private final AttendanceRules rules;
    private final Clock clock;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final de.tyro.project11.tallies.TallyHistoryService history;
    public AttendancePenaltyService(ActivityRepository activities, AttendanceRepository sheets, AttendancePenaltyRepository penalties,
                                    AbsenceApplicationRepository applications, UserRepository users, AttendanceRules rules, Clock clock, de.tyro.project11.tallies.TallyHistoryService history) {
        this.history = history;
        this.activities = activities; this.sheets = sheets; this.penalties = penalties;
        this.applications = applications; this.users = users; this.rules = rules; this.clock = clock;
    }
    @Transactional
    public void reconcile(long activityId) {
        var activity = activities.findLockedById(activityId).orElse(null);
        if (activity != null && activity.isCancelled()) {
            var applied = penalties.findByActivityId(activityId).stream().filter(AttendancePenalty::isApplied)
                    .sorted(Comparator.comparingLong(AttendancePenalty::getUserId)).toList();
            for (var penalty : applied) {
                var user = users.findLockedById(penalty.getUserId()).orElseThrow();
                entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                history.change(user, Math.max(0, user.getTallyCount() - 1), "Automatik", "Event abgesagt: " + activity.getTitle(), "EVENT", activityId);
                penalty.setApplied(false, clock.instant());
            }
            return;
        }
        if (activity == null || activity.getEndsAt().toInstant().isAfter(clock.instant())
                || clock.instant().isBefore(AttendanceRules.deadline(activity).toInstant())) return;
        var sheet = sheets.findById(activityId).orElse(null);
        if (sheet == null || !sheet.isConfirmed()) return;
        // Travel filing/decisions/settlement also hold the affected user lock. Acquire all
        // users first, then read exemptions and penalty rows so concurrent travel updates
        // cannot leave stale event assessments or overwrite a corrected tally.
        var roster = sheet.getRoster().stream().sorted(Comparator.comparing(u -> u.getId())).toList();
        for (var member : roster) {
            var user = users.findLockedById(member.getId()).orElseThrow();
            entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        var exempt = new HashSet<>(rules.holidayMembers(activity));
        sheet.getAttendees().forEach(user -> exempt.add(user.getId()));
        applications.findByActivityId(activityId).stream().filter(AttendanceRules::timely)
                .filter(a -> a.getDecision() != AbsenceApplication.Decision.REJECTED)
                .forEach(a -> exempt.add(a.getApplicant().getId()));
        var existing = penalties.findByActivityId(activityId).stream()
                .collect(Collectors.toMap(AttendancePenalty::getUserId, p -> p));
        // Global ascending user order prevents deadlocks when different events affect the same members.
        for (var member : roster) {
            boolean shouldApply = !exempt.contains(member.getId());
            var penalty = existing.get(member.getId());
            if (penalty == null && !shouldApply) continue;
            if (penalty != null && penalty.isApplied() == shouldApply) continue;
            var user = member;
            history.change(user, shouldApply ? Math.addExact(user.getTallyCount(), 1) : Math.max(0, user.getTallyCount() - 1),
                    "Automatik", (shouldApply ? "Unentschuldigte Abwesenheit: " : "Event-Strich zurückgenommen: ") + activity.getTitle(), "EVENT", activityId);
            if (penalty == null) penalty = new AttendancePenalty(activityId, member.getId());
            penalty.setApplied(shouldApply, clock.instant());
            penalties.save(penalty);
        }
    }
}
