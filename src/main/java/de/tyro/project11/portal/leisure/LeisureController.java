package de.tyro.project11.portal.leisure;

import de.tyro.project11.portal.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@Controller
@RequestMapping("/amt/extra")
public class LeisureController {
    private final LeisureService leisure;
    private final PortalService portal;
    public LeisureController(LeisureService leisure, PortalService portal) { this.leisure = leisure; this.portal = portal; }
    @ModelAttribute("citizen") PortalViews.Citizen citizen(Principal principal) { return portal.citizen(principal.getName()); }
    @InitBinder("leisureForm") void bindCase(WebDataBinder binder) { binder.setAllowedFields("token", "subject", "explanation", "activityId", "enthusiasm"); }
    @InitBinder("lostPropertyForm") void bindProperty(WebDataBinder binder) { binder.setAllowedFields("kind", "subject", "description", "parentId"); }

    @GetMapping
    String index(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("procedures", LeisureKind.forms());
        model.addAttribute("cases", leisure.list(principal.getName(), page));
        return "portal/leisure/index";
    }
    @GetMapping("/formular/{slug}")
    String form(@PathVariable String slug, Principal principal, Model model) {
        var kind = formKind(slug); model.addAttribute("leisureForm", new LeisureForm());
        formModel(kind, principal, model); return "portal/leisure/form";
    }
    @PostMapping("/formular/{slug}")
    String submit(@PathVariable String slug, @Valid @ModelAttribute LeisureForm leisureForm, BindingResult errors,
                  Principal principal, Model model) {
        var kind = formKind(slug);
        if (!errors.hasErrors()) {
            var problem = leisure.formProblem(kind, leisureForm);
            if (problem != null) errors.reject("invalid", problem);
        }
        if (errors.hasErrors()) { formModel(kind, principal, model); return "portal/leisure/form"; }
        return "redirect:/amt/extra/akten/" + leisure.submit(kind, leisureForm, principal.getName());
    }
    @GetMapping("/akten/{id}")
    String show(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("record", leisure.load(id, principal.getName())); return "portal/leisure/case";
    }
    @GetMapping("/akten/{id}/urkunde")
    String certificate(@PathVariable long id, Principal principal, Model model) {
        var record = leisure.load(id, principal.getName());
        if (!record.printable()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Für diesen Vorgang liegt noch keine Urkunde vor.");
        model.addAttribute("record", record); return "portal/leisure/certificate";
    }
    @PostMapping("/akten/{id}/weiter")
    String advance(@PathVariable long id, @RequestParam int stage, Principal principal) {
        leisure.advance(id, stage, principal.getName()); return "redirect:/amt/extra/akten/" + id;
    }
    @GetMapping("/wartezimmer")
    String waitingRoom() { return "portal/leisure/waiting"; }
    @PostMapping("/wartezimmer")
    String ticket(Principal principal) { return "redirect:/amt/extra/akten/" + leisure.takeTicket(principal.getName()); }
    @PostMapping("/akten/{id}/verlassen")
    String leave(@PathVariable long id, Principal principal) {
        leisure.endWaiting(id, true, principal.getName()); return "redirect:/amt/extra/akten/" + id;
    }
    @PostMapping("/akten/{id}/schalter")
    String forward(@PathVariable long id, Principal principal) {
        leisure.endWaiting(id, false, principal.getName()); return "redirect:/amt/extra/akten/" + id;
    }
    @GetMapping("/statistik")
    String statistics(Principal principal, Model model) {
        model.addAttribute("statistics", leisure.statistics(principal.getName())); return "portal/leisure/statistics";
    }
    @GetMapping("/fundbuero")
    String propertyList(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("notices", leisure.propertyList(principal.getName(), page)); return "portal/leisure/property-list";
    }
    @GetMapping("/fundbuero/neu")
    String propertyForm(@RequestParam(required = false) Long parent, Principal principal, Model model) {
        var form = new LostPropertyForm();
        if (parent != null) {
            var previous = leisure.property(parent, principal.getName());
            if (!previous.lost() || previous.resolved() != null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Zu dieser Anzeige können keine neuen Funde gemeldet werden.");
            form.setParentId(parent); form.setKind(LostProperty.Kind.FOUND); form.setSubject(previous.subject());
        }
        model.addAttribute("lostPropertyForm", form); return "portal/leisure/property-form";
    }
    @PostMapping("/fundbuero")
    String postProperty(@Valid @ModelAttribute LostPropertyForm lostPropertyForm, BindingResult errors, Principal principal) {
        if (errors.hasErrors()) return "portal/leisure/property-form";
        return "redirect:/amt/extra/fundbuero/" + leisure.postProperty(lostPropertyForm, principal.getName());
    }
    @GetMapping("/fundbuero/{id}")
    String property(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("notice", leisure.property(id, principal.getName()));
        model.addAttribute("replies", leisure.replies(id, principal.getName(), page)); return "portal/leisure/property";
    }
    @PostMapping("/fundbuero/{id}/erledigt")
    String resolve(@PathVariable long id, Principal principal) {
        leisure.resolveProperty(id, principal.getName()); return "redirect:/amt/extra/fundbuero/" + id;
    }
    private LeisureKind formKind(String slug) {
        var kind = LeisureKind.fromSlug(slug);
        if (kind == LeisureKind.WAITING) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Wartezimmer hat einen eigenen Eingang.");
        return kind;
    }
    private void formModel(LeisureKind kind, Principal principal, Model model) {
        model.addAttribute("procedure", kind); model.addAttribute("enthusiasmChoices", LeisureService.ENTHUSIASM);
        if (kind == LeisureKind.ANTICIPATION) model.addAttribute("events", leisure.events(principal.getName()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException e, HttpServletResponse response, Model model, Principal principal) {
        response.setStatus(e.getStatusCode().value()); model.addAttribute("citizen", portal.citizen(principal.getName()));
        model.addAttribute("problem", e.getReason()); return "portal/problem";
    }
}
