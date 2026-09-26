package de.tyro.project11.costs;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface EventCostRepository extends JpaRepository<EventCost, Long> {
    @EntityGraph(attributePaths = {"activity", "creator"})
    List<EventCost> findByPaidAtIsNullOrderByActivityStartsAtDescActivityIdDescIdAsc();
    @EntityGraph(attributePaths = {"activity", "creator"})
    List<EventCost> findAllByOrderByActivityStartsAtDescActivityIdDescIdAsc();
    @EntityGraph(attributePaths = "creator")
    List<EventCost> findByActivityIdOrderByIdAsc(long activityId);
    Optional<EventCost> findByCreatorIdAndRequestKey(long creatorId, String requestKey);
}
