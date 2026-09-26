package de.tyro.project11.portal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "travel_day_penalties", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "vacation_date"}))
public class TravelDayPenalty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private long userId;
    @Column(name = "vacation_date", nullable = false) private LocalDate vacationDate;
    @Column(nullable = false) private long holidayId;
    @Column(nullable = false) private Instant appliedAt;
    protected TravelDayPenalty() {}
    public TravelDayPenalty(long userId, LocalDate date, long holidayId, Instant now) {
        this.userId = userId; vacationDate = date; this.holidayId = holidayId; appliedAt = now;
    }
    public LocalDate getVacationDate() { return vacationDate; }
}
