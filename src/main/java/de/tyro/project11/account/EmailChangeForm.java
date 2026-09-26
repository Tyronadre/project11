package de.tyro.project11.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public class EmailChangeForm {

    @NotBlank(message = "Bitte gib deine neue E-Mail-Adresse ein.")
    @Email(message = "Bitte gib eine gültige E-Mail-Adresse ein.")
    @Size(max = 254, message = "Die E-Mail-Adresse ist zu lang.")
    private String email;

    @NotBlank(message = "Bitte bestätige die Änderung mit deinem Passwort.")
    @Size(max = 72, message = "Das Passwort ist zu lang.")
    private String currentPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
