package de.tyro.project11.calendar;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.time.OffsetDateTime;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select activity from Activity activity where activity.id = :id")
    Optional<Activity> findLockedById(@Param("id") long id);

    List<Activity> findByEndsAtLessThanEqualOrderByEndsAtDescIdDesc(OffsetDateTime now);

    List<Activity> findTop6ByEndsAtLessThanEqualOrderByEndsAtDescIdDesc(OffsetDateTime now);

    @EntityGraph(attributePaths = "createdBy")
    @Query("select a from Activity a where a.endsAt > :now and a.cancelledAt is null order by a.startsAt, a.id limit 6")
    List<Activity> findTop6ByEndsAtAfterOrderByStartsAtAscIdAsc(@Param("now") OffsetDateTime now);

    List<Activity> findByEndsAtAfterOrderByStartsAtAscIdAsc(OffsetDateTime earliestEnd);

    @EntityGraph(attributePaths = "createdBy")
    List<Activity> findByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAscIdAsc(
            OffsetDateTime endExclusive, OffsetDateTime startInclusive);
}
