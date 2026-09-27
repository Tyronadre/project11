package de.tyro.project11.tallies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface TallyRecordRepository extends JpaRepository<TallyRecord, Long> {
    Page<TallyRecord> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Pageable pageable);
}
