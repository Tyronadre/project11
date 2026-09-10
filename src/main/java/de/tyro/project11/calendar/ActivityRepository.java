package de.tyro.project11.calendar;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    @EntityGraph(attributePaths = "createdBy")
    List<Activity> findByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAscIdAsc(
            OffsetDateTime endExclusive, OffsetDateTime startInclusive);
}
