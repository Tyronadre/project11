package de.tyro.project11.portal;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "absence_decision_emails", indexes = @Index(columnList = "status,nextAttemptAt"))
public class AbsenceDecisionEmail {
    public enum Status { PENDING, SENT, FAILED, CANCELLED }
    // One final decision, and one durable notification, per application.
    @Id private Long id;
    @Column(nullable = false) private long activityId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant sentAt;
    protected AbsenceDecisionEmail() {}
    public AbsenceDecisionEmail(long applicationId, long activityId, Instant now) {
        id = applicationId; this.activityId = activityId; status = Status.PENDING; nextAttemptAt = now;
    }
    public Long getId() { return id; }
    public long getActivityId() { return activityId; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public boolean due(Instant now) { return status == Status.PENDING && !nextAttemptAt.isAfter(now); }
    public void sent(Instant now) { status = Status.SENT; sentAt = now; attempts++; }
    public void cancel() { status = Status.CANCELLED; }
    public void failed(Instant now) {
        attempts++; status = attempts >= 6 ? Status.FAILED : Status.PENDING;
        nextAttemptAt = now.plusSeconds(60L << (attempts - 1));
    }
}
