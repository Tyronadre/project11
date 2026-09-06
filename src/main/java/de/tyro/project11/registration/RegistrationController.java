package de.tyro.project11.registration;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {

    private final RegistrationService registrations;

    public RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @InitBinder("registrationForm")
    void configureFormBinding(WebDataBinder binder) {
        binder.setAllowedFields("displayName", "email", "password", "confirmPassword");
    }

    @GetMapping("/register")
    public String registration(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "register";
        }
        try {
            registrations.register(form);
        } catch (EmailAlreadyRegisteredException exception) {
            errors.rejectValue("email", "duplicate", exception.getMessage());
            return "register";
        }
        // Redirect-after-POST prevents a refresh from submitting the form again.
        redirect.addFlashAttribute("registered", true);
        return "redirect:/register/success";
    }

    @GetMapping("/register/success")
    public String success(Model model) {
        return Boolean.TRUE.equals(model.getAttribute("registered"))
                ? "registration-success" : "redirect:/register";
    }
}
