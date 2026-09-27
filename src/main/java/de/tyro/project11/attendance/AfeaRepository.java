package de.tyro.project11.attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AfeaRepository extends JpaRepository<AfeaDeclaration, Long> {
    List<AfeaDeclaration> findByActivityId(long activityId);
    Optional<AfeaDeclaration> findByActivityIdAndUserId(long activityId, long userId);
}
