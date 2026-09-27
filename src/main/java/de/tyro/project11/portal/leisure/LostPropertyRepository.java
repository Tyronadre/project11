package de.tyro.project11.portal.leisure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LostPropertyRepository extends JpaRepository<LostProperty, Long> {
    Page<LostProperty> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
    Page<LostProperty> findByParentIdOrderByCreatedAtDescIdDesc(long parent, Pageable page);
    long countByOwnerId(long owner);
    long countByOwnerIdAndResolvedAtIsNotNull(long owner);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LostProperty p where p.id = :id")
    Optional<LostProperty> lock(@Param("id") long id);
}
