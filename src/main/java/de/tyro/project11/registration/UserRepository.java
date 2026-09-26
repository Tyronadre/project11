package de.tyro.project11.registration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.email = :email")
    Optional<AppUser> findLockedByEmail(@Param("email") String email);

    boolean existsByEmail(String email);

    List<AppUser> findAllByOrderByDisplayNameAsc();

    long countByAdminTrue();

    Optional<AppUser> findFirstByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> findLockedById(@Param("id") Long id);
}
