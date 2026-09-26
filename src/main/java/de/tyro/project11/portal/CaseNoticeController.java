package de.tyro.project11.portal;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@Controller
public class CaseNoticeController {
    private final CaseFileService files;
    public CaseNoticeController(CaseFileService files) { this.files = files; }
    @GetMapping("/amt/antraege/{id}/bescheid")
    String absence(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("dossier", files.requireNotice(files.absence(id, principal.getName())));
        return "portal/notice";
    }
    @GetMapping("/amt/reisen/{id}/bescheid")
    String travel(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("dossier", files.requireNotice(files.travel(id, principal.getName())));
        return "portal/notice";
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("problem", exception.getReason());
        return "portal/notice-problem";
    }
}
