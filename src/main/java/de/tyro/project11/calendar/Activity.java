package de.tyro.project11.calendar;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "activities",
        indexes = @Index(name = "idx_activities_period", columnList = "starts_at, ends_at"),
        check = @CheckConstraint(name = "ck_activity_period", constraint = "ends_at > starts_at"))
public class Activity {
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

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

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public AppUser getCreatedBy() { return createdBy; }
}
