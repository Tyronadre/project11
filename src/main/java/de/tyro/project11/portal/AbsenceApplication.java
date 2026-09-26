package de.tyro.project11.portal;

import de.tyro.project11.calendar.Activity;
import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "absence_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_absence_user_activity", columnNames = {"applicant_id", "activity_id"}),
        indexes = @Index(name = "idx_absence_submitted", columnList = "submitted_at"))
public class AbsenceApplication {
    public enum Decision { PENDING, ACCEPTED, REJECTED }
    @Enumerated(EnumType.STRING) @Column(length = 16) private Decision decision;
    @ManyToOne(fetch = FetchType.LAZY) private AppUser decidedBy;
    private OffsetDateTime decidedAt;
    @Column(length = 2000) private String decisionReason;
    public Decision getDecision() { return decision == null ? Decision.PENDING : decision; }
    public AppUser getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public String getDecisionReason() { return decisionReason == null ? "" : decisionReason; }
    public void decide(Decision decision, AppUser admin, OffsetDateTime now, String reason) {
        if (decision == Decision.PENDING || getDecision() != Decision.PENDING) throw new IllegalStateException("Decision already recorded");
        this.decision = decision; decidedBy = admin; decidedAt = now; decisionReason = reason;
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private AppUser applicant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false, updatable = false)
    private Activity activity;
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;
    @Column(name = "activity_title", nullable = false, updatable = false, length = 120)
    private String activityTitle;
    @ElementCollection
    @CollectionTable(name = "absence_application_answers", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "answer_position")
    private List<ApplicationAnswer> answers = new ArrayList<>();

    protected AbsenceApplication() {}

    public AbsenceApplication(AppUser applicant, Activity activity, OffsetDateTime submittedAt, List<ApplicationAnswer> answers) {
        this.applicant = applicant;
        this.activity = activity;
        this.activityTitle = activity.getTitle();
        this.submittedAt = submittedAt;
        this.answers = new ArrayList<>(answers);
    }

    public Long getId() { return id; }
    public AppUser getApplicant() { return applicant; }
    public Activity getActivity() { return activity; }
    public String getActivityTitle() { return activityTitle; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public List<ApplicationAnswer> getAnswers() { return List.copyOf(answers); }
}
