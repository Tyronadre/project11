package de.tyro.project11.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<AttendanceSheet, Long> {
    @EntityGraph(attributePaths = "activity")
    List<AttendanceSheet> findAllByOrderBySavedAtDesc();
}
