package de.tyro.project11.portal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "ceremonial_complaints")
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private long ownerId;
    @Column(nullable = false, updatable = false, length = 120) private String subject;
    @Column(nullable = false, updatable = false, length = 2000) private String text;
    @Column(nullable = false, updatable = false) private Instant submittedAt;
    private Long parentId;
    private Instant closedAt;
    private Integer rating;
    protected Complaint() {}
    public Complaint(long ownerId, String subject, String text, Long parentId, Instant at) {
        this.ownerId = ownerId; this.subject = subject; this.text = text; this.parentId = parentId; submittedAt = at;
    }
    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public String getSubject() { return subject; }
    public String getText() { return text; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Long getParentId() { return parentId; }
    public Instant getClosedAt() { return closedAt; }
    public Integer getRating() { return rating; }
    public void close(Instant at) { if (closedAt == null) closedAt = at; }
    public void rate(int value) { rating = value; }
}
