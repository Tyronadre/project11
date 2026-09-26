package de.tyro.project11.portal;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.ActivityRepository;
import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AbsenceReviewService {
    private final AbsenceApplicationRepository applications;
    private final ActivityRepository activities;
    private final UserRepository users;
    private final AttendancePenaltyService penalties;
    private final AbsenceDecisionEmailRepository emails;
    private final Clock clock;
    public AbsenceReviewService(AbsenceApplicationRepository applications, ActivityRepository activities, UserRepository users,
                                AttendancePenaltyService penalties, AbsenceDecisionEmailRepository emails, Clock clock) {
        this.applications = applications; this.activities = activities; this.users = users;
        this.penalties = penalties; this.emails = emails; this.clock = clock;
    }
    public record Review(long id, String applicant, String title, String submitted, String deadline, boolean timely,
                         String decision, String reason, List<ApplicationAnswer> answers, boolean cancelled) {
        public boolean pending() { return !cancelled && timely && decision.equals("PENDING"); }
    }
    @Transactional(readOnly = true)
    public Page<Review> list(String email, int page) {
        requireAdmin(email);
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return applications.findAllByOrderBySubmittedAtDescIdDesc(PageRequest.of(page, 20)).map(this::view);
    }
    @Transactional(readOnly = true)
    public Review file(long id, String email) { requireAdmin(email); return view(application(id)); }
    @Transactional
    public void decide(long id, AbsenceApplication.Decision decision, String reason, String email) {
        var admin = requireAdmin(email);
        var activityId = applications.findActivityIdById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // All attendance, filing, adjudication, settlement and email workers lock the event first.
        if (activities.findLockedById(activityId).orElseThrow().isCancelled())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Das Event wurde abgesagt; ein AaA ist nicht mehr erforderlich.");
        var application = applications.findLockedById(id).orElseThrow();
        if (!AttendanceRules.timely(application)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verspätete AaAs sind nichtig und können nicht genehmigt werden.");
        if (decision == AbsenceApplication.Decision.PENDING) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte annehmen oder ablehnen.");
        if (application.getDecision() == decision) return; // Safe replay of the same decision.
        if (application.getDecision() != AbsenceApplication.Decision.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Antrag wurde bereits entschieden.");
        reason = reason == null ? "" : reason.strip();
        if (reason.length() > 2000 || (decision == AbsenceApplication.Decision.REJECTED && reason.isEmpty()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Für eine Ablehnung ist eine Begründung erforderlich (maximal 2.000 Zeichen).");
        application.decide(decision, admin, OffsetDateTime.now(clock), reason);
        applications.saveAndFlush(application);
        emails.save(new AbsenceDecisionEmail(id, application.getActivity().getId(), clock.instant()));
        penalties.reconcile(application.getActivity().getId());
    }
    private Review view(AbsenceApplication a) {
        var stamp = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm z", Locale.GERMAN);
        return new Review(a.getId(), a.getApplicant().getDisplayName(), a.getActivityTitle(),
                a.getSubmittedAt().atZoneSameInstant(CalendarTime.BERLIN).format(stamp),
                AttendanceRules.deadline(a.getActivity()).format(stamp), AttendanceRules.timely(a),
                a.getDecision().name(), a.getDecisionReason(), a.getAnswers(), a.getActivity().isCancelled());
    }
    private AbsenceApplication application(long id) {
        return applications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private AppUser requireAdmin(String email) {
        var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Account not found"));
        if (!user.isAdmin()) throw new AccessDeniedException("Nur Admins dürfen AaAs bearbeiten.");
        return user;
    }
}
