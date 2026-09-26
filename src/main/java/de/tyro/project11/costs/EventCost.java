package de.tyro.project11.costs;

import de.tyro.project11.calendar.Activity;
import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "event_costs", uniqueConstraints = @UniqueConstraint(columnNames = {"creator_id", "request_key"}),
        indexes = @Index(columnList = "activity_id"))
public class EventCost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id", nullable = false, updatable = false)
    private Activity activity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private AppUser creator;
    @Column(name = "request_key", length = 36, nullable = false, updatable = false) private String requestKey;
    @Column(length = 160, nullable = false) private String description;
    @Column(nullable = false) private long amountCents;
    @Column(nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    private OffsetDateTime paidAt;
    // Null preserves the all-attendees behavior of existing requests.
    private Boolean selectedOnly;
    @ElementCollection
    @CollectionTable(name = "event_cost_selected_users", joinColumns = @JoinColumn(name = "cost_id"))
    @Column(name = "user_id", nullable = false)
    private Set<Long> selectedUserIds = new HashSet<>();
    public boolean isSelectedOnly() { return Boolean.TRUE.equals(selectedOnly); }
    public Set<Long> getSelectedUserIds() { return Set.copyOf(selectedUserIds); }
    public void selectParticipants(boolean selectedOnly, Set<Long> ids) {
        if (hasPaymentTracking()) throw new IllegalStateException("Costs with tracked payments cannot be edited");
        this.selectedOnly = selectedOnly;
        selectedUserIds.clear();
        if (selectedOnly) selectedUserIds.addAll(ids);
    }
    @ElementCollection
    @CollectionTable(name = "event_cost_shares", joinColumns = @JoinColumn(name = "cost_id"))
    @OrderColumn(name = "share_position")
    private List<CostShare> settlementShares = new ArrayList<>();
    protected EventCost() {}
    public EventCost(Activity activity, AppUser creator, String requestKey, String description, long cents, OffsetDateTime now) {
        this.activity = activity; this.creator = creator; this.requestKey = requestKey; createdAt = now;
        update(description, cents, now);
    }
    public void update(String description, long cents, OffsetDateTime now) {
        if (hasPaymentTracking()) throw new IllegalStateException("Costs with tracked payments cannot be edited");
        if (cents <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.description = description; amountCents = cents; updatedAt = now;
    }
    public void markSharePaid(List<CostShare> currentShares, long participantId, OffsetDateTime now) {
        if (!hasPaymentTracking()) {
            validateShares(currentShares);
            settlementShares.addAll(currentShares);
        }
        var share = share(participantId);
        requirePayment(share);
        if (paidAt != null || share.isPaid()) return;
        share.markPaid(now);
        if (settlementShares.stream().filter(this::requiresPayment).allMatch(CostShare::isPaid)) paidAt = now;
        updatedAt = now;
    }
    public void markShareUnpaid(long participantId, OffsetDateTime now) {
        if (!hasPaymentTracking()) return;
        var share = share(participantId);
        requirePayment(share);
        if (paidAt != null) {
            // Older rows only recorded the completion time on the request. Materialize those
            // individual states before reopening one person.
            settlementShares.stream().filter(this::requiresPayment).filter(item -> !item.isPaid())
                    .forEach(item -> item.markPaid(paidAt));
            paidAt = null;
        }
        if (!share.isPaid()) return;
        share.markUnpaid();
        if (settlementShares.stream().filter(this::requiresPayment).noneMatch(CostShare::isPaid)) settlementShares.clear();
        updatedAt = now;
    }
    public void reopen(OffsetDateTime now) { paidAt = null; settlementShares.clear(); updatedAt = now; }
    private void validateShares(List<CostShare> shares) {
        if (shares.isEmpty() || shares.stream().mapToLong(CostShare::getAmountCents).sum() != amountCents)
            throw new IllegalArgumentException("Shares must cover the full amount");
    }
    private CostShare share(long participantId) {
        return settlementShares.stream().filter(item -> item.getParticipant().getId() == participantId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Participant has no share"));
    }
    private boolean requiresPayment(CostShare share) {
        return share.getAmountCents() > 0 && !share.getParticipant().getId().equals(creator.getId());
    }
    private void requirePayment(CostShare share) {
        if (!requiresPayment(share)) throw new IllegalArgumentException("Share does not require a payment");
    }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public Activity getActivity() { return activity; }
    public AppUser getCreator() { return creator; }
    public String getRequestKey() { return requestKey; }
    public String getDescription() { return description; }
    public long getAmountCents() { return amountCents; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public boolean isPaid() { return paidAt != null; }
    public boolean hasPaymentTracking() { return paidAt != null || !settlementShares.isEmpty(); }
    public boolean isSharePaid(long participantId) {
        return settlementShares.stream().filter(item -> item.getParticipant().getId() == participantId)
                .findFirst().filter(this::requiresPayment).map(item -> paidAt != null || item.isPaid()).orElse(false);
    }
    public List<CostShare> getSettlementShares() { return List.copyOf(settlementShares); }
}
