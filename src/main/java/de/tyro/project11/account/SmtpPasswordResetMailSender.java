package de.tyro.project11.account;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(name = "app.password-reset-mail.enabled", havingValue = "true")
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailSender.class);

    private final JavaMailSender sender;
    private final PasswordResetMailSettings settings;

    public SmtpPasswordResetMailSender(JavaMailSender sender, PasswordResetMailSettings settings,
                                       @Value("${spring.mail.host:}") String host) {
        if (host.isBlank()) {
            throw new IllegalArgumentException("Password reset email requires SMTP_HOST.");
        }
        this.sender = sender;
        this.settings = settings;
    }

    @Override
    public void send(String email, String displayName, String rawToken) {
        String resetUrl = settings.baseUrl() + "/reset-password?token=" + rawToken;
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from());
            helper.setTo(email);
            helper.setSubject("Project 11 · Passwort zurücksetzen");
            helper.setText("Hallo " + displayName + ",\n\n"
                    + "über diesen Link kannst du dein Passwort zurücksetzen:\n" + resetUrl + "\n\n"
                    + "Der Link ist 30 Minuten gültig und kann nur einmal verwendet werden. "
                    + "Falls du das nicht angefordert hast, kannst du diese Nachricht ignorieren.\n\n"
                    + "Viele Grüße\nProject 11", false);
            sender.send(message);
        } catch (MailException | MessagingException exception) {
            // Never log addresses, tokens, SMTP responses, or credentials.
            log.warn("Password reset email could not be delivered");
        }
    }
}
