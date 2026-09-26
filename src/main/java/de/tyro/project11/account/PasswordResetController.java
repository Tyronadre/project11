package de.tyro.project11.account;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {

    private final PasswordResetService resets;

    public PasswordResetController(PasswordResetService resets) {
        this.resets = resets;
    }

    @InitBinder("forgotPasswordForm")
    void forgotBinder(WebDataBinder binder) {
        binder.setAllowedFields("email");
    }

    @InitBinder("resetPasswordForm")
    void resetBinder(WebDataBinder binder) {
        binder.setAllowedFields("token", "newPassword", "confirmPassword");
    }

    @GetMapping("/forgot-password")
    public String forgot(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@Valid @ModelAttribute ForgotPasswordForm forgotPasswordForm,
                               BindingResult errors) {
        if (errors.hasErrors()) {
            return "forgot-password";
        }
        resets.requestReset(forgotPasswordForm.getEmail());
        return "redirect:/forgot-password/sent";
    }

    @GetMapping("/forgot-password/sent")
    public String sent() {
        return "forgot-password-sent";
    }

    @GetMapping("/reset-password")
    public String reset(@RequestParam(required = false) String token,
                        Model model, HttpServletResponse response) {
        protectToken(response);
        boolean valid = resets.isUsable(token);
        model.addAttribute("invalidToken", !valid);
        if (valid) {
            var form = new ResetPasswordForm();
            form.setToken(token);
            model.addAttribute("resetPasswordForm", form);
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@Valid @ModelAttribute ResetPasswordForm resetPasswordForm,
                        BindingResult errors, Model model, HttpServletResponse response) {
        protectToken(response);
        boolean validToken = resets.isUsable(resetPasswordForm.getToken());
        if (!validToken) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }
        model.addAttribute("invalidToken", false);
        if (errors.hasErrors()) {
            return "reset-password";
        }
        if (!resets.resetPassword(resetPasswordForm.getToken(), resetPasswordForm.getNewPassword())) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }
        return "redirect:/signin?passwordReset";
    }

    private void protectToken(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
