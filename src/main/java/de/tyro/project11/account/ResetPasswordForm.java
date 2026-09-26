package de.tyro.project11.account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ResetPasswordForm {

    @NotBlank
    @Size(max = 128)
    private String token;

    @NotBlank(message = "Bitte wähle ein neues Passwort.")
    @Size(min = 12, max = 72, message = "Das Passwort muss 12 bis 72 Zeichen lang sein.")
    private String newPassword;

    @NotBlank(message = "Bitte wiederhole das neue Passwort.")
    @Size(max = 72, message = "Das Passwort ist zu lang.")
    private String confirmPassword;

    @AssertTrue(message = "Die Passwörter stimmen nicht überein.")
    public boolean isPasswordsMatching() {
        return Objects.equals(newPassword, confirmPassword);
    }

    @AssertTrue(message = "Das Passwort ist in UTF-8 zu lang (maximal 72 Byte).")
    public boolean isPasswordWithinByteLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
