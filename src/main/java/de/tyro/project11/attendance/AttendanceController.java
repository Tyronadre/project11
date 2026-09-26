package de.tyro.project11.attendance;

import org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.util.Set;

@Controller
public class AttendanceController {
    private final AttendanceService attendance;
    private final AttendanceMailSettings mailSettings;
    public AttendanceController(AttendanceService attendance, AttendanceMailSettings mailSettings) {
        this.attendance = attendance; this.mailSettings = mailSettings;
    }

    @GetMapping("/events/{id}/attendance")
    public String page(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("attendance", attendance.page(id, principal.getName()));
        model.addAttribute("attendanceMailEnabled", mailSettings.enabled());
        return "attendance";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String problem(ResponseStatusException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("problem", exception.getReason());
        return "attendance-problem";
    }

    @PostMapping("/events/{id}/attendance/confirm")
    public String confirm(@PathVariable long id, @RequestParam long version,
                          @RequestParam(required = false) Set<Long> recipientIds, Principal principal, RedirectAttributes redirect) {
        attendance.confirm(id, version, recipientIds == null ? Set.of() : recipientIds, principal.getName());
        redirect.addFlashAttribute("attendanceConfirmed", true);
        return "redirect:/events/" + id + "/attendance";
    }

    @PostMapping("/events/{id}/attendance")
    public String save(@PathVariable long id, @RequestParam long version,
                       @RequestParam(required = false) Set<Long> attendeeIds, Principal principal, RedirectAttributes redirect) {
        attendance.save(id, version, attendeeIds == null ? Set.of() : attendeeIds, principal.getName());
        redirect.addFlashAttribute("attendanceSaved", true);
        return "redirect:/events/" + id + "/attendance";
    }
}
