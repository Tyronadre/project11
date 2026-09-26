package de.tyro.project11.portal;

import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.registration.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TravelReviewService {
    private final TravelApplicationRepository applications;
    private final TravelPhotoRepository photos;
    private final UserRepository users;
    private final TravelPenaltyService penalties;
    private final TravelDecisionEmailRepository emails;
    private final Clock clock;
    public TravelReviewService(TravelApplicationRepository applications, TravelPhotoRepository photos, UserRepository users,
                               TravelPenaltyService penalties, TravelDecisionEmailRepository emails, Clock clock) {
        this.applications = applications; this.photos = photos; this.users = users;
        this.penalties = penalties; this.emails = emails; this.clock = clock;
    }
    public record Review(long id, String kind, String applicant, String title, String submitted, String deadline,
                         String decision, String reason, String leaveDecision, boolean late, boolean invalidated,
                         List<ApplicationAnswer> answers, List<TravelPhotoRepository.Info> photos, Long relatedId) {
        public boolean pending() { return decision.equals("PENDING"); }
        public boolean canAccept() { return pending() && (kind.equals("AaB") || leaveDecision.equals("ACCEPTED")); }
    }
    @Transactional(readOnly = true)
    public Page<Review> list(String email, int page) {
        requireAdmin(email);
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return applications.findAllByOrderBySubmittedAtDescIdDesc(PageRequest.of(page, 20)).map(a -> view(a, false));
    }
    @Transactional(readOnly = true)
    public Review file(long id, String email) { requireAdmin(email); return view(application(id), true); }
    @Transactional
    public void decide(long id, TravelApplication.Decision decision, String reason, String email) {
        var admin = requireAdmin(email);
        var ownerId = applications.findApplicantIdById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Travel operations lock the owner first; they never take an event lock afterwards.
        users.findLockedById(ownerId).orElseThrow();
        var application = applications.findLockedById(id).orElseThrow();
        if (decision == TravelApplication.Decision.PENDING) throw badRequest("Bitte annehmen oder ablehnen.");
        if (application.getDecision() == decision) return;
        if (application.getDecision() != TravelApplication.Decision.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Antrag wurde bereits entschieden.");
        reason = reason == null ? "" : reason.strip();
        if (reason.length() > 2000 || (decision == TravelApplication.Decision.REJECTED && reason.isEmpty()))
            throw badRequest("Für eine Ablehnung ist eine Begründung erforderlich (maximal 2.000 Zeichen).");
        if (application.getKind() == TravelKind.REPORT && decision == TravelApplication.Decision.ACCEPTED) {
            var leave = applications.findByKindAndHolidayId(TravelKind.LEAVE, application.getHoliday().getId());
            if (leave.isPresent() && leave.get().getDecision() != TravelApplication.Decision.ACCEPTED)
                throw badRequest("Bitte zuerst den zugehörigen AaB annehmen. Ein Bericht zu einem abgelehnten AaB kann nicht bestätigt werden.");
        }
        application.decide(decision, admin, OffsetDateTime.now(clock), reason);
        applications.saveAndFlush(application);
        emails.save(new TravelDecisionEmail(id, ownerId, clock.instant()));
        penalties.reconcile(ownerId);
    }
    private Review view(TravelApplication a, boolean full) {
        var holiday = a.getHoliday();
        var leave = applications.findByKindAndHolidayId(TravelKind.LEAVE, holiday.getId());
        var related = applications.findByKindAndHolidayId(a.getKind() == TravelKind.LEAVE ? TravelKind.REPORT : TravelKind.LEAVE, holiday.getId());
        var stamp = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm z", Locale.GERMAN);
        return new Review(a.getId(), a.getKind().code, a.getApplicant().getDisplayName(), a.getSubject(),
                a.getSubmittedAt().atZoneSameInstant(CalendarTime.BERLIN).format(stamp),
                holiday.getEndsOn().plusDays(7).format(DateTimeFormatter.ofPattern("dd.MM.uuuu")) + ", einschließlich (Berlin)",
                a.getDecision().name(), a.getDecisionReason(), leave.map(l -> l.getDecision().name()).orElse("ACCEPTED"),
                a.getKind() == TravelKind.REPORT && !TravelRules.timely(a), holiday.isInvalidated(),
                full ? a.getAnswers() : List.of(), full ? photos.findByApplicationIdOrderByIdAsc(a.getId()) : List.of(),
                related.map(TravelApplication::getId).orElse(null));
    }
    private TravelApplication application(long id) { return applications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private AppUser requireAdmin(String email) {
        var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Account not found"));
        if (!user.isAdmin()) throw new AccessDeniedException("Nur Admins dürfen AaBs und Reiseberichte bearbeiten.");
        return user;
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
