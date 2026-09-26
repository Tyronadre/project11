package de.tyro.project11.calendar;

import de.tyro.project11.attendance.AttendanceMailSettings;
import de.tyro.project11.registration.UserRepository;
import jakarta.mail.MessagingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "app.attendance-mail.enabled", havingValue = "true")
public class EventChangeMailDelivery {
    private final EventChangeEmailRepository emails;
    private final UserRepository users;
    private final JavaMailSender sender;
    private final AttendanceMailSettings settings;
    private final Clock clock;
    public EventChangeMailDelivery(EventChangeEmailRepository emails, UserRepository users, JavaMailSender sender, AttendanceMailSettings settings, Clock clock) {
        this.emails = emails; this.users = users; this.sender = sender; this.settings = settings; this.clock = clock;
    }
    @Transactional
    public void deliver(long id) {
        var email = emails.findLockedById(id).orElse(null);
        if (email == null || !email.due(clock.instant())) return;
        var user = users.findById(email.getUserId()).orElse(null);
        if (user == null) { email.failed(clock.instant()); return; }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(settings.from()); helper.setTo(user.getEmail());
            helper.setSubject("Project 11 · " + email.getSubject());
            String stamp = email.getCreatedAt().atZone(CalendarTime.BERLIN).format(DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm z"));
            helper.setText("Hallo " + user.getDisplayName() + ",\n\n" + email.getSubject() + " · Änderung " + email.getRevision() + " vom " + stamp
                    + "\n\n" + email.getBody() + "\n\nAktueller Stand: " + settings.baseUrl() + "/events/" + email.getActivityId()
                    + "\n\nViele Grüße\nProject 11", false);
            sender.send(message); email.sent();
        } catch (MailException | MessagingException exception) {
            email.failed(clock.instant());
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Event notification {} failed on attempt {}", id, email.getAttempts());
        }
    }
}
