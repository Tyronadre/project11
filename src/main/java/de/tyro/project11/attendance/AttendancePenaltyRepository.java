package de.tyro.project11.attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AttendancePenaltyRepository extends JpaRepository<AttendancePenalty, Long> {
    List<AttendancePenalty> findByUserId(long userId);
    List<AttendancePenalty> findByActivityId(long activityId);
    List<AttendancePenalty> findByUserIdAndAppliedTrue(long userId);
}
