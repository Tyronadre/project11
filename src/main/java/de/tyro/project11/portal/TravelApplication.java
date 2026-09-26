package de.tyro.project11.portal;

import de.tyro.project11.calendar.Holiday;
import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "travel_applications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_travel_kind_holiday", columnNames = {"kind", "holiday_id"}),
        @UniqueConstraint(name = "uk_travel_filing_key", columnNames = "filing_key")},
        indexes = @Index(name = "idx_travel_submitted", columnList = "submitted_at"))
public class TravelApplication {
    public enum Decision { PENDING, ACCEPTED, REJECTED }
    @Enumerated(EnumType.STRING) @Column(length = 16) private Decision decision;
    @ManyToOne(fetch = FetchType.LAZY) private AppUser decidedBy;
    private OffsetDateTime decidedAt;
    @Column(length = 2000) private String decisionReason;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 10) private TravelKind kind;
    @Column(name = "filing_key", nullable = false, updatable = false, length = 36) private String filingKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private AppUser applicant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "holiday_id", nullable = false, updatable = false)
    private Holiday holiday;
    @Column(nullable = false, updatable = false, length = 250) private String subject;
    @Column(name = "submitted_at", nullable = false, updatable = false) private OffsetDateTime submittedAt;
    @ElementCollection @CollectionTable(name = "travel_application_answers", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "answer_position") private List<ApplicationAnswer> answers = new ArrayList<>();

    protected TravelApplication() {}
    public TravelApplication(TravelKind kind, String filingKey, AppUser applicant, Holiday holiday, String subject,
                             OffsetDateTime submittedAt, List<ApplicationAnswer> answers) {
        this.kind = kind; this.filingKey = filingKey; this.applicant = applicant; this.holiday = holiday;
        this.subject = subject; this.submittedAt = submittedAt; this.answers = new ArrayList<>(answers);
    }
    public Long getId() { return id; }
    public TravelKind getKind() { return kind; }
    public AppUser getApplicant() { return applicant; }
    public Holiday getHoliday() { return holiday; }
    public String getSubject() { return subject; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public List<ApplicationAnswer> getAnswers() { return List.copyOf(answers); }
    public Decision getDecision() { return decision == null ? Decision.PENDING : decision; }
    public String getDecisionReason() { return decisionReason == null ? "" : decisionReason; }
    public AppUser getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void decide(Decision decision, AppUser admin, OffsetDateTime now, String reason) {
        if (decision == Decision.PENDING || getDecision() != Decision.PENDING) throw new IllegalStateException("Decision already recorded");
        this.decision = decision; this.decidedBy = admin; this.decidedAt = now; this.decisionReason = reason;
    }
}
