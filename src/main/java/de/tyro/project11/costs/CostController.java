package de.tyro.project11.costs;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@Controller
public class CostController {
    private final CostService costs;
    public CostController(CostService costs) { this.costs = costs; }
    @InitBinder("costForm")
    void bind(WebDataBinder binder) { binder.setAllowedFields("description", "amount", "requestKey", "version", "selectedOnly", "selectedUserIds"); }

    @GetMapping("/costs")
    public String overview(Principal principal, Model model) {
        model.addAttribute("costs", costs.overview(principal.getName())); return "costs/overview";
    }
    @GetMapping("/costs/new")
    public String select(@RequestParam long eventId, Principal principal) {
        costs.event(eventId, principal.getName()); return "redirect:/events/" + eventId + "/costs/new";
    }
    @GetMapping("/events/{eventId}/costs")
    public String event(@PathVariable long eventId, Principal principal, Model model) {
        model.addAttribute("eventCosts", costs.event(eventId, principal.getName())); return "costs/event";
    }
    @GetMapping("/events/{eventId}/costs/new")
    public String form(@PathVariable long eventId, Principal principal, Model model) {
        model.addAttribute("costForm", new CostForm()); return formPage(eventId, null, principal, model);
    }
    @GetMapping("/events/{eventId}/costs/{costId}/edit")
    public String edit(@PathVariable long eventId, @PathVariable long costId, Principal principal, Model model) {
        model.addAttribute("costForm", costs.edit(eventId, costId, principal.getName())); return formPage(eventId, costId, principal, model);
    }
    @PostMapping("/events/{eventId}/costs")
    public String create(@PathVariable long eventId, @Valid @ModelAttribute("costForm") CostForm form, BindingResult errors,
                         Principal principal, Model model, RedirectAttributes redirect) {
        costs.validateAmount(form, errors);
        costs.validateSelection(form.isSelectedOnly(), form.getSelectedUserIds(), "selectedUserIds", errors);
        if (errors.hasErrors()) return formPage(eventId, null, principal, model);
        costs.create(eventId, form, principal.getName());
        return saved(eventId, "Kostenanfrage gespeichert.", redirect);
    }
    @PostMapping("/events/{eventId}/costs/{costId}")
    public String update(@PathVariable long eventId, @PathVariable long costId, @Valid @ModelAttribute("costForm") CostForm form,
                         BindingResult errors, Principal principal, Model model, RedirectAttributes redirect) {
        costs.edit(eventId, costId, principal.getName());
        costs.validateAmount(form, errors);
        costs.validateSelection(form.isSelectedOnly(), form.getSelectedUserIds(), "selectedUserIds", errors);
        if (errors.hasErrors()) return formPage(eventId, costId, principal, model);
        costs.update(eventId, costId, form, principal.getName());
        return saved(eventId, "Kostenanfrage aktualisiert.", redirect);
    }
    @PostMapping("/events/{eventId}/costs/{costId}/shares/{participantId}/paid")
    public String paid(@PathVariable long eventId, @PathVariable long costId, @PathVariable long participantId,
                       @RequestParam long version, @RequestParam long attendanceVersion,
                       Principal principal, RedirectAttributes redirect) {
        costs.markSharePaid(eventId, costId, participantId, version, attendanceVersion, principal.getName());
        return saved(eventId, "Zahlung als bezahlt markiert.", redirect);
    }
    @PostMapping("/events/{eventId}/costs/{costId}/shares/{participantId}/unpaid")
    public String unpaid(@PathVariable long eventId, @PathVariable long costId, @PathVariable long participantId,
                         @RequestParam long version, Principal principal, RedirectAttributes redirect) {
        costs.markShareUnpaid(eventId, costId, participantId, version, principal.getName());
        return saved(eventId, "Zahlung wieder als offen markiert.", redirect);
    }
    @PostMapping("/events/{eventId}/costs/{costId}/reopen")
    public String reopen(@PathVariable long eventId, @PathVariable long costId, @RequestParam long version,
                         Principal principal, RedirectAttributes redirect) {
        costs.reopen(eventId, costId, version, principal.getName());
        return saved(eventId, "Alle Zahlungsstände wurden zurückgesetzt. Es gilt wieder die aktuelle bestätigte Teilnehmerliste.", redirect);
    }
    private String formPage(long eventId, Long costId, Principal principal, Model model) {
        model.addAttribute("eventCosts", costs.event(eventId, principal.getName())); model.addAttribute("costId", costId);
        model.addAttribute("costMembers", costs.selectionChoices());
        return "costs/form";
    }
    private String saved(long eventId, String message, RedirectAttributes redirect) {
        redirect.addFlashAttribute("costMessage", message); return "redirect:/events/" + eventId + "/costs";
    }
    @ExceptionHandler(ResponseStatusException.class)
    public String problem(ResponseStatusException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value()); model.addAttribute("problem", exception.getReason());
        return "costs/problem";
    }
}
