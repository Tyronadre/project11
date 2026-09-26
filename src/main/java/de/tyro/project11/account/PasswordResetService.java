package de.tyro.project11.account;

import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final Optional<PasswordResetMailSender> mailer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwords, Optional<PasswordResetMailSender> mailer,
                                Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        users.findLockedByEmail(email.strip().toLowerCase(Locale.ROOT)).ifPresent(this::issueToken);
    }

    @Transactional(readOnly = true)
    public boolean isUsable(String rawToken) {
        if (!hasValidShape(rawToken)) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return tokens.findByTokenHash(hashToken(rawToken)).filter(token -> token.usableAt(now)).isPresent();
    }

    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        if (!hasValidShape(rawToken)) {
            return false;
        }
        String hash = hashToken(rawToken);
        var userId = tokens.findUserIdByTokenHash(hash).orElse(null);
        if (userId == null) {
            return false;
        }
        AppUser user = users.findLockedById(userId).orElse(null);
        var token = tokens.findLockedByTokenHash(hash).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (user == null || token == null || !token.usableAt(now)) {
            return false;
        }
        user.changePassword(passwords.encode(newPassword));
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(active -> active.consume(now));
        return true;
    }

    private void issueToken(AppUser user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        var latest = tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (latest != null && latest.getCreatedAt().isAfter(now.minusMinutes(2))) {
            return;
        }
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(token -> token.consume(now));
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.saveAndFlush(new PasswordResetToken(user, hashToken(rawToken), now, now.plusMinutes(30)));
        mailer.ifPresent(sender -> sender.send(user.getEmail(), user.getDisplayName(), rawToken));
    }

    static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean hasValidShape(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '-' && character != '_') {
                return false;
            }
        }
        return true;
    }
}
