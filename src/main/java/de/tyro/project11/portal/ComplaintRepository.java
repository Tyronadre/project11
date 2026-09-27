package de.tyro.project11.portal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Page<Complaint> findByOwnerIdOrderBySubmittedAtDescIdDesc(long ownerId, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> lock(@Param("id") long id);
}
