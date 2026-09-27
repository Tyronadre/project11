package de.tyro.project11.auth;

import de.tyro.project11.dashboard.DashboardService;
import de.tyro.project11.dashboard.OpenItemsService;
import de.tyro.project11.dashboard.DecisionReceipt;
import de.tyro.project11.calendar.CalendarService;
import de.tyro.project11.attendance.AttendanceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class SignInController {

    private final DashboardService dashboard;

    private final CalendarService calendar;

    private final AttendanceService attendance;
    private final OpenItemsService openItems;

    public SignInController(DashboardService dashboard, CalendarService calendar, AttendanceService attendance, OpenItemsService openItems) {
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.attendance = attendance;
        this.openItems = openItems;
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
    public String welcome(Principal principal, Model model,
                          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean editTallies) {
        var view = dashboard.load(principal.getName());
        model.addAttribute("dashboard", view);
        model.addAttribute("editingTallies", view.admin() && editTallies);
        model.addAttribute("pendingReviews", openItems.pendingReviews(principal.getName()));
        model.addAttribute("upcomingEvents", calendar.upcoming());
        var pending = attendance.pending(principal.getName());
        model.addAttribute("pendingAttendance", pending);
        model.addAttribute("openItems", openItems.load(principal.getName(), pending));
        model.addAttribute("attendanceEvents", attendance.recent(principal.getName()));
        return "welcome";
    }

    @PostMapping("/welcome/decisions/{source}/{id}/read")
    public String readDecision(@PathVariable DecisionReceipt.Source source, @PathVariable long id,
                               Principal principal, RedirectAttributes redirect) {
        openItems.markRead(principal.getName(), source, id);
        redirect.addFlashAttribute("decisionRead", true);
        return "redirect:/welcome#for-you";
    }
}
