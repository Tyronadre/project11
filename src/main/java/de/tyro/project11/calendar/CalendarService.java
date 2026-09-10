package de.tyro.project11.calendar;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CalendarService {
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_LABEL = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z", Locale.ENGLISH);
    private static final YearMonth FIRST_MONTH = YearMonth.of(1, 1);
    private static final YearMonth LAST_MONTH = YearMonth.of(9999, 12);

    private final ActivityRepository activities;
    private final HolidayRepository holidays;
    private final Clock clock;

    public CalendarService(ActivityRepository activities, HolidayRepository holidays, Clock clock) {
        this.activities = activities;
        this.holidays = holidays;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendarView load(String requestedMonth, CalendarFilter filter) {
        LocalDate today = LocalDate.now(clock.withZone(CalendarTime.BERLIN));
        YearMonth month = parseMonth(requestedMonth, today);
        LocalDate first = month.atDay(1);
        LocalDate afterLast = month.plusMonths(1).atDay(1);
        // Activity intervals are half-open; holidays include both of their dates.
        var monthActivities = activities.findByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAscIdAsc(
                afterLast.atStartOfDay(CalendarTime.BERLIN).toOffsetDateTime(),
                first.atStartOfDay(CalendarTime.BERLIN).toOffsetDateTime());
        var monthHolidays = holidays.findByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnAscIdAsc(
                month.atEndOfMonth(), first);

        List<CalendarView.Entry> entries = new ArrayList<>();
        if (filter != CalendarFilter.HOLIDAYS) {
            monthActivities.forEach(activity -> entries.add(activityEntry(activity)));
        }
        if (filter != CalendarFilter.ACTIVITIES) {
            monthHolidays.forEach(holiday -> entries.add(holidayEntry(holiday)));
        }
        entries.sort(Comparator.comparing(CalendarView.Entry::firstDay)
                .thenComparing(CalendarView.Entry::startTime)
                .thenComparing(CalendarView.Entry::key));

        LocalDate gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<CalendarView.Day> days = gridStart.datesUntil(gridEnd.plusDays(1)).map(date -> {
            boolean inMonth = YearMonth.from(date).equals(month);
            // Adjacent-month cells are muted placeholders; navigate to see their full agenda.
            var dayEntries = inMonth ? entries.stream()
                    .filter(entry -> !date.isBefore(entry.firstDay()) && !date.isAfter(entry.lastDay()))
                    .map(entry -> new CalendarView.DayEntry(entry.key(), entry.kind(), entry.title(),
                            daySummary(entry, date)))
                    .toList() : List.<CalendarView.DayEntry>of();
            return new CalendarView.Day(date, date.getDayOfMonth(), inMonth, date.equals(today), dayEntries);
        }).toList();

        return new CalendarView(month.toString(), month.format(MONTH_LABEL),
                month.equals(FIRST_MONTH) ? null : month.minusMonths(1).toString(),
                month.equals(LAST_MONTH) ? null : month.plusMonths(1).toString(),
                YearMonth.from(today).toString(), filter, monthActivities.size(), monthHolidays.size(),
                days, List.copyOf(entries));
    }

    private YearMonth parseMonth(String value, LocalDate today) {
        if (value == null) {
            return YearMonth.from(today);
        }
        try {
            if (!value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
                throw new IllegalArgumentException();
            }
            YearMonth month = YearMonth.parse(value);
            if (month.isBefore(FIRST_MONTH) || month.isAfter(LAST_MONTH)) {
                throw new IllegalArgumentException();
            }
            return month;
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid month in YYYY-MM format.");
        }
    }

    private CalendarView.Entry activityEntry(Activity activity) {
        var start = activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN);
        var end = activity.getEndsAt().atZoneSameInstant(CalendarTime.BERLIN);
        return new CalendarView.Entry("activity-" + activity.getId(), "activity", activity.getTitle(),
                activity.getCreatedBy().getDisplayName(), start.format(DATE_TIME_LABEL) + " – " + end.format(DATE_TIME_LABEL),
                activity.getLocation(), activity.getDescription(), start.toLocalDate(),
                end.minusNanos(1).toLocalDate(), start.format(TIME_LABEL));
    }

    private CalendarView.Entry holidayEntry(Holiday holiday) {
        return new CalendarView.Entry("holiday-" + holiday.getId(), "holiday", holiday.getTitle(),
                holiday.getUser().getDisplayName(), holiday.getStartsOn().format(DATE_LABEL) + " – "
                + holiday.getEndsOn().format(DATE_LABEL) + " · all day, both dates included",
                "", holiday.getDescription(), holiday.getStartsOn(), holiday.getEndsOn(), "");
    }

    private String daySummary(CalendarView.Entry entry, LocalDate date) {
        if (entry.kind().equals("holiday")) {
            return entry.owner() + " · all day";
        }
        return (date.equals(entry.firstDay()) ? entry.startTime() : "Continues") + " · " + entry.owner();
    }
}
