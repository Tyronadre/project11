package de.tyro.project11.attendance;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
@Controller
public class AfeaController {
    private final AfeaService afea;
    public AfeaController(AfeaService afea) { this.afea = afea; }
    @PostMapping("/events/{id}/afea")
    String submit(@PathVariable long id, @RequestParam int evidence, Principal principal, RedirectAttributes flash) {
        afea.submit(id, evidence, principal.getName());
        flash.addFlashAttribute("afeaMessage", "Dein AFeA wurde vermerkt. Die Anwesenheitsliste wird weiterhin separat erfasst und bestätigt.");
        return "redirect:/events/" + id + "#afea";
    }
    @PostMapping("/events/{id}/afea/withdraw")
    String withdraw(@PathVariable long id, Principal principal, RedirectAttributes flash) {
        afea.withdraw(id, principal.getName()); flash.addFlashAttribute("afeaMessage", "AFeA zurückgezogen. Eine bereits gespeicherte Anwesenheitsliste bleibt unverändert.");
        return "redirect:/events/" + id + "#afea";
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException e, HttpServletResponse response, Model model) {
        response.setStatus(e.getStatusCode().value()); model.addAttribute("problem", e.getReason()); return "event-problem";
    }
}
