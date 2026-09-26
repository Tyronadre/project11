package de.tyro.project11.costs;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Embeddable
public class CostShare {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false) private AppUser participant;
    @Column(nullable = false) private long amountCents;
    @Column(name = "paid_at") private OffsetDateTime paidAt;
    protected CostShare() {}
    public CostShare(AppUser participant, long amountCents) { this.participant = participant; this.amountCents = amountCents; }
    void markPaid(OffsetDateTime now) { paidAt = now; }
    void markUnpaid() { paidAt = null; }
    public AppUser getParticipant() { return participant; }
    public long getAmountCents() { return amountCents; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public boolean isPaid() { return paidAt != null; }
}
