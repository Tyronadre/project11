package de.tyro.project11.polls;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PollRepository extends JpaRepository<DatePoll, Long> {
    List<DatePoll> findAllByOrderByIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DatePoll p where p.id = :id")
    Optional<DatePoll> lock(@Param("id") long id);
}
