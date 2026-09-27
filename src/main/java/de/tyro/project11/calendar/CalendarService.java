package de.tyro.project11.calendar;

import de.tyro.project11.profile.UserProfileRepository;
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
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CalendarService {
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.GERMAN);
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.GERMAN);
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter DATE_TIME_LABEL = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z", Locale.GERMAN);
    private static final YearMonth FIRST_MONTH = YearMonth.of(1, 1);
    private static final YearMonth LAST_MONTH = YearMonth.of(9999, 12);

    private final ActivityRepository activities;
    private final HolidayRepository holidays;
    private final Clock clock;
    private final UserProfileRepository profiles;

    public CalendarService(ActivityRepository activities, HolidayRepository holidays, Clock clock, UserProfileRepository profiles) {
        this.activities = activities;
        this.holidays = holidays;
        this.clock = clock;
        this.profiles = profiles;
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

        // Fetch only colors, without loading private profile fields or one profile per holiday.
        var holidayUserIds = monthHolidays.stream().map(holiday -> holiday.getUser().getId()).distinct().toList();
        Map<Long, String> userColors = filter == CalendarFilter.ACTIVITIES || holidayUserIds.isEmpty() ? Map.of()
                : profiles.findColorsByUserIds(holidayUserIds).stream().collect(Collectors.toMap(
                        UserProfileRepository.ProfileColor::getUserId, UserProfileRepository.ProfileColor::getColor));
        var holidayColors = monthHolidays.stream().collect(Collectors.toMap(
                holiday -> "holiday-" + holiday.getId(),
                holiday -> userColors.getOrDefault(holiday.getUser().getId(), "#52734d")));

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
                    // Stable grouping keeps the existing order within events and within holidays.
                    .sorted(Comparator.comparingInt(entry -> entry.kind().equals("activity") ? 0 : 1))
                    .map(entry -> new CalendarView.DayEntry(entry.key(), entry.kind(), entry.title(),
                            daySummary(entry, date), entry.owner(), holidayColors.get(entry.key()),
                            entry.kind().equals("holiday") ? entry.title() + "\nUrlaub von " + entry.owner() + "\n" + entry.period()
                                    + (entry.description().isBlank() ? "" : "\n" + entry.description()) : null))
                    .toList() : List.<CalendarView.DayEntry>of();
            return new CalendarView.Day(date, date.getDayOfMonth(), inMonth, date.equals(today), dayEntries);
        }).toList();

        return new CalendarView(month.toString(), month.format(MONTH_LABEL),
                month.equals(FIRST_MONTH) ? null : month.minusMonths(1).toString(),
                month.equals(LAST_MONTH) ? null : month.plusMonths(1).toString(),
                YearMonth.from(today).toString(), filter, monthActivities.size(), monthHolidays.size(),
                days, List.copyOf(entries));
    }

    public record UpcomingEvent(long id, String title, String description, String owner, String period,
                                LocalDate date, String day, String month, String calendarMonth, String status) {}

    @Transactional(readOnly = true)
    public List<UpcomingEvent> upcoming() {
        var now = clock.instant();
        var today = LocalDate.now(clock.withZone(CalendarTime.BERLIN));
        return activities.findTop6ByEndsAtAfterOrderByStartsAtAscIdAsc(now.atOffset(java.time.ZoneOffset.UTC)).stream()
                .map(activity -> {
                    var entry = activityEntry(activity);
                    var date = entry.firstDay();
                    String status = activity.hasStartTime() && !activity.getStartsAt().toInstant().isAfter(now)
                            ? "Findet gerade statt" : date.equals(today) ? "Heute" : date.equals(today.plusDays(1)) ? "Morgen" : "Demnächst";
                    return new UpcomingEvent(activity.getId(), entry.title(), entry.description(), entry.owner(), entry.period(),
                            date, date.format(DateTimeFormatter.ofPattern("dd")), date.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)),
                            YearMonth.from(date).toString(), status);
                }).toList();
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen gültigen Monat im Format JJJJ-MM auswählen.");
        }
    }

    static CalendarView.Entry activityEntry(Activity activity) {
        var start = activity.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN);
        var end = activity.getEndsAt().atZoneSameInstant(CalendarTime.BERLIN);
        String period = activity.isAllDay() ? start.format(DATE_LABEL) + (end.minusNanos(1).toLocalDate().equals(start.toLocalDate()) ? "" : " – " + end.minusNanos(1).format(DATE_LABEL)) + " · ganztägig"
                : !activity.hasStartTime() ? start.format(DATE_LABEL) + " · bis " + (end.toLocalDate().equals(start.toLocalDate())
                    ? end.format(DateTimeFormatter.ofPattern("HH:mm z", Locale.GERMAN)) : end.format(DATE_TIME_LABEL))
                : start.format(DATE_TIME_LABEL) + (activity.hasEndTime() ? " – " + end.format(DATE_TIME_LABEL) : "");
        return new CalendarView.Entry("activity-" + activity.getId(), "activity", (activity.isCancelled() ? "Abgesagt · " : "") + activity.getTitle(),
                activity.getCreatedBy().getDisplayName(), period,
                activity.getLocation(), activity.getDescription(), start.toLocalDate(),
                end.minusNanos(1).toLocalDate(), activity.isAllDay() ? "" : !activity.hasStartTime() ? "Bis " + end.format(TIME_LABEL) : start.format(TIME_LABEL));
    }

    private CalendarView.Entry holidayEntry(Holiday holiday) {
        return new CalendarView.Entry("holiday-" + holiday.getId(), "holiday", holiday.getTitle(),
                holiday.getUser().getDisplayName(), holiday.getStartsOn().format(DATE_LABEL) + " – "
                + holiday.getEndsOn().format(DATE_LABEL) + " · ganztägig, beide Tage einschließlich",
                "", holiday.getDescription(), holiday.getStartsOn(), holiday.getEndsOn(), "");
    }

    private String daySummary(CalendarView.Entry entry, LocalDate date) {
        if (entry.kind().equals("holiday") || entry.startTime().isEmpty()) {
            return entry.owner() + " · ganztägig";
        }
        return (date.equals(entry.firstDay()) ? entry.startTime() : "Continues") + " · " + entry.owner();
    }
}
