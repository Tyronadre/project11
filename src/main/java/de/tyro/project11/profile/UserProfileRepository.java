package de.tyro.project11.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    interface ProfileColor {
        Long getUserId();
        String getColor();
    }

    @Query("select profile.id as userId, profile.color as color from UserProfile profile where profile.id in :userIds")
    List<ProfileColor> findColorsByUserIds(@Param("userIds") Collection<Long> userIds);
}
