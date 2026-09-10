package de.tyro.project11.auth;

import de.tyro.project11.dashboard.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class SignInController {

    private final DashboardService dashboard;

    public SignInController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String home(Principal principal) {
        return principal == null ? "redirect:/signin" : "redirect:/welcome";
    }

    @GetMapping("/signin")
    public String signIn(Principal principal) {
        return principal == null ? "signin" : "redirect:/welcome";
    }

    @GetMapping("/welcome")
    public String welcome(Principal principal, Model model) {
        model.addAttribute("dashboard", dashboard.load(principal.getName()));
        return "welcome";
    }
}
