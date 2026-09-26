package de.tyro.project11.attendance;

import de.tyro.project11.calendar.Activity;
import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "attendance_sheets")
public class AttendanceSheet {
    @Id private Long id;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id") private Activity activity;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private AppUser reviewedBy;
    @Column(nullable = false) private OffsetDateTime savedAt;
    private OffsetDateTime confirmedAt;
    @ManyToOne(fetch = FetchType.LAZY) private AppUser confirmedBy;
    @ManyToMany
    @JoinTable(name = "attendance_roster", joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<AppUser> roster = new HashSet<>();
    @ManyToMany
    @JoinTable(name = "attendance_attendees", joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<AppUser> attendees = new HashSet<>();

    protected AttendanceSheet() {}
    public AttendanceSheet(Activity activity, Set<AppUser> roster) {
        this.activity = activity; this.roster = new HashSet<>(roster);
    }
    public void record(Set<AppUser> attendees, AppUser editor, OffsetDateTime now) {
        this.attendees.clear(); this.attendees.addAll(attendees);
        this.reviewedBy = editor; this.savedAt = now;
        this.confirmedAt = null; this.confirmedBy = null;
    }
    public void confirm(AppUser admin, OffsetDateTime now) { confirmedBy = admin; confirmedAt = now; }
    public boolean isConfirmed() { return confirmedAt != null; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public Activity getActivity() { return activity; }
    public long getVersion() { return version; }
    public Set<AppUser> getRoster() { return Set.copyOf(roster); }
    public Set<AppUser> getAttendees() { return Set.copyOf(attendees); }
    public AppUser getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getSavedAt() { return savedAt; }
}
