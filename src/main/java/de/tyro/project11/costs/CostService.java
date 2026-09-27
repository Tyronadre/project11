package de.tyro.project11.costs;

import de.tyro.project11.attendance.AttendanceRepository;
import de.tyro.project11.calendar.*;
import de.tyro.project11.registration.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CostService {
    private final EventCostRepository costs;
    private final ActivityRepository activities;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final Validator validator;
    private final Clock clock;
    public CostService(EventCostRepository costs, ActivityRepository activities, AttendanceRepository attendance,
                       UserRepository users, Validator validator, Clock clock) {
        this.costs = costs; this.activities = activities; this.attendance = attendance;
        this.users = users; this.validator = validator; this.clock = clock;
    }
    public record Share(long userId, String name, long cents, boolean ownShare, boolean mine,
                        boolean paymentRequired, boolean paid, String paidAt) {
        public String amount() { return Money.display(cents); }
    }
    public record Request(long id, long version, String description, long cents, long creatorId, String creator,
                          boolean editable, boolean paid, boolean partiallyPaid, boolean paymentTracking, String paidAt,
                          boolean allocated, boolean requiresPayments, boolean selectedOnly, List<Share> shares) {
        public String amount() { return Money.display(cents); }
        public String reimbursement() { return Money.display(shares.stream().filter(Share::paymentRequired).mapToLong(Share::cents).sum()); }
        public String openReimbursement() { return Money.display(shares.stream().filter(s -> s.paymentRequired() && !s.paid()).mapToLong(Share::cents).sum()); }
    }
    public record Event(long id, String title, String date, String distributionStatus, boolean ready, long attendanceVersion,
                        int participants, List<Request> requests, long totalCents, long openCents, long myShareCents,
                        long myDueCents, long myReceivableCents, boolean awaitingDistribution) {
        public String total() { return Money.display(totalCents); }
        public String open() { return Money.display(openCents); }
        public String myShare() { return Money.display(myShareCents); }
        public String myDue() { return Money.display(myDueCents); }
        public String myReceivable() { return Money.display(myReceivableCents); }
    }
    public record Choice(long id, String title, String date) {}
    public record Overview(List<Event> events, List<Choice> choices) {}
    @Transactional(readOnly = true)
    public List<Event> openEvents(String email) {
        var viewer = user(email);
        var grouped = costs.findByPaidAtIsNullOrderByActivityStartsAtDescActivityIdDescIdAsc().stream()
                .collect(Collectors.groupingBy(c -> c.getActivity().getId(), LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream().map(rows -> eventView(rows.getFirst().getActivity(), rows, viewer.getId())).toList();
    }
    private record Distribution(List<AppUser> participants, long version, String status) {
        boolean ready() { return !participants.isEmpty(); }
    }
    @Transactional(readOnly = true)
    public Overview overview(String email) {
        var viewer = user(email);
        var grouped = costs.findAllByOrderByActivityStartsAtDescActivityIdDescIdAsc().stream()
                .collect(Collectors.groupingBy(c -> c.getActivity().getId(), LinkedHashMap::new, Collectors.toList()));
        var events = grouped.values().stream().map(rows -> eventView(rows.getFirst().getActivity(), rows, viewer.getId())).toList();
        var choices = activities.findAll(Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id"))).stream()
                .map(a -> new Choice(a.getId(), a.getTitle(), date(a))).toList();
        return new Overview(events, choices);
    }
    @Transactional(readOnly = true)
    public Event event(long eventId, String email) {
        var viewer = user(email);
        return eventView(activity(eventId), costs.findByActivityIdOrderByIdAsc(eventId), viewer.getId());
    }
    @Transactional(readOnly = true)
    public CostForm edit(long eventId, long costId, String email) {
        var cost = own(eventId, costId, user(email));
        requireOpen(cost);
        var form = new CostForm(); form.setDescription(cost.getDescription()); form.setAmount(Money.input(cost.getAmountCents()));
        form.setSelectedOnly(cost.isSelectedOnly()); form.setSelectedUserIds(cost.getSelectedUserIds());
        form.setRequestKey(cost.getRequestKey()); form.setVersion(cost.getVersion()); return form;
    }
    public void validateAmount(CostForm form, BindingResult errors) {
        try { Money.cents(form.getAmount()); }
        catch (IllegalArgumentException exception) { if (!errors.hasFieldErrors("amount")) errors.rejectValue("amount", "money", exception.getMessage()); }
    }
    @Transactional
    public long create(long eventId, CostForm form, String email) {
        long amount = validatedAmount(form);
        var activity = activities.findLockedById(eventId).orElseThrow(CostService::missing);
        var creator = user(email);
        // Same lock order as event settlement: event -> user. Also serialize reused form keys across events.
        users.findLockedById(creator.getId()).orElseThrow(CostService::missing);
        var existing = costs.findByCreatorIdAndRequestKey(creator.getId(), form.getRequestKey());
        if (existing.isPresent()) {
            if (existing.get().getActivity().getId() != eventId) throw conflict("Dieses Formular wurde bereits für ein anderes Event verwendet.");
            return existing.get().getId();
        }
        validateSelection(form.isSelectedOnly(), form.getSelectedUserIds());
        var cost = new EventCost(activity, creator, form.getRequestKey(), form.getDescription(), amount, OffsetDateTime.now(clock));
        cost.selectParticipants(form.isSelectedOnly(), form.getSelectedUserIds());
        return costs.saveAndFlush(cost).getId();
    }
    @Transactional
    public void update(long eventId, long costId, CostForm form, String email) {
        long amount = validatedAmount(form);
        activities.findLockedById(eventId).orElseThrow(CostService::missing);
        var cost = own(eventId, costId, user(email));
        checkVersion(cost, form.getVersion()); requireOpen(cost);
        validateSelection(form.isSelectedOnly(), form.getSelectedUserIds());
        cost.selectParticipants(form.isSelectedOnly(), form.getSelectedUserIds());
        cost.update(form.getDescription(), amount, OffsetDateTime.now(clock));
        costs.saveAndFlush(cost);
    }
    @Transactional
    public void markSharePaid(long eventId, long costId, long participantId, long version, long attendanceVersion, String email) {
        activities.findLockedById(eventId).orElseThrow(CostService::missing);
        var cost = own(eventId, costId, user(email));
        if (cost.isSharePaid(participantId)) return; // A repeated POST is idempotent.
        checkVersion(cost, version);
        List<CostShare> shares = List.of();
        if (!cost.hasPaymentTracking()) {
            var distribution = distribution(eventId);
            if (!cost.isSelectedOnly()) {
                if (!distribution.ready()) throw conflict("Zuerst muss eine Teilnehmerliste mit mindestens einer anwesenden Person bestätigt werden.");
                if (distribution.version() != attendanceVersion) throw conflict("Die Teilnehmerliste hat sich geändert. Bitte die neue Aufteilung prüfen.");
            }
            var participants = participants(cost, distribution);
            if (participants.isEmpty()) throw conflict("Keine Personen für die Aufteilung vorhanden. Bitte die Auswahl prüfen.");
            shares = split(cost.getAmountCents(), participants);
        }
        requirePayableShare(cost, shares, participantId);
        cost.markSharePaid(shares, participantId, OffsetDateTime.now(clock));
        costs.saveAndFlush(cost);
    }
    @Transactional
    public void markShareUnpaid(long eventId, long costId, long participantId, long version, String email) {
        activities.findLockedById(eventId).orElseThrow(CostService::missing);
        var cost = own(eventId, costId, user(email));
        if (!cost.hasPaymentTracking()) return; // A repeated POST after reopening is idempotent.
        requirePayableShare(cost, List.of(), participantId);
        if (!cost.isSharePaid(participantId)) return;
        checkVersion(cost, version);
        cost.markShareUnpaid(participantId, OffsetDateTime.now(clock));
        costs.saveAndFlush(cost);
    }
    @Transactional
    public void reopen(long eventId, long costId, long version, String email) {
        activities.findLockedById(eventId).orElseThrow(CostService::missing);
        var cost = own(eventId, costId, user(email));
        if (!cost.hasPaymentTracking()) return;
        checkVersion(cost, version); cost.reopen(OffsetDateTime.now(clock)); costs.saveAndFlush(cost);
    }
    private Event eventView(Activity activity, List<EventCost> rows, long viewer) {
        var distribution = distribution(activity.getId());
        var requests = rows.stream().map(cost -> {
            var shares = cost.hasPaymentTracking() ? cost.getSettlementShares() : split(cost.getAmountCents(), participants(cost, distribution));
            var shareViews = shares.stream().map(share -> {
                boolean ownShare = share.getParticipant().getId().equals(cost.getCreator().getId());
                boolean paymentRequired = !ownShare && share.getAmountCents() > 0;
                boolean paid = paymentRequired && (cost.isPaid() || share.isPaid());
                var paidAt = share.getPaidAt() == null ? cost.getPaidAt() : share.getPaidAt();
                return new Share(share.getParticipant().getId(), share.getParticipant().getDisplayName(), share.getAmountCents(),
                        ownShare, share.getParticipant().getId() == viewer, paymentRequired, paid,
                        paid && paidAt != null ? dateTime(paidAt) : null);
            }).toList();
            boolean partiallyPaid = shareViews.stream().anyMatch(Share::paid) && !cost.isPaid();
            return new Request(cost.getId(), cost.getVersion(), cost.getDescription(), cost.getAmountCents(), cost.getCreator().getId(),
                    cost.getCreator().getDisplayName(), cost.getCreator().getId() == viewer, cost.isPaid(), partiallyPaid,
                    cost.hasPaymentTracking(), cost.getPaidAt() == null ? null : dateTime(cost.getPaidAt()),
                    !shares.isEmpty(), shareViews.stream().anyMatch(Share::paymentRequired), cost.isSelectedOnly(), shareViews);
        }).toList();
        long total = 0, open = 0, myShare = 0, myDue = 0, receivable = 0;
        for (var request : requests) {
            total = Math.addExact(total, request.cents());
            for (var share : request.shares()) {
                if (share.mine()) myShare = Math.addExact(myShare, share.cents());
                if (share.paymentRequired() && !share.paid()) {
                    open = Math.addExact(open, share.cents());
                    if (share.mine()) myDue = Math.addExact(myDue, share.cents());
                    if (request.creatorId() == viewer) receivable = Math.addExact(receivable, share.cents());
                }
            }
        }
        return new Event(activity.getId(), activity.getTitle(), date(activity), distribution.status(), distribution.ready(), distribution.version(),
                distribution.participants().size(), requests, total, open, myShare, myDue, receivable,
                requests.stream().anyMatch(r -> !r.allocated()));
    }
    public record Member(long id, String name) {}
    @Transactional(readOnly = true)
    public List<Member> selectionChoices() {
        return users.findAll(Sort.by("displayName", "id")).stream().map(u -> new Member(u.getId(), u.getDisplayName())).toList();
    }
    @Transactional(readOnly = true)
    public void validateSelection(boolean selectedOnly, Set<Long> ids) {
        if (!selectedOnly) return;
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull) || users.findAllById(ids).size() != ids.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte mindestens eine gültige Person auswählen.");
    }
    public void validateSelection(boolean selectedOnly, Set<Long> ids, String field, BindingResult errors) {
        if (errors.hasFieldErrors(field)) return;
        try { validateSelection(selectedOnly, ids); }
        catch (ResponseStatusException exception) { errors.rejectValue(field, "selection", exception.getReason()); }
    }
    private List<AppUser> participants(EventCost cost, Distribution distribution) {
        if (!cost.isSelectedOnly()) return distribution.participants();
        return users.findAllById(cost.getSelectedUserIds()).stream()
                .sorted(Comparator.comparing(AppUser::getId)).toList();
    }
    private Distribution distribution(long eventId) {
        var sheet = attendance.findById(eventId).orElse(null);
        if (sheet == null) return new Distribution(List.of(), -1, "Teilnehmerliste noch nicht erfasst");
        if (!sheet.isConfirmed()) return new Distribution(List.of(), sheet.getVersion(), "Teilnehmerliste wartet auf Bestätigung");
        var participants = sheet.getAttendees().stream().sorted(Comparator.comparing(AppUser::getId)).toList();
        return new Distribution(participants, sheet.getVersion(), participants.isEmpty() ? "Keine Teilnehmenden eingetragen" : "Aufteilung auf " + participants.size() + " Teilnehmende");
    }
    private List<CostShare> split(long cents, List<AppUser> participants) {
        if (participants.isEmpty()) return List.of();
        long base = cents / participants.size(), remainder = cents % participants.size();
        var result = new ArrayList<CostShare>();
        for (int i = 0; i < participants.size(); i++) result.add(new CostShare(participants.get(i), base + (i < remainder ? 1 : 0)));
        return result;
    }
    private void requirePayableShare(EventCost cost, List<CostShare> currentShares, long participantId) {
        var shares = cost.hasPaymentTracking() ? cost.getSettlementShares() : currentShares;
        var share = shares.stream().filter(item -> item.getParticipant().getId() == participantId).findFirst()
                .orElseThrow(() -> conflict("Diese Person hat keinen Anteil an der Kostenanfrage."));
        if (share.getParticipant().getId().equals(cost.getCreator().getId()) || share.getAmountCents() == 0)
            throw conflict("Für diesen Anteil ist keine Zahlung erforderlich.");
    }
    private long validatedAmount(CostForm form) {
        if (!validator.validate(form).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte Verwendungszweck und Betrag prüfen.");
        try { return Money.cents(form.getAmount()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
    }
    private EventCost own(long eventId, long costId, AppUser user) {
        var cost = costs.findById(costId).orElseThrow(CostService::missing);
        if (cost.getActivity().getId() != eventId) throw missing();
        if (!cost.getCreator().getId().equals(user.getId())) throw new AccessDeniedException("Nur der Ersteller darf diese Kostenanfrage ändern.");
        return cost;
    }
    private void checkVersion(EventCost cost, long version) { if (cost.getVersion() != version) throw conflict("Die Kostenanfrage wurde geändert. Bitte neu laden und prüfen."); }
    private void requireOpen(EventCost cost) { if (cost.hasPaymentTracking()) throw conflict("Vor Änderungen müssen zuerst alle Zahlungsstände zurückgesetzt werden."); }
    private Activity activity(long id) { return activities.findById(id).orElseThrow(CostService::missing); }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private String date(Activity activity) { return activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).format(DateTimeFormatter.ofPattern("dd.MM.uuuu")); }
    private String dateTime(OffsetDateTime value) { return value.atZoneSameInstant(CalendarTime.BERLIN).format(DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm")); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event oder Kostenanfrage nicht gefunden."); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
