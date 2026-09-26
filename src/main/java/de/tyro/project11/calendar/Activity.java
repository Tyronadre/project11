package de.tyro.project11.calendar;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "activities",
        indexes = @Index(name = "idx_activities_period", columnList = "starts_at, ends_at"),
        check = @CheckConstraint(name = "ck_activity_period", constraint = "ends_at > starts_at"))
public class Activity {
    public enum Timing { INTERVAL, START_TIME, ALL_DAY }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    // Null in existing databases means a legacy event with an explicit start/end interval.
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Timing timing;

    // Nullable for existing records, which already have a known start time unless all-day.
    private Boolean startTimeUnspecified;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    private Long revision;
    private OffsetDateTime cancelledAt;
    @Column(length = 1000) private String cancellationReason;
    public long getRevision() { return revision == null ? 0 : revision; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public boolean isCancelled() { return cancelledAt != null; }
    public String getCancellationReason() { return cancellationReason == null ? "" : cancellationReason; }
    public void revise(String title, String description, String location, OffsetDateTime start, OffsetDateTime end,
                       Timing timing, boolean unspecified) {
        this.title = title; this.description = description; this.location = location;
        startsAt = start; endsAt = end; this.timing = timing; startTimeUnspecified = unspecified;
        revision = getRevision() + 1;
    }
    public void cancel(String reason, OffsetDateTime now) {
        cancelledAt = now; cancellationReason = reason; revision = getRevision() + 1;
    }
    protected Activity() {}

    public Activity(String title, String location, String description, OffsetDateTime startsAt,
                    OffsetDateTime endsAt, AppUser createdBy) {
        this.title = title;
        this.location = location;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
    }

    public Activity(String title, String location, String description, OffsetDateTime startsAt,
                    OffsetDateTime endsAt, AppUser createdBy, Timing timing) {
        this(title, location, description, startsAt, endsAt, createdBy);
        this.timing = timing;
    }

    public Activity(String title, String location, String description, OffsetDateTime startsAt,
                    OffsetDateTime endsAt, AppUser createdBy, Timing timing, boolean startTimeUnspecified) {
        this(title, location, description, startsAt, endsAt, createdBy, timing);
        this.startTimeUnspecified = startTimeUnspecified;
    }

    public boolean hasStartTime() { return !isAllDay() && !Boolean.TRUE.equals(startTimeUnspecified); }
    public boolean isAllDay() { return timing == Timing.ALL_DAY; }
    public boolean hasEndTime() { return timing == null || timing == Timing.INTERVAL; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public AppUser getCreatedBy() { return createdBy; }
}
