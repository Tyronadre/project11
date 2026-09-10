package de.tyro.project11.calendar;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "holidays",
        indexes = @Index(name = "idx_holidays_period", columnList = "starts_on, ends_on"),
        check = @CheckConstraint(name = "ck_holiday_period", constraint = "ends_on >= starts_on"))
public class Holiday {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    protected Holiday() {}

    public Holiday(AppUser user, String title, String description, LocalDate startsOn,
                   LocalDate endsOn, OffsetDateTime submittedAt) {
        this.user = user;
        this.title = title;
        this.description = description;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
}
