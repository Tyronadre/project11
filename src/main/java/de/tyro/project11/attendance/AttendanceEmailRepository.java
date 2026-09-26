package de.tyro.project11.attendance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface AttendanceEmailRepository extends JpaRepository<AttendanceEmail, Long> {
    List<AttendanceEmail> findByActivityId(long activityId);
    List<AttendanceEmail> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(AttendanceEmail.Status status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select email from AttendanceEmail email where email.id = :id")
    Optional<AttendanceEmail> findLockedById(long id);
}
