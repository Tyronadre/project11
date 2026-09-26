package de.tyro.project11.account;

import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.EmailAlreadyRegisteredException;
import de.tyro.project11.registration.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class AccountService {

    public enum ChangeResult {
        CHANGED,
        WRONG_PASSWORD,
        SAME_EMAIL,
        SAME_PASSWORD
    }

    public record AccountView(String displayName, String email) {
    }

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordResetTokenRepository resetTokens,
                          PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountView load(String email) {
        AppUser user = currentUser(email);
        return new AccountView(user.getDisplayName(), user.getEmail());
    }

    @Transactional
    public ChangeResult changeEmail(String currentEmail, EmailChangeForm form) {
        AppUser user = lockedCurrentUser(currentEmail);
        if (!passwords.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            return ChangeResult.WRONG_PASSWORD;
        }
        if (user.getEmail().equals(form.getEmail())) {
            return ChangeResult.SAME_EMAIL;
        }
        if (users.existsByEmail(form.getEmail())) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            user.changeEmail(form.getEmail());
            invalidateResetTokens(user.getId());
            users.saveAndFlush(user);
            return ChangeResult.CHANGED;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
    }

    @Transactional
    public ChangeResult changePassword(String email, AccountPasswordForm form) {
        AppUser user = lockedCurrentUser(email);
        if (!passwords.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            return ChangeResult.WRONG_PASSWORD;
        }
        if (passwords.matches(form.getNewPassword(), user.getPasswordHash())) {
            return ChangeResult.SAME_PASSWORD;
        }
        user.changePassword(passwords.encode(form.getNewPassword()));
        invalidateResetTokens(user.getId());
        return ChangeResult.CHANGED;
    }

    private void invalidateResetTokens(long userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        resetTokens.findByUserIdAndUsedAtIsNull(userId).forEach(token -> token.consume(now));
    }

    private AppUser lockedCurrentUser(String email) {
        return users.findLockedByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
    }

    private AppUser currentUser(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
    }

    private boolean isDuplicateEmail(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("uk_app_users_email")) {
                return true;
            }
        }
        return false;
    }
}
