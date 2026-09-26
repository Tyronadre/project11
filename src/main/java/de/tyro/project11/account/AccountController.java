package de.tyro.project11.account;

import de.tyro.project11.registration.EmailAlreadyRegisteredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
public class AccountController {

    private final AccountService accounts;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @InitBinder("emailChangeForm")
    void emailBinder(WebDataBinder binder) {
        binder.setAllowedFields("email", "currentPassword");
    }

    @InitBinder("accountPasswordForm")
    void passwordBinder(WebDataBinder binder) {
        binder.setAllowedFields("currentPassword", "newPassword", "confirmPassword");
    }

    @GetMapping("/account")
    public String account(Principal principal, Model model, HttpServletResponse response) {
        noStore(response);
        return accountModel(principal, model);
    }

    @PostMapping("/account/email")
    public String changeEmail(Principal principal,
                              @Valid @ModelAttribute EmailChangeForm emailChangeForm,
                              BindingResult errors, Model model,
                              HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        if (errors.hasErrors()) {
            return accountModel(principal, model);
        }
        try {
            var result = accounts.changeEmail(principal.getName(), emailChangeForm);
            if (result == AccountService.ChangeResult.WRONG_PASSWORD) {
                errors.rejectValue("currentPassword", "wrong", "Das aktuelle Passwort ist nicht korrekt.");
                return accountModel(principal, model);
            }
            if (result == AccountService.ChangeResult.SAME_EMAIL) {
                errors.rejectValue("email", "same", "Das ist bereits deine aktuelle E-Mail-Adresse.");
                return accountModel(principal, model);
            }
        } catch (EmailAlreadyRegisteredException exception) {
            errors.rejectValue("email", "duplicate", "Diese E-Mail-Adresse wird bereits verwendet.");
            return accountModel(principal, model);
        }
        signOut(request, response);
        return "redirect:/signin?emailChanged";
    }

    @PostMapping("/account/password")
    public String changePassword(Principal principal,
                                 @Valid @ModelAttribute AccountPasswordForm accountPasswordForm,
                                 BindingResult errors, Model model,
                                 HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        if (errors.hasErrors()) {
            return accountModel(principal, model);
        }
        var result = accounts.changePassword(principal.getName(), accountPasswordForm);
        if (result == AccountService.ChangeResult.WRONG_PASSWORD) {
            errors.rejectValue("currentPassword", "wrong", "Das aktuelle Passwort ist nicht korrekt.");
            return accountModel(principal, model);
        }
        if (result == AccountService.ChangeResult.SAME_PASSWORD) {
            errors.rejectValue("newPassword", "same", "Das neue Passwort muss sich vom aktuellen unterscheiden.");
            return accountModel(principal, model);
        }
        signOut(request, response);
        return "redirect:/signin?passwordChanged";
    }

    private String accountModel(Principal principal, Model model) {
        var account = accounts.load(principal.getName());
        model.addAttribute("account", account);
        if (!model.containsAttribute("emailChangeForm")) {
            var form = new EmailChangeForm();
            form.setEmail(account.email());
            model.addAttribute("emailChangeForm", form);
        }
        if (!model.containsAttribute("accountPasswordForm")) {
            model.addAttribute("accountPasswordForm", new AccountPasswordForm());
        }
        return "account";
    }

    private void signOut(HttpServletRequest request, HttpServletResponse response) {
        logout.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
}
