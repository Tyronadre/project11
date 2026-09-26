package de.tyro.project11.portal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AbsenceApplicationRepository extends JpaRepository<AbsenceApplication, Long> {
    List<AbsenceApplication> findByApplicantIdAndDecidedAtIsNotNullOrderByDecidedAtDescIdDesc(long applicantId);
    @org.springframework.data.jpa.repository.Query("select a.activity.id from AbsenceApplication a where a.id = :id")
    Optional<Long> findActivityIdById(long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AbsenceApplication a where a.id = :id")
    Optional<AbsenceApplication> findLockedById(long id);
    @EntityGraph(attributePaths = "applicant")
    List<AbsenceApplication> findByActivityId(long activityId);

    @EntityGraph(attributePaths = "applicant")
    Page<AbsenceApplication> findAllByOrderBySubmittedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "applicant")
    Page<AbsenceApplication> findByApplicantIdOrderBySubmittedAtDescIdDesc(long applicantId, Pageable pageable);

    Optional<AbsenceApplication> findByApplicantIdAndActivityId(long applicantId, long activityId);
    long countByApplicantId(long applicantId);
}
