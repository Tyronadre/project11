package de.tyro.project11.calendar;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    interface Coverage {
        long getUserId(); LocalDate getStartsOn(); LocalDate getEndsOn();
        OffsetDateTime getSubmittedAt(); OffsetDateTime getInvalidatedAt();
        de.tyro.project11.portal.TravelApplication.Decision getDecision();
    }
    // Scalar values avoid stale managed holiday/application entities after waiting for a user lock.
    @org.springframework.data.jpa.repository.Query("""
            select h.user.id as userId, h.startsOn as startsOn, h.endsOn as endsOn,
                   h.submittedAt as submittedAt, h.invalidatedAt as invalidatedAt, a.decision as decision
            from Holiday h left join TravelApplication a on a.holiday.id = h.id and a.kind = :kind
            where h.startsOn <= :last and h.endsOn >= :first order by h.startsOn, h.id
            """)
    List<Coverage> findCoverage(LocalDate first, LocalDate last, de.tyro.project11.portal.TravelKind kind);
    List<Holiday> findByUserIdAndEndsOnBeforeOrderByEndsOnDesc(long userId, LocalDate date);
    List<Holiday> findByUserIdOrderByEndsOnAscIdAsc(long userId);
    @EntityGraph(attributePaths = "user")
    List<Holiday> findByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnAscIdAsc(
            LocalDate endInclusive, LocalDate startInclusive);
}
