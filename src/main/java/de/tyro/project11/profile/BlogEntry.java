package de.tyro.project11.profile;
import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "blog_entries", indexes = @Index(name = "idx_blog_author_created", columnList = "author_id,created_at"))
public class BlogEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private AppUser author;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @ElementCollection @CollectionTable(name = "blog_blocks", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "block_position") private List<BlogBlock> blocks = new ArrayList<>();
    protected BlogEntry() {}
    public BlogEntry(AppUser author, OffsetDateTime now) { this.author = author; this.createdAt = now; }
    public void update(BlogForm form, OffsetDateTime now) {
        title = form.getTitle().strip(); updatedAt = now; blocks.clear(); blocks.addAll(form.getBlocks());
    }
    public Long getId() { return id; }
    public AppUser getAuthor() { return author; }
    public String getTitle() { return title; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<BlogBlock> getBlocks() { return List.copyOf(blocks); }
}
