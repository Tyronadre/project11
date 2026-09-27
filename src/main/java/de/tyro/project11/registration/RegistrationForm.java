package de.tyro.project11.registration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

public class RegistrationForm {

    @NotBlank(message = "Bitte gib deinen Namen ein.")
    @Size(max = 80, message = "Dein Name darf höchstens 80 Zeichen enthalten.")
    private String displayName;

    @NotBlank(message = "Bitte gib deine E-Mail-Adresse ein.")
    @Email(message = "Bitte gib eine gültige E-Mail-Adresse ein.")
    @Size(max = 254, message = "Deine E-Mail-Adresse darf höchstens 254 Zeichen enthalten.")
    private String email;

    @NotBlank(message = "Bitte wähle ein Passwort.")
    @Size(min = 4, max = 72, message = "Dein Passwort muss zwischen 4 und 72 Zeichen lang sein.")
    private String password;

    @NotBlank(message = "Bitte gib dein Passwort erneut ein.")
    @Size(max = 72, message = "Bitte verwende höchstens 72 Zeichen.")
    private String confirmPassword;

    @AssertTrue(message = "Deine Passwörter stimmen nicht überein.")
    public boolean isPasswordsMatching() {
        return Objects.equals(password, confirmPassword);
    }

    @AssertTrue(message = "Dein Passwort ist in UTF-8 zu lang. Verwende weniger Sonderzeichen oder Emojis (höchstens 72 Bytes).")
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
