package de.tyro.project11.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public final class AppUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private final String passwordHash;
    private final boolean admin;
    private final long credentialVersion;

    public AppUserPrincipal(long userId, String email, String passwordHash, boolean admin, long credentialVersion) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.admin = admin;
        this.credentialVersion = credentialVersion;
    }

    public long userId() {
        return userId;
    }

    public long credentialVersion() {
        return credentialVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
