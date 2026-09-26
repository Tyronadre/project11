package de.tyro.project11.attendance;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attendance_penalties", uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "user_id"}))
public class AttendancePenalty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "activity_id", nullable = false) private long activityId;
    @Column(name = "user_id", nullable = false) private long userId;
    @Column(nullable = false) private boolean applied;
    @Column(nullable = false) private Instant updatedAt;
    protected AttendancePenalty() {}
    AttendancePenalty(long activityId, long userId) { this.activityId = activityId; this.userId = userId; }
    public long getUserId() { return userId; }
    public long getActivityId() { return activityId; }
    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied, Instant now) { this.applied = applied; updatedAt = now; }
}
