package de.tyro.project11.portal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
@Controller
public class ComplaintController {
    private final ComplaintService complaints;
    private final PortalService portal;
    public ComplaintController(ComplaintService complaints, PortalService portal) { this.complaints = complaints; this.portal = portal; }
    @ModelAttribute("citizen") PortalViews.Citizen citizen(Principal principal) { return portal.citizen(principal.getName()); }
    @InitBinder("complaintForm") void bind(WebDataBinder binder) { binder.setAllowedFields("subject", "text", "parentId"); }
    @GetMapping("/amt/bub")
    String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("complaints", complaints.list(principal.getName(), page)); return "portal/complaints";
    }
    @GetMapping("/amt/bub/neu")
    String form(@RequestParam(required = false) Long parent, Principal principal, Model model) {
        var form = new ComplaintForm();
        if (parent != null) {
            var previous = complaints.load(parent, principal.getName());
            if (!previous.owner()) throw new org.springframework.security.access.AccessDeniedException("Bitte eine eigene Beschwerde auswählen.");
            form.setParentId(parent); form.setSubject("Bearbeitungsdauer von " + previous.reference());
        }
        model.addAttribute("complaintForm", form); return "portal/complaint-form";
    }
    @PostMapping("/amt/bub")
    String submit(@Valid @ModelAttribute ComplaintForm complaintForm, BindingResult errors, Principal principal) {
        if (errors.hasErrors()) return "portal/complaint-form";
        return "redirect:/amt/bub/" + complaints.submit(complaintForm, principal.getName());
    }
    @GetMapping("/amt/bub/{id}")
    String show(@PathVariable long id, Principal principal, Model model) { model.addAttribute("complaint", complaints.load(id, principal.getName())); return "portal/complaint"; }
    @PostMapping("/amt/bub/{id}/close")
    String close(@PathVariable long id, Principal principal) { complaints.close(id, principal.getName()); return "redirect:/amt/bub/" + id; }
    @PostMapping("/amt/bub/{id}/rate")
    String rate(@PathVariable long id, @RequestParam int rating, Principal principal) { complaints.rate(id, rating, principal.getName()); return "redirect:/amt/bub/" + id; }
    @ExceptionHandler(ResponseStatusException.class)
    String error(ResponseStatusException e, HttpServletResponse response, Model model, Principal principal) { model.addAttribute("citizen", portal.citizen(principal.getName())); response.setStatus(e.getStatusCode().value()); model.addAttribute("problem", e.getReason()); return "portal/problem"; }
}
