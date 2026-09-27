package de.tyro.project11.portal;

import de.tyro.project11.attendance.AttendanceMailSettings;
import de.tyro.project11.registration.UserRepository;
import jakarta.mail.MessagingException;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "app.attendance-mail.enabled", havingValue = "true")
public class TravelDecisionMailDelivery {
    private final TravelDecisionEmailRepository emails;
    private final TravelApplicationRepository applications;
    private final UserRepository users;
    private final JavaMailSender sender;
    private final AttendanceMailSettings settings;
    private final Clock clock;
    public TravelDecisionMailDelivery(TravelDecisionEmailRepository emails, TravelApplicationRepository applications, UserRepository users,
                                      JavaMailSender sender, AttendanceMailSettings settings, Clock clock) {
        this.emails = emails; this.applications = applications; this.users = users;
        this.sender = sender; this.settings = settings; this.clock = clock;
    }
    @Transactional
    public void deliver(long applicationId, long userId) {
        var owner = users.findLockedById(userId).orElse(null);
        var email = emails.findLockedById(applicationId).orElse(null);
        if (email == null || !email.due(clock.instant())) return;
        var application = applications.findById(applicationId).orElse(null);
        if (owner == null || application == null || application.getDecision() == TravelApplication.Decision.PENDING) { email.cancel(); return; }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from()); helper.setTo(owner.getEmail());
            boolean accepted = application.getDecision() == TravelApplication.Decision.ACCEPTED;
            String decision = accepted ? "angenommen" : "abgelehnt";
            var holiday = application.getHoliday();
            String deadline = holiday.getEndsOn().plusDays(7).format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
            String consequence;
            if (application.getKind() == TravelKind.LEAVE) {
                consequence = accepted
                        ? "Bitte reiche deinen Reisebericht bis einschließlich " + deadline + " (Berlin) ein. Er muss anschließend von einem Admin bestätigt werden."
                        : "Dieser AaB entschuldigt keine Event-Abwesenheit. Für betroffene Events gelten die jeweiligen AaA-Fristen.";
            } else {
                consequence = accepted && TravelRules.timely(application) && !holiday.isInvalidated()
                        ? "Dein fristgerechter Reisebericht wurde bestätigt."
                        : "Ohne fristgerechten, bestätigten Reisebericht wird der angenommene Urlaub nach Ablauf der Berichtsfrist ungültig: ein Strich pro Urlaubstag, ohne zusätzliche Event-Striche im abgedeckten Zeitraum. Eine verspätete Bestätigung hebt dies nicht auf.";
            }
            if (holiday.isInvalidated()) consequence += "\nDieser Urlaub ist bereits wegen der abgelaufenen Berichtsfrist ungültig. Die Tagesstriche bleiben bestehen.";
            helper.setSubject("Project 11 · " + application.getKind().code + " " + decision + " · " + CaseReference.of(application));
            helper.setText("Hallo " + owner.getDisplayName() + ",\n\ndein " + application.getKind().code + " zu „" + application.getSubject()
                    + "“ wurde " + decision + ".\n\n"
                    + (application.getDecisionReason().isBlank() ? "" : "Begründung: " + application.getDecisionReason() + "\n\n")
                    + consequence + "\n\nAktenzeichen: " + CaseReference.of(application)
                    + "\nBescheid: " + settings.baseUrl() + "/amt/reisen/" + applicationId + "/bescheid"
                    + "\nReiseakte: " + settings.baseUrl() + "/amt/reisen/" + applicationId
                    + (application.getKind() == TravelKind.LEAVE && accepted ? "\nBericht einreichen: " + settings.baseUrl() + "/amt/eer?holiday=" + holiday.getId() : "")
                    + "\n\nViele Grüße\nProject 11", false);
            sender.send(message); email.sent(clock.instant());
        } catch (MailException | MessagingException exception) {
            email.failed(clock.instant());
            LoggerFactory.getLogger(getClass()).warn("Travel decision email {} failed on attempt {}; status={}", applicationId, email.getAttempts(), email.getStatus());
        }
    }
}
