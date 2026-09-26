package de.tyro.project11.portal;
import de.tyro.project11.attendance.AttendanceMailSettings;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@Controller
@RequestMapping("/admin/travel")
public class TravelReviewController {
    private final TravelReviewService review;
    private final AttendanceMailSettings mailSettings;
    public TravelReviewController(TravelReviewService review, AttendanceMailSettings mailSettings) {
        this.review = review; this.mailSettings = mailSettings;
    }
    @ModelAttribute("mailEnabled")
    boolean mailEnabled() { return mailSettings.enabled(); }
    @GetMapping
    String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("applications", review.list(principal.getName(), page));
        return "travel-reviews";
    }
    @GetMapping("/{id}")
    String file(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("review", review.file(id, principal.getName()));
        return "travel-review";
    }
    @PostMapping("/{id}")
    String decide(@PathVariable long id, @RequestParam TravelApplication.Decision decision,
                  @RequestParam(defaultValue = "") String reason, Principal principal, RedirectAttributes flash,
                  Model model, HttpServletResponse response) {
        try {
            review.decide(id, decision, reason, principal.getName());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 400 && exception.getStatusCode().value() != 409) throw exception;
            response.setStatus(exception.getStatusCode().value());
            model.addAttribute("decisionError", exception.getReason());
            model.addAttribute("reason", reason);
            return file(id, principal, model);
        }
        flash.addFlashAttribute("decisionSaved", true);
        return "redirect:/admin/travel/" + id;
    }
}
