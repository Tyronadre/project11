package de.tyro.project11.portal.leisure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "immaterial_lost_property", indexes = {@Index(columnList = "parent_id,created_at"), @Index(columnList = "owner_id")})
public class LostProperty {
    public enum Kind { LOST, FOUND }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false, updatable = false) private long ownerId;
    @Column(nullable = false, updatable = false, length = 80) private String applicant;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 10) private Kind kind;
    @Column(nullable = false, updatable = false, length = 120) private String subject;
    @Column(nullable = false, updatable = false, length = 2000) private String description;
    @Column(name = "parent_id", updatable = false) private Long parentId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    private Instant resolvedAt;
    protected LostProperty() {}
    LostProperty(long owner, String applicant, Kind kind, String subject, String description, Long parent, Instant now) {
        ownerId = owner; this.applicant = applicant; this.kind = kind; this.subject = subject;
        this.description = description; parentId = parent; createdAt = now;
    }
    void resolve(Instant now) { if (resolvedAt == null) resolvedAt = now; }
    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public String getApplicant() { return applicant; }
    public Kind getKind() { return kind; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public Long getParentId() { return parentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
