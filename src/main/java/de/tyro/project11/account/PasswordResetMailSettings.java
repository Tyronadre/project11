package de.tyro.project11.account;

import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("app.password-reset-mail")
public record PasswordResetMailSettings(boolean enabled, String from, String baseUrl) {
    public PasswordResetMailSettings {
        from = from == null ? "" : from.strip();
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        if (enabled) {
            try {
                if (from.isBlank() || from.contains("\r") || from.contains("\n")) {
                    throw new IllegalArgumentException();
                }
                new InternetAddress(from, true).validate();
                var uri = URI.create(baseUrl);
                if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Password reset email requires a valid MAIL_FROM and absolute HTTP(S) PUBLIC_URL."
                );
            }
        }
    }
}
