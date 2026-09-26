package de.tyro.project11.profile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BlogEntryRepository extends JpaRepository<BlogEntry, Long> {
    List<BlogEntry> findByAuthorIdOrderByCreatedAtDescIdDesc(long authorId);
}
