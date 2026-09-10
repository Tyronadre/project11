package de.tyro.project11.registration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "app_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_users_email", columnNames = "email"),
        check = @CheckConstraint(name = "ck_app_users_tally_nonnegative", constraint = "tally_count >= 0"))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 254)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;

    @Column(name = "is_admin", nullable = false)
    @ColumnDefault("FALSE")
    private boolean admin;

    @Column(name = "tally_count", nullable = false)
    @ColumnDefault("0")
    private int tallyCount;

    protected AppUser() {
        // Required by JPA when loading a row.
    }

    public AppUser(String displayName, String email, String passwordHash) {
        this(displayName, email, passwordHash, false);
    }

    public AppUser(String displayName, String email, String passwordHash, boolean admin) {
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.admin = admin;
        this.tallyCount = 0;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public int getTallyCount() {
        return tallyCount;
    }

    public void setTallyCount(int tallyCount) {
        if (tallyCount < 0) {
            throw new IllegalArgumentException("A tally cannot be negative.");
        }
        this.tallyCount = tallyCount;
    }
}
