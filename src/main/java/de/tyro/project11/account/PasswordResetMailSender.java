package de.tyro.project11.account;

public interface PasswordResetMailSender {
    void send(String email, String displayName, String rawToken);
}
