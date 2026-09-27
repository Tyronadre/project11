package de.tyro.project11.dashboard;

import de.tyro.project11.attendance.AttendanceService;
import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.costs.CostService;
import de.tyro.project11.costs.Money;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OpenItemsService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm 'Uhr'", Locale.GERMAN)
            .withZone(CalendarTime.BERLIN);
    private final CostService costs;
    private final TravelService travel;
    private final AbsenceApplicationRepository absences;
    private final TravelApplicationRepository applications;
    private final DecisionReceiptRepository receipts;
    private final UserRepository users;
    private final Clock clock;
    private final de.tyro.project11.attendance.AttendanceRepository attendance;

    public OpenItemsService(CostService costs, TravelService travel, AbsenceApplicationRepository absences,
                            TravelApplicationRepository applications, DecisionReceiptRepository receipts,
                            UserRepository users, Clock clock, de.tyro.project11.attendance.AttendanceRepository attendance) {
        this.costs = costs; this.travel = travel; this.absences = absences; this.applications = applications;
        this.receipts = receipts; this.users = users; this.clock = clock;
        this.attendance = attendance;
    }
    public record Item(String kind, String title, String detail, String meta, String badge, String tone,
                       String url, String action, Instant time, DecisionReceipt.Source source, Long applicationId) {}
    public record View(List<Item> costs, List<Item> deadlines, List<Item> decisions, String payable, String receivable) {
        public int count() { return costs.size() + deadlines.size() + decisions.size(); }
        public long urgentCount() { return deadlines.stream().filter(i -> !i.tone().equals("neutral")).count(); }
    }

    public record AttendanceReview(long id, String title) {}
    public record Reviews(long absences, long travel, List<AttendanceReview> attendance) {
        public boolean isEmpty() { return absences == 0 && travel == 0 && attendance.isEmpty(); }
    }

    @Transactional(readOnly = true)
    public Reviews pendingReviews(String email) {
        if (!user(email).isAdmin()) return new Reviews(0, 0, List.of());
        var confirmations = attendance.findAllByOrderBySavedAtDesc().stream()
                .filter(sheet -> !sheet.isConfirmed() && !sheet.getActivity().isCancelled())
                .filter(sheet -> !sheet.getActivity().getEndsAt().toInstant().isAfter(clock.instant()))
                .map(sheet -> new AttendanceReview(sheet.getActivity().getId(), sheet.getActivity().getTitle())).toList();
        return new Reviews(absences.countPending(), applications.countPending(), confirmations);
    }

    @Transactional(readOnly = true)
    public View load(String email, AttendanceService.Pending pending) {
        long userId = user(email).getId();
        var costItems = new ArrayList<Item>();
        long payable = 0, receivable = 0;
        for (var event : costs.openEvents(email)) {
            payable = Math.addExact(payable, event.myDueCents());
            receivable = Math.addExact(receivable, event.myReceivableCents());
            for (var request : event.requests()) {
                String url = "/events/" + event.id() + "/costs#cost-" + request.id();
                if (request.editable()) {
                    long amount = request.shares().stream().filter(s -> s.paymentRequired() && !s.paid()).mapToLong(CostService.Share::cents).sum();
                    if (amount > 0 || !request.allocated()) costItems.add(new Item("Erstattung", request.description(), event.title(),
                            request.allocated() ? "An dich zu erstatten · ohne deinen eigenen Anteil" : "Aufteilung wartet auf bestätigte Teilnehmende",
                            request.allocated() ? Money.display(amount) : "Aufteilung offen", "neutral", url, "Anfrage ansehen", null, null, null));
                } else {
                    for (var share : request.shares()) if (share.mine() && share.paymentRequired() && !share.paid()) {
                        costItems.add(new Item("Offene Zahlung", request.description(), event.title(), "An " + request.creator(),
                                share.amount(), "neutral", url, "Zahlungsdetails", null, null, null));
                    }
                }
            }
        }
        var deadlines = new ArrayList<Item>();
        for (var reminder : pending.reminders()) {
            deadlines.add(new Item("AaA", reminder.title(), "1 Strich vorgemerkt", "Frist: vor " + STAMP.format(reminder.dueAt()),
                    reminder.expired() ? "Frist abgelaufen" : urgencyLabel(reminder.dueAt()), tone(reminder.dueAt()),
                    reminder.expired() ? "/events/" + reminder.activityId() : "/amt/aaa?activity=" + reminder.activityId(),
                    reminder.expired() ? "Event ansehen" : "AaA einreichen", reminder.dueAt(), null, null));
        }
        for (var report : travel.reportReminders(email)) {
            deadlines.add(new Item("Reisebericht", report.title(), report.period()
                    + (report.overdue() ? " · Eine verspätete Einreichung hebt bereits vergebene Striche nicht auf." : ""),
                    "Frist: bis " + report.deadline() + (report.canFile() ? "" : " · Einreichbar ab " + report.availableOn()),
                    report.overdue() ? "Überfällig" : urgencyLabel(report.dueAt()), tone(report.dueAt()),
                    report.canFile() ? "/amt/eer?holiday=" + report.holidayId() : "/calendar",
                    report.canFile() ? "Bericht einreichen" : "Zum Kalender", report.dueAt(), null, null));
        }
        deadlines.sort(Comparator.comparing(Item::time).thenComparing(Item::kind).thenComparing(Item::title));
        var read = receipts.findByUserId(userId).stream()
                .map(r -> r.getSource() + ":" + r.getApplicationId()).collect(Collectors.toSet());
        var decisions = new ArrayList<Item>();
        for (var application : absences.findByApplicantIdAndDecidedAtIsNotNullOrderByDecidedAtDescIdDesc(userId)) {
            if (read.contains("ABSENCE:" + application.getId())) continue;
            boolean accepted = application.getDecision() == AbsenceApplication.Decision.ACCEPTED;
            decisions.add(decision("AaA", application.getActivityTitle(), application.getDecisionReason(), accepted,
                    application.getDecidedAt(), DecisionReceipt.Source.ABSENCE, application.getId(), "/amt/antraege/"));
        }
        for (var application : applications.findByApplicantIdAndDecidedAtIsNotNullOrderByDecidedAtDescIdDesc(userId)) {
            if (read.contains("TRAVEL:" + application.getId())) continue;
            boolean accepted = application.getDecision() == TravelApplication.Decision.ACCEPTED;
            decisions.add(decision(application.getKind() == TravelKind.LEAVE ? "AaB" : "Reisebericht", application.getSubject(),
                    application.getDecisionReason(), accepted, application.getDecidedAt(), DecisionReceipt.Source.TRAVEL,
                    application.getId(), "/amt/reisen/"));
        }
        decisions.sort(Comparator.comparing(Item::time).reversed().thenComparing(Item::kind).thenComparing(Item::applicationId));
        return new View(List.copyOf(costItems), List.copyOf(deadlines), List.copyOf(decisions), Money.display(payable), Money.display(receivable));
    }

    @Transactional
    public void markRead(String email, DecisionReceipt.Source source, long applicationId) {
        var viewer = user(email);
        // Serialize duplicate clicks, and derive ownership from the signed-in account only.
        users.findLockedById(viewer.getId()).orElseThrow();
        boolean ownedDecision = switch (source) {
            case ABSENCE -> absences.findById(applicationId).filter(a -> a.getApplicant().getId().equals(viewer.getId())
                    && a.getDecidedAt() != null).isPresent();
            case TRAVEL -> applications.findById(applicationId).filter(a -> a.getApplicant().getId().equals(viewer.getId())
                    && a.getDecidedAt() != null).isPresent();
        };
        if (!ownedDecision) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entscheidung nicht gefunden.");
        if (!receipts.existsByUserIdAndSourceAndApplicationId(viewer.getId(), source, applicationId))
            receipts.save(new DecisionReceipt(viewer.getId(), source, applicationId, clock.instant()));
    }
    private Item decision(String kind, String title, String reason, boolean accepted, OffsetDateTime date,
                          DecisionReceipt.Source source, long id, String path) {
        return new Item(kind, title, reason.isBlank() ? "Die Entscheidung liegt in deiner Antragsakte vor." : reason,
                "Entschieden am " + STAMP.format(date), accepted ? "Angenommen" : "Abgelehnt", accepted ? "success" : "danger",
                path + id, "Entscheidung ansehen", date.toInstant(), source, id);
    }
    private String tone(Instant deadline) {
        if (!clock.instant().isBefore(deadline)) return "danger";
        return deadline.isBefore(clock.instant().atZone(CalendarTime.BERLIN).plusDays(3).toInstant()) ? "warning" : "neutral";
    }
    private String urgencyLabel(Instant deadline) { return tone(deadline).equals("warning") ? "Bald fällig" : "Frist offen"; }
    private AppUser user(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
    }
}
