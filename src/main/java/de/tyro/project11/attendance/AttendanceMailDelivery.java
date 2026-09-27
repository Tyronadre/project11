package de.tyro.project11.attendance;

import de.tyro.project11.calendar.ActivityRepository;
import de.tyro.project11.portal.*;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Service
@ConditionalOnProperty(name = "app.attendance-mail.enabled", havingValue = "true")
public class AttendanceMailDelivery {
    private static final Logger log = LoggerFactory.getLogger(AttendanceMailDelivery.class);
    private final AttendanceEmailRepository emails;
    private final ActivityRepository activities;
    private final AttendanceService attendance;
    private final JavaMailSender sender;
    private final AttendanceMailSettings settings;
    private final Clock clock;
    private final AbsenceDecisionEmailRepository decisionEmails;
    private final AbsenceApplicationRepository applications;

    public AttendanceMailDelivery(AttendanceEmailRepository emails, ActivityRepository activities,
                                  AttendanceService attendance, JavaMailSender sender,
                                  AttendanceMailSettings settings, Clock clock,
                                  @Value("${spring.mail.host:}") String host, AbsenceDecisionEmailRepository decisionEmails, AbsenceApplicationRepository applications) {
        if (host.isBlank()) throw new IllegalArgumentException("Attendance email requires SMTP_HOST.");
        this.emails = emails; this.activities = activities; this.attendance = attendance;
        this.sender = sender; this.settings = settings; this.clock = clock;
        this.decisionEmails = decisionEmails; this.applications = applications;
    }

    @Transactional
    public void deliver(long emailId, long activityId) {
        // Use the same lock order as attendance saves. Other workers cannot send the same reminder concurrently.
        boolean eventExists = activities.findLockedById(activityId).isPresent();
        var email = emails.findLockedById(emailId).orElse(null);
        if (email == null || !email.due(clock.instant())) return;
        var reminder = eventExists ? attendance.mailReminder(activityId, email.getUserId()).orElse(null) : null;
        if (reminder == null) { email.cancel(); return; }

        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from());
            helper.setTo(reminder.email());
            helper.setSubject("Project 11 · Ein Strich ist vorgemerkt");
            helper.setText(body(activityId, reminder), false);
            sender.send(message);
            email.sent(clock.instant());
            log.info("Attendance email {} accepted by SMTP server", emailId);
        } catch (MailException | MessagingException exception) {
            email.failed(clock.instant());
            // Provider errors can include addresses or credentials; log only queue IDs and retry state.
            log.warn("Attendance email {} failed on attempt {}; status={}", emailId, email.getAttempts(), email.getStatus());
        }
    }

    @Transactional
    public void deliverDecision(long applicationId, long activityId) {
        boolean exists = activities.findLockedById(activityId).filter(a -> !a.isCancelled()).isPresent();
        var email = decisionEmails.findLockedById(applicationId).orElse(null);
        if (email == null || !email.due(clock.instant())) return;
        var application = exists ? applications.findById(applicationId).orElse(null) : null;
        if (application == null || application.getDecision() == AbsenceApplication.Decision.PENDING) { email.cancel(); return; }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from()); helper.setTo(application.getApplicant().getEmail());
            String decision = application.getDecision() == AbsenceApplication.Decision.ACCEPTED ? "angenommen" : "abgelehnt";
            helper.setSubject("Project 11 · Dein AaA wurde " + decision + " · " + de.tyro.project11.portal.CaseReference.of(application));
            helper.setText("Hallo " + application.getApplicant().getDisplayName() + ",\n\n"
                    + "dein Antrag auf Abwesenheit zum Event „" + application.getActivityTitle() + "“ wurde " + decision + ".\n\n"
                    + (application.getDecisionReason().isBlank() ? "" : "Begründung: " + application.getDecisionReason() + "\n\n")
                    + (application.getDecision() == AbsenceApplication.Decision.ACCEPTED
                        ? "Deine Abwesenheit ist damit entschuldigt."
                        : "Bei bestätigter Abwesenheit ohne abdeckenden Urlaub wird ab Ablauf der Einreichungsfrist ein Strich vergeben. Eine erneute Einreichung für dieses Event ist nicht möglich.")
                    + "\n\nEvent: " + settings.baseUrl() + "/events/" + activityId
                    + "\nAktenzeichen: " + de.tyro.project11.portal.CaseReference.of(application)
                    + "\nBescheid: " + settings.baseUrl() + "/amt/antraege/" + applicationId + "/bescheid"
                    + "\nAntragsakte: " + settings.baseUrl() + "/amt/antraege/" + applicationId
                    + "\n\nViele Grüße\nProject 11", false);
            sender.send(message); email.sent(clock.instant());
        } catch (MailException | MessagingException exception) {
            email.failed(clock.instant());
            log.warn("AaA decision email {} failed on attempt {}; status={}", applicationId, email.getAttempts(), email.getStatus());
        }
    }

    private String body(long activityId, AttendanceService.MailReminder reminder) {
        var action = reminder.expired()
                ? "Die Frist für einen Antrag auf Abwesenheit (AaA) ist bereits abgelaufen ("
                  + reminder.deadline() + ", Berliner Zeit). "
                  + "Für dieses Event kann kein AaA mehr eingereicht werden."
                : "Bitte reiche deinen Antrag auf Abwesenheit (AaA) vor " + reminder.deadline()
                  + " ein. Alle Zeiten gelten für Berlin.\n\nAaA einreichen:\n"
                  + settings.baseUrl() + "/amt/aaa?activity=" + activityId;
        return "Hallo " + reminder.name() + ",\n\n"
                + "für das Event „" + reminder.title() + "“ (beendet am " + reminder.ended() + ") "
                + "wurdest du nicht als anwesend erfasst. Es liegt kein fristgerechter AaA oder vollständig "
                + "abdeckender Urlaub vor. Deshalb ist ein Strich für dich vorgemerkt.\n\n"
                + "Ab Ablauf der Frist wird daraus automatisch ein Strich. Ein rechtzeitiger AaA hält dies bis zur Admin-Entscheidung zurück.\n\n"
                + action + "\n\nEvent und Anwesenheitsliste:\n" + settings.baseUrl() + "/events/" + activityId
                + "\n\nFalls du dabei warst, bitte einen Admin, die Anwesenheitsliste zu korrigieren."
                + "\n\nViele Grüße\nProject 11\n";
    }
}
