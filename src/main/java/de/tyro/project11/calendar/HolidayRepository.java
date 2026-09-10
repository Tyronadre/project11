package de.tyro.project11.calendar;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    @EntityGraph(attributePaths = "user")
    List<Holiday> findByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnAscIdAsc(
            LocalDate endInclusive, LocalDate startInclusive);
}
