package de.tyro.project11.registration;

import jakarta.validation.Valid;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

@Service
@Validated
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public RegistrationService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public void register(@Valid RegistrationForm form) {
        String hash = passwords.encode(form.getPassword());
        try {
            // Flush here so a constraint error is raised inside this method, before commit.
            users.saveAndFlush(new AppUser(form.getDisplayName(), form.getEmail(), hash));
        } catch (DataIntegrityViolationException exception) {
            // The database constraint also handles two simultaneous registrations.
            if (isDuplicateEmail(exception)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
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
