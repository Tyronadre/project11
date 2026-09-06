package de.tyro.project11.registration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

public class RegistrationForm {

    @NotBlank(message = "Enter your name.")
    @Size(max = 80, message = "Use no more than 80 characters for your name.")
    private String displayName;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Use no more than 254 characters for your email.")
    private String email;

    @NotBlank(message = "Choose a password.")
    @Size(min = 12, max = 72, message = "Use between 12 and 72 characters for your password.")
    private String password;

    @NotBlank(message = "Enter your password again.")
    @Size(max = 72, message = "Use no more than 72 characters.")
    private String confirmPassword;

    @AssertTrue(message = "Your passwords do not match.")
    public boolean isPasswordsMatching() {
        return Objects.equals(password, confirmPassword);
    }

    @AssertTrue(message = "Your password is too long in UTF-8. Use fewer accented characters or emoji (72 bytes maximum).")
    public boolean isPasswordWithinByteLimit() {
        // BCrypt accepts at most 72 bytes, which can be fewer than 72 characters.
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.strip();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // This project deliberately treats the whole email address as case-insensitive.
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
