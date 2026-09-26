package de.tyro.project11.portal;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface AbsenceDecisionEmailRepository extends JpaRepository<AbsenceDecisionEmail, Long> {
    List<AbsenceDecisionEmail> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(AbsenceDecisionEmail.Status status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select email from AbsenceDecisionEmail email where email.id = :id")
    Optional<AbsenceDecisionEmail> findLockedById(long id);
}
