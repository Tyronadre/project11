package de.tyro.project11.portal.leisure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LeisureCaseRepository extends JpaRepository<LeisureCase, Long> {
    Page<LeisureCase> findByOwnerIdOrderByCreatedAtDescIdDesc(long owner, Pageable page);
    Optional<LeisureCase> findByOwnerIdAndSubmissionToken(long owner, String token);
    Optional<LeisureCase> findFirstByOwnerIdAndKindAndCompletedAtIsNull(long owner, LeisureKind kind);
    long countByOwnerId(long owner);
    long countByOwnerIdAndCompletedAtIsNotNull(long owner);
    long countByOwnerIdAndKind(long owner, LeisureKind kind);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LeisureCase c where c.id = :id")
    Optional<LeisureCase> lock(@Param("id") long id);
}
