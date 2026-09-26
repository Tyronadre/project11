package de.tyro.project11.dashboard;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "decision_receipts", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "source", "application_id"}))
public class DecisionReceipt {
    public enum Source { ABSENCE, TRAVEL }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Source source;
    @Column(name = "application_id", nullable = false) private long applicationId;
    @Column(nullable = false) private Instant readAt;
    protected DecisionReceipt() {}
    public DecisionReceipt(long userId, Source source, long applicationId, Instant readAt) {
        this.userId = userId; this.source = source; this.applicationId = applicationId; this.readAt = readAt;
    }
    public Source getSource() { return source; }
    public long getApplicationId() { return applicationId; }
}
