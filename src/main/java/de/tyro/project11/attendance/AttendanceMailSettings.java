package de.tyro.project11.attendance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import jakarta.mail.internet.InternetAddress;

@ConfigurationProperties("app.attendance-mail")
public record AttendanceMailSettings(boolean enabled, String from, String baseUrl) {
    public AttendanceMailSettings {
        from = from == null ? "" : from.strip();
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        if (enabled) {
            try {
                if (from.isBlank() || from.contains("\r") || from.contains("\n")) throw new IllegalArgumentException();
                new InternetAddress(from, true).validate();
                var uri = URI.create(baseUrl);
                if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            } catch (Exception exception) {
                throw new IllegalArgumentException("Attendance email requires a valid MAIL_FROM and absolute HTTP(S) PUBLIC_URL.");
            }
        }
    }
}
