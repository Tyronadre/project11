package de.tyro.project11.portal;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface TravelDecisionEmailRepository extends JpaRepository<TravelDecisionEmail, Long> {
    List<TravelDecisionEmail> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(TravelDecisionEmail.Status status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select email from TravelDecisionEmail email where email.id = :id")
    Optional<TravelDecisionEmail> findLockedById(long id);
}
