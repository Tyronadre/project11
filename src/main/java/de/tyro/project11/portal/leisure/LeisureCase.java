package de.tyro.project11.portal.leisure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "leisure_cases", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "submission_token"}),
        indexes = @Index(columnList = "owner_id,created_at"))
public class LeisureCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false, updatable = false) private long ownerId;
    @Column(name = "submission_token", nullable = false, updatable = false, length = 36) private String submissionToken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 30) private LeisureKind kind;
    @Column(nullable = false, updatable = false, length = 80) private String applicant;
    @Column(nullable = false, updatable = false, length = 160) private String subject;
    @Column(nullable = false, updatable = false, length = 2000) private String explanation;
    @Column(updatable = false) private Long activityId;
    @Column(nullable = false, updatable = false, length = 40) private String enthusiasm;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    private Instant completedAt;
    @Column(nullable = false) private int stage;
    @Column(nullable = false) private boolean leftWaitingRoom;
    protected LeisureCase() {}
    LeisureCase(long owner, String token, LeisureKind kind, String applicant, String subject, String explanation,
                Long activityId, String enthusiasm, Instant now) {
        ownerId = owner; submissionToken = token; this.kind = kind; this.applicant = applicant;
        this.subject = subject; this.explanation = explanation; this.activityId = activityId;
        this.enthusiasm = enthusiasm; createdAt = now;
        if (kind != LeisureKind.JURISDICTION && kind != LeisureKind.WAITING) completedAt = now;
    }
    void advance(Instant now) { if (completedAt == null && ++stage >= 3) completedAt = now; }
    void endWaiting(Instant now, boolean left) { if (completedAt == null) { completedAt = now; leftWaitingRoom = left; } }
    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public LeisureKind getKind() { return kind; }
    public String getApplicant() { return applicant; }
    public String getSubject() { return subject; }
    public String getExplanation() { return explanation; }
    public Long getActivityId() { return activityId; }
    public String getEnthusiasm() { return enthusiasm; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getStage() { return stage; }
    public boolean isLeftWaitingRoom() { return leftWaitingRoom; }
}
