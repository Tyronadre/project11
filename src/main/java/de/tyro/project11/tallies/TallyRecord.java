package de.tyro.project11.tallies;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tally_history", indexes = @Index(columnList = "userId,createdAt"))
public class TallyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private long userId;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false, updatable = false) private int beforeCount;
    @Column(nullable = false, updatable = false) private int afterCount;
    @Column(nullable = false, updatable = false, length = 80) private String actor;
    @Column(nullable = false, updatable = false, length = 500) private String reason;
    @Column(nullable = false, updatable = false, length = 20) private String source;
    @Column(updatable = false) private Long sourceId;
    protected TallyRecord() {}
    public TallyRecord(long userId, Instant at, int before, int after, String actor, String reason, String source, Long sourceId) {
        this.userId = userId; createdAt = at; beforeCount = before; afterCount = after;
        this.actor = actor; this.reason = reason; this.source = source; this.sourceId = sourceId;
    }
    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public int getBeforeCount() { return beforeCount; }
    public int getAfterCount() { return afterCount; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public String getSource() { return source; }
    public Long getSourceId() { return sourceId; }
}
