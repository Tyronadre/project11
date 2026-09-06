package de.tyro.project11.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class SignInController {

    @GetMapping("/")
    public String home(Principal principal) {
        return principal == null ? "redirect:/signin" : "redirect:/welcome";
    }

    @GetMapping("/signin")
    public String signIn(Principal principal) {
        return principal == null ? "signin" : "redirect:/welcome";
    }

    @GetMapping("/welcome")
    public String welcome() {
        return "welcome";
    }
}
