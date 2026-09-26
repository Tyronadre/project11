package de.tyro.project11.calendar;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface EventChangeEmailRepository extends JpaRepository<EventChangeEmail, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventChangeEmail e where e.id = :id")
    Optional<EventChangeEmail> findLockedById(@Param("id") long id);
    List<EventChangeEmail> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(EventChangeEmail.Status status, Instant now);
    List<EventChangeEmail> findByActivityIdOrderByIdAsc(long activityId);
}
