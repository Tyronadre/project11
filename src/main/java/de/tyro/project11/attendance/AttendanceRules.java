package de.tyro.project11.attendance;

import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.AbsenceApplication;
import de.tyro.project11.portal.TravelApplication;
import de.tyro.project11.portal.TravelKind;
import org.springframework.stereotype.Component;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AttendanceRules {
    private final HolidayRepository holidays;
    public AttendanceRules(HolidayRepository holidays) { this.holidays = holidays; }

    // Calendar-day deadline, including overnight/all-day events and DST transitions.
    public static ZonedDateTime deadline(Activity activity) {
        return activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).toLocalDate()
                .plusDays(7).atStartOfDay(CalendarTime.BERLIN);
    }
    public static boolean timely(AbsenceApplication application) {
        return application.getSubmittedAt().toInstant().isBefore(deadline(application.getActivity()).toInstant());
    }
    public Set<Long> holidayMembers(Activity activity) {
        var first = activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).toLocalDate();
        var last = activity.getEndsAt().atZoneSameInstant(CalendarTime.BERLIN).minusNanos(1).toLocalDate();
        var byUser = holidays.findCoverage(first, last, TravelKind.LEAVE).stream()
                // Invalidated accepted leave is charged by vacation day, never again by event.
                .filter(holiday -> holiday.getInvalidatedAt() != null || holiday.getSubmittedAt().toInstant().isBefore(deadline(activity).toInstant()))
                .filter(holiday -> holiday.getDecision() != TravelApplication.Decision.REJECTED)
                .collect(Collectors.groupingBy(HolidayRepository.Coverage::getUserId));
        Set<Long> covered = new HashSet<>();
        byUser.forEach((id, periods) -> {
            var uncovered = first;
            for (var holiday : periods) {
                if (holiday.getStartsOn().isAfter(uncovered)) break;
                if (!holiday.getEndsOn().isBefore(uncovered)) uncovered = holiday.getEndsOn().plusDays(1);
                if (uncovered.isAfter(last)) { covered.add(id); break; }
            }
        });
        return covered;
    }
}
