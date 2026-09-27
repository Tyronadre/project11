package de.tyro.project11.polls;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "date_polls")
public class DatePoll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 200) private String location;
    @Column(nullable = false, length = 2000) private String description;
    private int durationMinutes;
    @ManyToOne(optional = false) private AppUser owner;
    @ElementCollection @OrderColumn(name = "slot_index")
    private List<LocalDateTime> slots = new ArrayList<>();
    // One ballot per member. Empty string explicitly means "none of these dates".
    @ElementCollection @MapKeyColumn(name = "member_id") @Column(name = "choices", length = 40)
    private Map<Long, String> ballots = new HashMap<>();
    private java.time.Instant closedAt;
    @Column(length = 500) private String closeReason;
    private Long closedBy;
    public boolean isClosedWithoutEvent() { return closedAt != null; }
    public String getCloseReason() { return closeReason == null ? "" : closeReason; }
    public void close(java.time.Instant at, long userId, String reason) { closedAt = at; closedBy = userId; closeReason = reason; }
    private Long eventId;
    private Integer chosenSlot;
    protected DatePoll() {}
    public DatePoll(PollForm form, List<LocalDateTime> slots, AppUser owner) {
        title = form.getTitle(); location = form.getLocation(); description = form.getDescription();
        durationMinutes = form.getDurationMinutes(); this.slots.addAll(slots); this.owner = owner;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public int getDurationMinutes() { return durationMinutes; }
    public AppUser getOwner() { return owner; }
    public List<LocalDateTime> getSlots() { return slots; }
    public Map<Long, String> getBallots() { return ballots; }
    public Long getEventId() { return eventId; }
    public Integer getChosenSlot() { return chosenSlot; }
    public void finish(long eventId, int slot) { this.eventId = eventId; this.chosenSlot = slot; }
}
