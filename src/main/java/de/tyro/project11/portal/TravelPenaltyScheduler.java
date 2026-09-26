package de.tyro.project11.portal;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TravelPenaltyScheduler {
    private final TravelApplicationRepository applications;
    private final TravelPenaltyService penalties;
    public TravelPenaltyScheduler(TravelApplicationRepository applications, TravelPenaltyService penalties) {
        this.applications = applications; this.penalties = penalties;
    }
    @Scheduled(initialDelayString = "${app.travel-penalties.initial-delay:15000}", fixedDelay = 60000)
    public void processDue() {
        for (long userId : applications.findApplicantIdsByKindAndDecision(TravelKind.LEAVE, TravelApplication.Decision.ACCEPTED)) {
            try { penalties.reconcile(userId); }
            catch (RuntimeException exception) {
                LoggerFactory.getLogger(getClass()).warn("Travel settlement failed for member {} ({})", userId, exception.getClass().getSimpleName());
            }
        }
    }
}
