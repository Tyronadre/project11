package de.tyro.project11.calendar;

import org.springframework.stereotype.Controller;
import de.tyro.project11.attendance.AttendanceService;
import java.security.Principal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CalendarController {
    private final CalendarService calendar;

    private final AttendanceService attendance;

    public CalendarController(CalendarService calendar, AttendanceService attendance) {
        this.calendar = calendar;
        this.attendance = attendance;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) String month,
                           @RequestParam(defaultValue = "ALL") CalendarFilter show, Principal principal, Model model) {
        var view = calendar.load(month, show);
        model.addAttribute("calendar", view);
        model.addAttribute("attendanceLinks", attendance.calendarLinks(view.month(), principal.getName()));
        return "calendar";
    }
}
