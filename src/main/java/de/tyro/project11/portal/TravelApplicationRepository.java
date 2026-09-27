package de.tyro.project11.portal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TravelApplicationRepository extends JpaRepository<TravelApplication, Long> {
    long countByDecision(TravelApplication.Decision decision);
    @org.springframework.data.jpa.repository.Query("select count(a) from TravelApplication a where a.decision is null or a.decision = de.tyro.project11.portal.TravelApplication.Decision.PENDING")
    long countPending();
    java.util.List<TravelApplication> findByApplicantIdAndDecidedAtIsNotNullOrderByDecidedAtDescIdDesc(long applicantId);
    @org.springframework.data.jpa.repository.Query("select a.applicant.id from TravelApplication a where a.id = :id")
    Optional<Long> findApplicantIdById(long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from TravelApplication a where a.id = :id")
    Optional<TravelApplication> findLockedById(long id);
    @org.springframework.data.jpa.repository.Query("select distinct a.applicant.id from TravelApplication a where a.kind = :kind and a.decision = :decision")
    java.util.List<Long> findApplicantIdsByKindAndDecision(TravelKind kind, TravelApplication.Decision decision);
    @EntityGraph(attributePaths = {"applicant", "holiday"})
    Page<TravelApplication> findAllByOrderBySubmittedAtDescIdDesc(Pageable pageable);
    @EntityGraph(attributePaths = {"applicant", "holiday"})
    Page<TravelApplication> findByApplicantIdOrderBySubmittedAtDescIdDesc(long applicantId, Pageable pageable);
    @EntityGraph(attributePaths = "holiday")
    java.util.List<TravelApplication> findByApplicantIdAndKind(long applicantId, TravelKind kind);
    Optional<TravelApplication> findByKindAndHolidayId(TravelKind kind, long holidayId);
    Optional<TravelApplication> findByFilingKeyAndApplicantId(String filingKey, long applicantId);
    long countByApplicantId(long applicantId);
    long countByApplicantIdAndKind(long applicantId, TravelKind kind);
    boolean existsByKindAndHolidayId(TravelKind kind, long holidayId);
}
