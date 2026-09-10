package de.tyro.project11.calendar;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CalendarController {
    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) String month,
                           @RequestParam(defaultValue = "ALL") CalendarFilter show, Model model) {
        model.addAttribute("calendar", calendar.load(month, show));
        return "calendar";
    }
}
