package de.tyro.project11.rsvp;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface EventRsvpRepository extends JpaRepository<EventRsvp, Long> {
    Optional<EventRsvp> findByActivityIdAndUserId(long activityId, long userId);
    @EntityGraph(attributePaths = "user")
    List<EventRsvp> findByActivityId(long activityId);
}
