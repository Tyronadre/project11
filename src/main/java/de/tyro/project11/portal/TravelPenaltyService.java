package de.tyro.project11.portal;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.ActivityRepository;
import de.tyro.project11.registration.UserRepository;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TravelPenaltyService {
    private final TravelApplicationRepository applications;
    private final TravelDayPenaltyRepository days;
    private final UserRepository users;
    private final AttendancePenaltyRepository eventPenalties;
    private final ActivityRepository activities;
    private final AttendanceRules attendanceRules;
    private final Clock clock;
    @PersistenceContext private EntityManager entityManager;
    public TravelPenaltyService(TravelApplicationRepository applications, TravelDayPenaltyRepository days, UserRepository users,
                                AttendancePenaltyRepository eventPenalties, ActivityRepository activities, AttendanceRules attendanceRules, Clock clock) {
        this.applications = applications; this.days = days; this.users = users; this.eventPenalties = eventPenalties;
        this.activities = activities; this.attendanceRules = attendanceRules; this.clock = clock;
    }
    @Transactional
    public void reconcile(long userId) {
        var user = users.findLockedById(userId).orElse(null);
        if (user == null) return;
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        var chargedDates = days.findByUserId(userId).stream().map(TravelDayPenalty::getVacationDate).collect(Collectors.toSet());
        int added = 0;
        boolean invalidated = false;
        for (var leave : applications.findByApplicantIdAndKind(userId, TravelKind.LEAVE).stream()
                .sorted(Comparator.comparing(TravelApplication::getId)).toList()) {
            if (leave.getDecision() != TravelApplication.Decision.ACCEPTED) continue;
            var holiday = leave.getHoliday();
            if (clock.instant().isBefore(TravelRules.deadline(holiday).toInstant())) continue;
            var report = applications.findByKindAndHolidayId(TravelKind.REPORT, holiday.getId()).orElse(null);
            boolean timelyReport = report != null && TravelRules.timely(report) && report.getDecision() != TravelApplication.Decision.REJECTED;
            if (!holiday.isInvalidated() && timelyReport) continue; // Pending admin review preserves the receipt.
            holiday.invalidate(OffsetDateTime.now(clock));
            invalidated = true;
            for (var date = holiday.getStartsOn(); !date.isAfter(holiday.getEndsOn()); date = date.plusDays(1)) {
                if (chargedDates.add(date)) {
                    days.save(new TravelDayPenalty(userId, date, holiday.getId(), clock.instant()));
                    added = Math.addExact(added, 1);
                }
            }
        }
        int removed = 0;
        if (invalidated) {
            // Do not lock events here: event settlement locks event -> users. The user
            // lock serializes penalty-row changes without introducing a reverse lock order.
            for (var penalty : eventPenalties.findByUserIdAndAppliedTrue(userId)) {
                var activity = activities.findById(penalty.getActivityId()).orElse(null);
                if (activity != null && attendanceRules.holidayMembers(activity).contains(userId)) {
                    penalty.setApplied(false, clock.instant());
                    removed++;
                }
            }
        }
        user.setTallyCount(Math.max(0, Math.addExact(user.getTallyCount(), added) - removed));
    }
}
