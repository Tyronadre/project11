package de.tyro.project11.calendar;

import jakarta.validation.Valid;
import de.tyro.project11.costs.CostService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

@Controller
public class EventController {
    private final EventService events;
    private final Clock clock;

    private final EventDetailsService details;
    private final CostService costs;
    private final de.tyro.project11.rsvp.RsvpService rsvp;
    private final de.tyro.project11.attendance.AttendanceMailSettings mail;

    public EventController(EventService events, Clock clock, EventDetailsService details, CostService costs, de.tyro.project11.attendance.AttendanceMailSettings mail, de.tyro.project11.rsvp.RsvpService rsvp) {
        this.events = events; this.clock = clock; this.details = details; this.costs = costs; this.mail = mail; this.rsvp = rsvp;
    }

    @GetMapping("/events/{id}")
    public String details(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("event", details.load(id, principal.getName()));
        model.addAttribute("rsvp", rsvp.load(id, principal.getName()));
        model.addAttribute("eventCosts", costs.event(id, principal.getName()));
        return "event-details";
    }

    @InitBinder("eventForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("name", "description", "location", "revision", "date", "endDate", "time", "endTime", "costAmount", "costSelectedOnly", "costSelectedUserIds"); }

    @ModelAttribute("costMembers")
    public java.util.List<CostService.Member> costMembers() { return costs.selectionChoices(); }

    @ModelAttribute("eventMailEnabled")
    public boolean mailEnabled() { return mail.enabled(); }

    @GetMapping("/events/{id}/edit")
    public String edit(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("eventForm", events.edit(id, principal.getName())); model.addAttribute("editingId", id);
        return "event-form";
    }
    @PostMapping("/events/{id}/edit")
    public String update(@PathVariable long id, @Valid @ModelAttribute("eventForm") EventForm form, BindingResult errors,
                         Principal principal, Model model, RedirectAttributes redirect) {
        events.edit(id, principal.getName());
        events.validate(form, errors);
        model.addAttribute("editingId", id);
        if (errors.hasErrors()) return "event-form";
        boolean changed = events.update(id, form, principal.getName());
        redirect.addFlashAttribute("eventMessage", changed ? notification("Änderungen gespeichert.") : "Keine Änderungen vorhanden.");
        return "redirect:/events/" + id;
    }
    @PostMapping("/events/{id}/cancel")
    public String cancel(@PathVariable long id, @RequestParam long revision, @RequestParam(defaultValue = "") String reason,
                         Principal principal, RedirectAttributes redirect) {
        boolean changed = events.cancel(id, revision, reason, principal.getName());
        redirect.addFlashAttribute("eventMessage", changed ? notification("Event abgesagt.") : "Das Event ist bereits abgesagt.");
        return "redirect:/events/" + id;
    }
    private String notification(String message) {
        return message + (mail.enabled() ? " Die Benachrichtigungen an die Gruppe wurden zum Versand vorgemerkt."
                : " Die Benachrichtigungen sind gespeichert, aber der E-Mail-Versand ist deaktiviert. Bitte SMTP konfigurieren und aktivieren.");
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public String problem(org.springframework.web.server.ResponseStatusException exception, jakarta.servlet.http.HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value()); model.addAttribute("problem", exception.getReason()); return "event-problem";
    }

    @GetMapping("/events/new")
    public String form(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        var form = new EventForm();
        form.setDate(date == null ? LocalDate.now(clock.withZone(CalendarTime.BERLIN)) : date);
        model.addAttribute("eventForm", form);
        return "event-form";
    }

    @PostMapping("/events")
    public String create(@Valid @ModelAttribute("eventForm") EventForm form, BindingResult errors,
                         Principal principal, RedirectAttributes redirect) {
        events.validate(form, errors);
        if (errors.hasErrors()) return "event-form";
        long id = events.create(form, principal.getName());
        redirect.addFlashAttribute("createdEvent", form.getName());
        return "redirect:/events/" + id;
    }
}
