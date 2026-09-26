package de.tyro.project11.profile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@Controller
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles = profiles; }
    @InitBinder("profileForm")
    void profileBinder(WebDataBinder binder) { binder.setAllowedFields("color", "birthday", "paypal", "iban", "answers[*]"); }
    @InitBinder("blogForm")
    void blogBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(40);
        binder.setAllowedFields("title", "blocks[*].text", "blocks[*].font", "blocks[*].color", "blocks[*].kind", "blocks[*].bold", "blocks[*].italic", "blocks[*].inlineContent");
    }
    @ExceptionHandler(org.springframework.beans.InvalidPropertyException.class)
    org.springframework.http.ResponseEntity<Void> invalidCollectionIndex() {
        return org.springframework.http.ResponseEntity.badRequest().build();
    }
    @GetMapping("/users/me")
    String me(Principal principal) { return "redirect:/users/" + profiles.myId(principal.getName()); }
    @GetMapping("/users/{id}")
    String show(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("profile", profiles.load(id, principal.getName()));
        return "profile/show";
    }
    @GetMapping("/users/{id}/edit")
    String edit(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("profileForm", profiles.edit(id, principal.getName()));
        return editModel(id, principal, model);
    }
    @PostMapping("/users/{id}/profile")
    String save(@PathVariable long id, Principal principal, @Valid @ModelAttribute ProfileForm profileForm,
                BindingResult errors, Model model, RedirectAttributes flash) {
        profiles.requireOwner(id, principal.getName());
        if (errors.hasErrors()) return editModel(id, principal, model);
        profiles.save(id, principal.getName(), profileForm);
        flash.addFlashAttribute("saved", "Dein Profil wurde gespeichert.");
        return "redirect:/users/" + id;
    }
    // An explicit CSRF-protected action is required; regular profile HTML contains no payment values.
    @PostMapping("/users/{id}/payments")
    String payments(@PathVariable long id, Principal principal, Model model, HttpServletResponse response,
                    @RequestParam(defaultValue = "false") boolean fragment) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("payments", profiles.payments(id, principal.getName()));
        model.addAttribute("userId", id);
        return fragment ? "profile/payments :: details" : "profile/payments";
    }
    @GetMapping("/users/{id}/blog/new")
    String newBlog(@PathVariable long id, Principal principal, Model model) {
        profiles.requireOwner(id, principal.getName());
        model.addAttribute("blogForm", new BlogForm());
        return blogModel(id, null, model);
    }
    @GetMapping("/users/{id}/blog/{entryId}/edit")
    String editBlog(@PathVariable long id, @PathVariable long entryId, Principal principal, Model model) {
        model.addAttribute("blogForm", profiles.editBlog(id, entryId, principal.getName()));
        return blogModel(id, entryId, model);
    }
    @PostMapping({"/users/{id}/blog", "/users/{id}/blog/{entryId}"})
    String saveBlog(@PathVariable long id, @PathVariable(required = false) Long entryId, Principal principal,
                    @Valid @ModelAttribute BlogForm blogForm, BindingResult errors, Model model, RedirectAttributes flash) {
        profiles.requireOwner(id, principal.getName());
        if (entryId != null) profiles.editBlog(id, entryId, principal.getName());
        if (errors.hasErrors()) return blogModel(id, entryId, model);
        long savedId = profiles.saveBlog(id, entryId, principal.getName(), blogForm);
        flash.addFlashAttribute("saved", "Dein Blogbeitrag wurde gespeichert.");
        return "redirect:/users/" + id + "#blog-" + savedId;
    }
    @PostMapping("/users/{id}/blog/{entryId}/delete")
    String deleteBlog(@PathVariable long id, @PathVariable long entryId, Principal principal, RedirectAttributes flash) {
        profiles.deleteBlog(id, entryId, principal.getName());
        flash.addFlashAttribute("saved", "Dein Blogbeitrag wurde gelöscht.");
        return "redirect:/users/" + id;
    }
    private String editModel(long id, Principal principal, Model model) {
        model.addAttribute("profileName", profiles.requireOwner(id, principal.getName()).getDisplayName());
        model.addAttribute("userId", id); model.addAttribute("questions", ProfileQuestions.ALL);
        return "profile/edit";
    }
    private String blogModel(long id, Long entryId, Model model) {
        model.addAttribute("userId", id); model.addAttribute("entryId", entryId);
        model.addAttribute("saveUrl", "/users/" + id + "/blog" + (entryId == null ? "" : "/" + entryId));
        return "profile/blog-edit";
    }
}
