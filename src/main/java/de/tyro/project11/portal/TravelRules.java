package de.tyro.project11.portal;

import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.calendar.Holiday;
import java.time.ZonedDateTime;

public final class TravelRules {
    private TravelRules() {}
    // End 1 January -> filing throughout 8 January -> exclusive cutoff 9 January, midnight Berlin.
    public static ZonedDateTime deadline(Holiday holiday) {
        return holiday.getEndsOn().plusDays(8).atStartOfDay(CalendarTime.BERLIN);
    }
    public static boolean timely(TravelApplication report) {
        return report.getSubmittedAt().toInstant().isBefore(deadline(report.getHoliday()).toInstant());
    }
}
