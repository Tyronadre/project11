package de.tyro.project11.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @PostMapping("/users/{userId}/tally")
    public String updateTally(@PathVariable long userId,
                              @RequestParam TallyChange change,
                              @RequestParam(required = false) Integer value,
                              Principal principal) {
        dashboard.changeTally(principal.getName(), userId, change, value);
        return "redirect:/welcome#user-" + userId;
    }

    @PostMapping("/users/{userId}/admin")
    public String updateAdmin(@PathVariable long userId,
                              @RequestParam boolean admin,
                              Principal principal) {
        dashboard.setAdmin(principal.getName(), userId, admin);
        return "redirect:/welcome#user-" + userId;
    }
}
