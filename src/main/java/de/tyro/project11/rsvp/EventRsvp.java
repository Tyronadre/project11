package de.tyro.project11.rsvp;

import de.tyro.project11.calendar.Activity;
import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_rsvps", uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "user_id"}))
public class EventRsvp {
    public enum Answer {
        YES("Dabei"), MAYBE("Vielleicht"), NO("Nicht dabei");
        private final String label;
        Answer(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id", nullable = false) private Activity activity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Answer answer;
    @Column(nullable = false) private OffsetDateTime startsAt;
    @Column(nullable = false) private OffsetDateTime endsAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    protected EventRsvp() {}
    public EventRsvp(Activity activity, AppUser user, Answer answer, OffsetDateTime now) {
        this.activity = activity; this.user = user; respond(answer, now);
    }
    public void respond(Answer answer, OffsetDateTime now) {
        this.answer = answer; startsAt = activity.getStartsAt(); endsAt = activity.getEndsAt(); updatedAt = now;
    }
    public boolean current(Activity event) {
        return startsAt.toInstant().equals(event.getStartsAt().toInstant()) && endsAt.toInstant().equals(event.getEndsAt().toInstant());
    }
    public AppUser getUser() { return user; }
    public Answer getAnswer() { return answer; }
    public long getVersion() { return version; }
}
