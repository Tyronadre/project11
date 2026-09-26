package de.tyro.project11.calendar;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_change_emails", uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "revision", "user_id"}),
        indexes = @Index(columnList = "status,nextAttemptAt"))
public class EventChangeEmail {
    public enum Status { PENDING, SENT, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "activity_id", nullable = false) private long activityId;
    @Column(nullable = false) private long revision;
    @Column(name = "user_id", nullable = false) private long userId;
    @Column(nullable = false, length = 80) private String subject;
    @Column(nullable = false, length = 12000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant nextAttemptAt;
    @Column(nullable = false) private int attempts;
    protected EventChangeEmail() {}
    public EventChangeEmail(long activityId, long revision, long userId, String subject, String body, Instant now) {
        this.activityId = activityId; this.revision = revision; this.userId = userId;
        this.subject = subject; this.body = body; createdAt = now; nextAttemptAt = now; status = Status.PENDING;
    }
    public Long getId() { return id; }
    public long getActivityId() { return activityId; }
    public long getRevision() { return revision; }
    public long getUserId() { return userId; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public boolean due(Instant now) { return status == Status.PENDING && !nextAttemptAt.isAfter(now); }
    public void sent() { status = Status.SENT; attempts++; }
    public void failed(Instant now) {
        attempts++; status = attempts >= 6 ? Status.FAILED : Status.PENDING;
        nextAttemptAt = now.plusSeconds(60L << (attempts - 1));
    }
}
