package de.tyro.project11.attendance;
import de.tyro.project11.calendar.ActivityRepository;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class AttendancePenaltyScheduler {
    private final ActivityRepository activities;
    private final AttendancePenaltyService penalties;
    private final Clock clock;
    public AttendancePenaltyScheduler(ActivityRepository activities, AttendancePenaltyService penalties, Clock clock) {
        this.activities = activities; this.penalties = penalties; this.clock = clock;
    }
    @Scheduled(initialDelayString = "${app.attendance-penalties.initial-delay:15000}", fixedDelay = 60000)
    public void processDue() {
        for (var activity : activities.findByEndsAtLessThanEqualOrderByEndsAtDescIdDesc(OffsetDateTime.now(clock))) {
            try { penalties.reconcile(activity.getId()); }
            catch (RuntimeException exception) {
                LoggerFactory.getLogger(getClass()).warn("Attendance settlement failed for event {} ({})",
                        activity.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
