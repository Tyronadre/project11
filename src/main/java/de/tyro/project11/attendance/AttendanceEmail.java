package de.tyro.project11.attendance;

import jakarta.persistence.*;
import java.time.Instant;

/** One durable reminder per event/member. SMTP credentials and message bodies are never stored here. */
@Entity
@Table(name = "attendance_emails",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "user_id"}),
        indexes = @Index(name = "attendance_email_due", columnList = "status,next_attempt_at"))
public class AttendanceEmail {
    public enum Status { PENDING, SENT, CANCELLED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "activity_id", nullable = false)
    private long activityId;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Status status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    private Instant sentAt;

    protected AttendanceEmail() {}
    AttendanceEmail(long activityId, long userId, Instant now) {
        this.activityId = activityId; this.userId = userId;
        status = Status.PENDING; nextAttemptAt = now;
    }
    public Long getId() { return id; }
    public long getActivityId() { return activityId; }
    public long getUserId() { return userId; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public boolean due(Instant now) { return status == Status.PENDING && !nextAttemptAt.isAfter(now); }
    void requeue(Instant now) {
        if (status == Status.CANCELLED || status == Status.FAILED) {
            status = Status.PENDING; attempts = 0; nextAttemptAt = now;
        }
    }
    void cancel() { if (status != Status.SENT) status = Status.CANCELLED; }
    void sent(Instant now) { status = Status.SENT; sentAt = now; attempts++; }
    void failed(Instant now) {
        attempts++;
        status = attempts >= 6 ? Status.FAILED : Status.PENDING;
        nextAttemptAt = now.plusSeconds(60L << (attempts - 1));
    }
}
