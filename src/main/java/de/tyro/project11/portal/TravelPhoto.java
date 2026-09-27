package de.tyro.project11.portal;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "travel_photos", indexes = @Index(name = "idx_travel_photo_draft", columnList = "draft_key, owner_id"))
public class TravelPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id") private TravelApplication application;
    @Column(name = "draft_key", nullable = false, length = 36) private String draftKey;
    @Column(name = "original_name", nullable = false, length = 200) private String originalName;
    @Column(name = "content_type", nullable = false, length = 30) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Lob @Column(nullable = false) private byte[] content;

    protected TravelPhoto() {}
    public TravelPhoto(AppUser owner, String draftKey, String originalName, String contentType, byte[] content, OffsetDateTime now) {
        this.owner = owner; this.draftKey = draftKey; this.originalName = originalName;
        this.contentType = contentType; this.content = content; this.sizeBytes = content.length; this.createdAt = now;
    }
    public Long getId() { return id; }
    public void accountForUpload(long bytes) { sizeBytes = Math.max(sizeBytes, bytes); }
    public AppUser getOwner() { return owner; }
    public TravelApplication getApplication() { return application; }
    public String getDraftKey() { return draftKey; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
