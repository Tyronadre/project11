package de.tyro.project11.rsvp;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@Controller
public class RsvpController {
    private final RsvpService rsvp;
    public RsvpController(RsvpService rsvp) { this.rsvp = rsvp; }
    @PostMapping("/events/{id}/rsvp")
    public String respond(@PathVariable long id, @RequestParam EventRsvp.Answer answer, @RequestParam long version,
                          @RequestParam long eventRevision, Principal principal, RedirectAttributes redirect) {
        rsvp.respond(id, answer, version, eventRevision, principal.getName());
        redirect.addFlashAttribute("rsvpMessage", "Deine Rückmeldung wurde gespeichert: " + answer.getLabel());
        return "redirect:/events/" + id + "#rsvp";
    }
    @ExceptionHandler(ResponseStatusException.class)
    public String problem(ResponseStatusException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value()); model.addAttribute("problem", exception.getReason()); return "event-problem";
    }
}
