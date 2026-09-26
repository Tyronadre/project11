package de.tyro.project11.portal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TravelDayPenaltyRepository extends JpaRepository<TravelDayPenalty, Long> {
    List<TravelDayPenalty> findByUserId(long userId);
}
