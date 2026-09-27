package de.tyro.project11.attendance;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "afea_declarations", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "userId"}))
public class AfeaDeclaration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long activityId;
    @Column(nullable = false) private long userId;
    @Column(nullable = false) private Instant submittedAt;
    @Column(nullable = false, length = 200) private String evidence;
    protected AfeaDeclaration() {}
    public AfeaDeclaration(long activityId, long userId, Instant at, String evidence) {
        this.activityId = activityId; this.userId = userId; submittedAt = at; this.evidence = evidence;
    }
    public long getUserId() { return userId; }
    public String getEvidence() { return evidence; }
    public String reference() { return "AFeA-" + id; }
}
