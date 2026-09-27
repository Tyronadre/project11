package de.tyro.project11.polls;

import de.tyro.project11.calendar.*;
import de.tyro.project11.registration.*;
import jakarta.validation.Validator;
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
public class PollService {
    private final PollRepository polls;
    private final UserRepository users;
    private final EventService events;
    private final Validator validator;
    private final Clock clock;
    public PollService(PollRepository polls, UserRepository users, EventService events, Validator validator, Clock clock) {
        this.polls = polls; this.users = users; this.events = events; this.validator = validator; this.clock = clock;
    }
    public record Slot(int index, String label, int votes, boolean selected, boolean leading, boolean expired, boolean chosen) {}
    public record View(long id, String title, String location, String description, String owner,
                       int duration, int responses, int members, boolean answered, boolean editor, boolean votingOpen, int unavailable, Long eventId, List<Slot> slots) {}
    @Transactional(readOnly = true)
    public List<View> list(String email) {
        var viewer = user(email);
        int members = (int) users.count();
        return polls.findAllByOrderByIdDesc().stream().map(p -> view(p, viewer, members)).toList();
    }
    @Transactional(readOnly = true)
    public View load(long id, String email) { return view(find(id), user(email), (int) users.count()); }
    @Transactional
    public long create(PollForm form, String email) {
        if (!validator.validate(form).isEmpty() || form.getSlots() == null) throw invalid("Bitte die Angaben prüfen.");
        List<LocalDateTime> slots = new ArrayList<>();
        try {
            for (String value : form.getSlots()) {
                if (value == null || value.isBlank()) continue;
                var date = LocalDateTime.parse(value);
                if (date.getYear() < 1 || date.getYear() > 9998 || date.plusMinutes(form.getDurationMinutes()).getYear() > 9998 || !date.atZone(CalendarTime.BERLIN).toInstant().isAfter(clock.instant())
                        || CalendarTime.BERLIN.getRules().getValidOffsets(date).size() != 1
                        || CalendarTime.BERLIN.getRules().getValidOffsets(date.plusMinutes(form.getDurationMinutes())).size() != 1
                        || Duration.between(date.atZone(CalendarTime.BERLIN), date.plusMinutes(form.getDurationMinutes()).atZone(CalendarTime.BERLIN)).toMinutes() != form.getDurationMinutes())
                    throw invalid("Bitte zukünftige, eindeutige Uhrzeiten wählen. Der Zeitraum darf keinen Zeitwechsel überschreiten.");
                slots.add(date);
            }
        } catch (java.time.format.DateTimeParseException exception) { throw invalid("Bitte gültige Termine eingeben."); }
        if (slots.size() < 2 || new HashSet<>(slots).size() != slots.size())
            throw invalid("Bitte mindestens zwei unterschiedliche Termine vorschlagen.");
        return polls.save(new DatePoll(form, slots, user(email))).getId();
    }
    @Transactional
    public void vote(long id, Set<Integer> selected, String email) {
        var member = user(email);
        var poll = locked(id);
        if (poll.getEventId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Abstimmung ist abgeschlossen.");
        if (poll.getSlots().stream().noneMatch(this::future))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alle vorgeschlagenen Termine sind bereits vergangen.");
        if (selected.stream().anyMatch(i -> i == null || i < 0 || i >= poll.getSlots().size()))
            throw invalid("Ungültiger Terminvorschlag.");
        poll.getBallots().put(member.getId(), selected.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
    }
    @Transactional
    public long finish(long id, int slot, String email) {
        var member = user(email);
        var poll = locked(id);
        if (!member.isAdmin() && !poll.getOwner().getId().equals(member.getId())) throw new AccessDeniedException("Nur Ersteller und Admins können den Termin festlegen.");
        if (poll.getEventId() != null) return poll.getEventId();
        if (slot < 0 || slot >= poll.getSlots().size()) throw invalid("Ungültiger Terminvorschlag.");
        var start = poll.getSlots().get(slot);
        if (!start.atZone(CalendarTime.BERLIN).toInstant().isAfter(clock.instant())) throw invalid("Dieser Termin liegt bereits in der Vergangenheit.");
        var end = start.plusMinutes(poll.getDurationMinutes());
        var form = new EventForm();
        form.setName(poll.getTitle()); form.setLocation(poll.getLocation()); form.setDescription(poll.getDescription());
        form.setDate(start.toLocalDate()); form.setTime(start.toLocalTime());
        form.setEndDate(end.toLocalDate()); form.setEndTime(end.toLocalTime());
        long event = events.create(form, poll.getOwner().getEmail());
        poll.finish(event, slot);
        return event;
    }
    private View view(DatePoll poll, AppUser viewer, int members) {
        var own = choices(poll.getBallots().get(viewer.getId()));
        int[] counts = new int[poll.getSlots().size()];
        poll.getBallots().values().forEach(ballot -> choices(ballot).forEach(i -> counts[i]++));
        int max = Arrays.stream(counts).max().orElse(0);
        List<Slot> slots = new ArrayList<>();
        var format = DateTimeFormatter.ofPattern("EEE, dd.MM.uuuu · HH:mm", Locale.GERMAN);
        for (int i = 0; i < counts.length; i++)
            slots.add(new Slot(i, poll.getSlots().get(i).format(format), counts[i], own.contains(i), max > 0 && counts[i] == max, !future(poll.getSlots().get(i)), Objects.equals(poll.getChosenSlot(), i)));
        return new View(poll.getId(), poll.getTitle(), poll.getLocation(), poll.getDescription(), poll.getOwner().getDisplayName(),
                poll.getDurationMinutes(), poll.getBallots().size(), members, poll.getBallots().containsKey(viewer.getId()),
                viewer.isAdmin() || viewer.getId().equals(poll.getOwner().getId()),
                poll.getEventId() == null && poll.getSlots().stream().anyMatch(this::future),
                (int) poll.getBallots().values().stream().filter(String::isBlank).count(), poll.getEventId(), slots);
    }
    private boolean future(LocalDateTime slot) { return slot.atZone(CalendarTime.BERLIN).toInstant().isAfter(clock.instant()); }
    private Set<Integer> choices(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(Integer::valueOf).collect(Collectors.toSet());
    }
    private AppUser user(String email) { return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden.")); }
    private DatePoll find(long id) { return polls.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private DatePoll locked(long id) { return polls.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
