package de.tyro.project11.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;

@Component
@ConditionalOnProperty(name = "app.attendance-mail.enabled", havingValue = "true")
public class AttendanceMailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AttendanceMailDispatcher.class);
    private final AttendanceEmailRepository emails;
    private final AttendanceMailDelivery delivery;
    private final Clock clock;
    private final de.tyro.project11.calendar.EventChangeEmailRepository eventEmails;
    private final de.tyro.project11.calendar.EventChangeMailDelivery eventDelivery;
    private final de.tyro.project11.portal.AbsenceDecisionEmailRepository decisions;
    private final de.tyro.project11.portal.TravelDecisionEmailRepository travelDecisions;
    private final de.tyro.project11.portal.TravelDecisionMailDelivery travelDelivery;

    public AttendanceMailDispatcher(AttendanceEmailRepository emails, AttendanceMailDelivery delivery, Clock clock,
                                    de.tyro.project11.portal.AbsenceDecisionEmailRepository decisions,
                                    de.tyro.project11.portal.TravelDecisionEmailRepository travelDecisions,
                                    de.tyro.project11.portal.TravelDecisionMailDelivery travelDelivery,
                                    de.tyro.project11.calendar.EventChangeEmailRepository eventEmails,
                                    de.tyro.project11.calendar.EventChangeMailDelivery eventDelivery) {
        this.emails = emails; this.delivery = delivery; this.clock = clock; this.decisions = decisions;
        this.eventEmails = eventEmails; this.eventDelivery = eventDelivery;
        this.travelDecisions = travelDecisions; this.travelDelivery = travelDelivery;
    }

    @Scheduled(initialDelayString = "${app.attendance-mail.initial-delay:15000}", fixedDelay = 30000)
    public void dispatch() {
        for (var email : eventEmails.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                de.tyro.project11.calendar.EventChangeEmail.Status.PENDING, clock.instant())) {
            try { eventDelivery.deliver(email.getId()); }
            catch (RuntimeException exception) { log.warn("Event email {} could not be processed ({})", email.getId(), exception.getClass().getSimpleName()); }
        }
        for (var email : travelDecisions.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                de.tyro.project11.portal.TravelDecisionEmail.Status.PENDING, clock.instant())) {
            try { travelDelivery.deliver(email.getId(), email.getUserId()); }
            catch (RuntimeException exception) {
                log.warn("Travel decision email {} could not be processed ({})", email.getId(), exception.getClass().getSimpleName());
            }
        }
        for (var email : decisions.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                de.tyro.project11.portal.AbsenceDecisionEmail.Status.PENDING, clock.instant())) {
            try { delivery.deliverDecision(email.getId(), email.getActivityId()); }
            catch (RuntimeException exception) {
                log.warn("AaA decision email {} could not be processed ({})", email.getId(), exception.getClass().getSimpleName());
            }
        }
        for (var email : emails.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                AttendanceEmail.Status.PENDING, clock.instant())) {
            try {
                delivery.deliver(email.getId(), email.getActivityId());
            } catch (RuntimeException exception) {
                log.warn("Attendance email {} could not be processed ({})", email.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
