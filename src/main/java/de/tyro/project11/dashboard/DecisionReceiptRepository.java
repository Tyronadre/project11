package de.tyro.project11.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DecisionReceiptRepository extends JpaRepository<DecisionReceipt, Long> {
    List<DecisionReceipt> findByUserId(long userId);
    boolean existsByUserIdAndSourceAndApplicationId(long userId, DecisionReceipt.Source source, long applicationId);
}
