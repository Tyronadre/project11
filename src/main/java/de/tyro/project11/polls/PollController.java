package de.tyro.project11.polls;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.Set;

@Controller
public class PollController {
    private final PollService polls;
    public PollController(PollService polls) { this.polls = polls; }
    @ExceptionHandler(org.springframework.beans.InvalidPropertyException.class)
    org.springframework.http.ResponseEntity<Void> invalidIndex() {
        return org.springframework.http.ResponseEntity.badRequest().build();
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException exception, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("problem", exception.getReason() == null ? "Diese Abstimmung wurde nicht gefunden." : exception.getReason());
        return "polls/problem";
    }
    @InitBinder("pollForm")
    void binder(WebDataBinder binder) {
        binder.setAllowedFields("title", "location", "description", "durationMinutes", "slots[*]");
        binder.setAutoGrowCollectionLimit(6);
    }
    @GetMapping("/polls")
    String list(Principal principal, Model model) { model.addAttribute("polls", polls.list(principal.getName())); return "polls/list"; }
    @GetMapping("/polls/new")
    String create(Model model) { model.addAttribute("pollForm", new PollForm()); return "polls/new"; }
    @PostMapping("/polls")
    String create(@Valid @ModelAttribute PollForm pollForm, BindingResult errors, Principal principal, Model model) {
        if (errors.hasErrors()) return "polls/new";
        try { return "redirect:/polls/" + polls.create(pollForm, principal.getName()); }
        catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 400) throw exception;
            model.addAttribute("problem", exception.getReason()); return "polls/new";
        }
    }
    @GetMapping("/polls/{id}")
    String show(@PathVariable long id, Principal principal, Model model) { model.addAttribute("poll", polls.load(id, principal.getName())); return "polls/show"; }
    @PostMapping("/polls/{id}/vote")
    String vote(@PathVariable long id, @RequestParam(required = false) Set<Integer> slots, Principal principal) {
        polls.vote(id, slots == null ? Set.of() : slots, principal.getName()); return "redirect:/polls/" + id + "?saved";
    }
    @PostMapping("/polls/{id}/finish")
    String finish(@PathVariable long id, @RequestParam int slot, Principal principal) {
        return "redirect:/events/" + polls.finish(id, slot, principal.getName());
    }
}
