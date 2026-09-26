package de.tyro.project11.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;

public interface TravelPhotoRepository extends JpaRepository<TravelPhoto, Long> {
    interface Info {
        Long getId(); String getOriginalName(); String getContentType(); long getSizeBytes();
    }
    List<Info> findByDraftKeyAndOwnerIdAndApplicationIsNullOrderByIdAsc(String draftKey, long ownerId);
    List<Info> findByApplicationIdOrderByIdAsc(long applicationId);
    @Modifying
    @Query("update TravelPhoto p set p.application = :application where p.draftKey = :draft and p.owner.id = :owner and p.application is null")
    int fileDraft(@Param("draft") String draft, @Param("owner") long owner, @Param("application") TravelApplication application);
    @Modifying
    @Query("delete from TravelPhoto p where p.application is null and p.createdAt < :cutoff")
    int removeExpiredDrafts(@Param("cutoff") OffsetDateTime cutoff);
}
