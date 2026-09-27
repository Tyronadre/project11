package de.tyro.project11.calendar;

import de.tyro.project11.registration.UserRepository;
import de.tyro.project11.costs.CostForm;
import de.tyro.project11.costs.CostService;
import de.tyro.project11.costs.Money;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class EventService {
    private final ActivityRepository activities;
    private final UserRepository users;
    private final CostService costs;
    private final de.tyro.project11.attendance.AttendanceRepository attendance;
    private final de.tyro.project11.attendance.AttendancePenaltyService penalties;
    private final de.tyro.project11.attendance.AttendanceMailQueue reminders;
    private final de.tyro.project11.portal.AbsenceApplicationRepository absences;
    private final EventChangeEmailRepository emails;
    private final java.time.Clock clock;
    private final jakarta.validation.Validator validator;

    public EventService(ActivityRepository activities, UserRepository users, CostService costs,
                        de.tyro.project11.attendance.AttendanceRepository attendance,
                        de.tyro.project11.attendance.AttendancePenaltyService penalties,
                        de.tyro.project11.attendance.AttendanceMailQueue reminders,
                        de.tyro.project11.portal.AbsenceApplicationRepository absences,
                        EventChangeEmailRepository emails, java.time.Clock clock, jakarta.validation.Validator validator) {
        this.activities = activities; this.users = users; this.costs = costs;
        this.attendance = attendance; this.penalties = penalties; this.reminders = reminders;
        this.absences = absences; this.emails = emails; this.clock = clock; this.validator = validator;
    }

    public void validate(EventForm form, BindingResult errors) {
        if (!form.getCostAmount().isBlank()) costs.validateSelection(form.isCostSelectedOnly(), form.getCostSelectedUserIds(), "costSelectedUserIds", errors);
        try { Money.optionalCents(form.getCostAmount()); }
        catch (IllegalArgumentException exception) {
            if (!errors.hasFieldErrors("costAmount")) errors.rejectValue("costAmount", "money", exception.getMessage());
        }
        if (form.getDate() == null || !form.isDateInRange() || !form.isEndDateInRange()) return;
        validateTime(form.getDate(), form.getTime(), "time", errors);
        if (form.getEndTime() != null) {
            if (form.getTime() != null && form.getTime().equals(form.getEndTime()) && endDate(form).equals(form.getDate())) {
                errors.rejectValue("endTime", "sameTime", "Beginn und Ende müssen unterschiedliche Uhrzeiten haben.");
            }
            validateTime(endDate(form), form.getEndTime(), "endTime", errors);
            if (!endDate(form).atTime(form.getEndTime()).isAfter(form.getDate().atTime(form.getTime() == null ? LocalTime.MIDNIGHT : form.getTime())))
                errors.rejectValue("endTime", "period", "Das Ende muss nach dem Beginn liegen.");
        }
    }

    private void validateTime(LocalDate date, LocalTime time, String field, BindingResult errors) {
        if (time != null && CalendarTime.BERLIN.getRules().getValidOffsets(date.atTime(time)).isEmpty()) {
            errors.rejectValue(field, "clockChange", "Diese Uhrzeit gibt es an diesem Tag wegen der Zeitumstellung nicht. Bitte wähle eine andere Uhrzeit.");
        }
    }

    private LocalDate endDate(EventForm form) {
        if (form.getEndDate() != null) return form.getEndDate();
        boolean nextDay = form.getTime() == null ? LocalTime.MIDNIGHT.equals(form.getEndTime())
                : form.getEndTime() != null && form.getEndTime().isBefore(form.getTime());
        return nextDay ? form.getDate().plusDays(1) : form.getDate();
    }

    @Transactional
    public long create(EventForm form, String email) {
        requireValid(form);
        Long amount;
        try { amount = Money.optionalCents(form.getCostAmount()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
        var owner = users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Dein Konto ist nicht mehr verfügbar."));
        var start = form.getDate().atTime(form.getTime() == null ? LocalTime.MIDNIGHT : form.getTime())
                .atZone(CalendarTime.BERLIN);
        // Without an explicit end, retain the selected day's Berlin midnight boundary.
        var end = form.getEndTime() == null ? (form.getEndDate() == null ? form.getDate() : form.getEndDate()).plusDays(1).atStartOfDay(CalendarTime.BERLIN)
                : endDate(form).atTime(form.getEndTime()).atZone(CalendarTime.BERLIN);
        var timing = form.getEndTime() != null ? Activity.Timing.INTERVAL
                : form.getTime() == null ? Activity.Timing.ALL_DAY : Activity.Timing.START_TIME;
        var activity = new Activity(form.getName(), form.getLocation(), form.getDescription(), start.toOffsetDateTime(),
                end.toOffsetDateTime(), owner, timing, form.getTime() == null);
        long id = activities.saveAndFlush(activity).getId();
        if (amount != null) {
            var cost = new CostForm(); cost.setAmount(Money.input(amount));
            cost.setSelectedOnly(form.isCostSelectedOnly()); cost.setSelectedUserIds(form.getCostSelectedUserIds());
            costs.create(id, cost, email);
        }
        return id;
    }

    private void requireValid(EventForm form) {
        var errors = new org.springframework.validation.BeanPropertyBindingResult(form, "eventForm");
        validate(form, errors);
        if (!validator.validate(form).isEmpty() || errors.hasErrors()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte die Eventangaben prüfen.");
    }
    @Transactional(readOnly = true)
    public EventForm edit(long id, String email) {
        var activity = activities.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireEditor(activity, email);
        if (activity.isCancelled()) throw conflict("Ein abgesagtes Event kann nicht mehr bearbeitet werden.");
        var form = new EventForm(); form.setRevision(activity.getRevision());
        form.setName(activity.getTitle()); form.setDescription(activity.getDescription()); form.setLocation(activity.getLocation());
        var start = activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN);
        var end = activity.getEndsAt().atZoneSameInstant(CalendarTime.BERLIN);
        form.setDate(start.toLocalDate());
        form.setTime(activity.hasStartTime() ? start.toLocalTime() : null);
        form.setEndTime(activity.hasEndTime() ? end.toLocalTime() : null);
        form.setEndDate(activity.hasEndTime() ? end.toLocalDate() : end.minusNanos(1).toLocalDate());
        return form;
    }
    @Transactional
    public boolean update(long id, EventForm form, String email) {
        var activity = activities.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireEditor(activity, email);
        if (activity.isCancelled()) throw conflict("Dieses Event wurde abgesagt.");
        checkRevision(activity, form.getRevision()); requireValid(form);
        var start = resolveTime(form.getDate().atTime(form.getTime() == null ? LocalTime.MIDNIGHT : form.getTime()), activity.getStartsAt());
        var end = form.getEndTime() == null
                ? (form.getEndDate() == null ? form.getDate() : form.getEndDate()).plusDays(1).atStartOfDay(CalendarTime.BERLIN).toOffsetDateTime()
                : resolveTime(endDate(form).atTime(form.getEndTime()), activity.getEndsAt());
        var timing = form.getEndTime() != null ? Activity.Timing.INTERVAL : form.getTime() == null ? Activity.Timing.ALL_DAY : Activity.Timing.START_TIME;
        boolean scheduleChanged = !start.toInstant().equals(activity.getStartsAt().toInstant()) || !end.toInstant().equals(activity.getEndsAt().toInstant())
                || activity.hasStartTime() != (form.getTime() != null) || activity.hasEndTime() != (form.getEndTime() != null);
        if (scheduleChanged && (attendance.existsById(id) || !absences.findByActivityId(id).isEmpty()))
            throw conflict("Zu diesem Termin liegen bereits Anwesenheiten oder AaAs vor. Datum und Uhrzeit können deshalb nicht mehr geändert werden. Ort, Name und Beschreibung bleiben bearbeitbar; bei einer Neuplanung bitte das Event absagen und ein neues anlegen.");
        if (!scheduleChanged && activity.getTitle().equals(form.getName()) && activity.getDescription().equals(form.getDescription()) && activity.getLocation().equals(form.getLocation())) return false;
        String before = snapshot(activity);
        activity.revise(form.getName(), form.getDescription(), form.getLocation(), start, end, timing, form.getTime() == null);
        activities.saveAndFlush(activity);
        queue(activity, "Event geändert", "Bisher:\n" + before + "\n\nNeu:\n" + snapshot(activity));
        return true;
    }
    @Transactional
    public boolean cancel(long id, long revision, String reason, String email) {
        var activity = activities.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireEditor(activity, email);
        if (activity.isCancelled()) return false;
        checkRevision(activity, revision);
        reason = reason == null ? "" : reason.strip();
        if (reason.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Begründung darf höchstens 1000 Zeichen enthalten.");
        activity.cancel(reason, java.time.OffsetDateTime.now(clock)); activities.saveAndFlush(activity);
        reminders.sync(id, java.util.Set.of()); penalties.reconcile(id);
        queue(activity, "Event abgesagt", snapshot(activity) + "\n\nDieses Event findet nicht statt. Es entstehen keine Event-Striche und es ist kein AaA erforderlich. Bestehende Kosten bitte mit den jeweiligen Erstellern klären."
                + (reason.isBlank() ? "" : "\n\nGrund: " + reason));
        return true;
    }
    private void queue(Activity activity, String subject, String body) {
        for (var member : users.findAllByOrderByDisplayNameAsc())
            emails.save(new EventChangeEmail(activity.getId(), activity.getRevision(), member.getId(), subject,
                    body, clock.instant()));
    }
    private String snapshot(Activity a) {
        var entry = CalendarService.activityEntry(a);
        return a.getTitle() + "\n" + entry.period() + " (Europe/Berlin)\nOrt: " + (a.getLocation().isBlank() ? "Noch offen" : a.getLocation())
                + "\n" + a.getDescription();
    }
    private void requireEditor(Activity activity, String email) {
        var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
        if (!user.isAdmin() && !activity.getCreatedBy().getId().equals(user.getId())) throw new AccessDeniedException("Nur Eventersteller und Admins dürfen Events ändern.");
    }
    private java.time.OffsetDateTime resolveTime(java.time.LocalDateTime local, java.time.OffsetDateTime previous) {
        // Preserve the original instant when an unchanged wall time lies in the autumn DST overlap.
        if (local.equals(previous.atZoneSameInstant(CalendarTime.BERLIN).toLocalDateTime())) return previous;
        return local.atZone(CalendarTime.BERLIN).toOffsetDateTime();
    }
    private void checkRevision(Activity activity, long revision) {
        if (activity.getRevision() != revision) throw conflict("Das Event wurde inzwischen geändert. Bitte neu laden und prüfen.");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
