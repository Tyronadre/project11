package de.tyro.project11.calendar;

import java.time.LocalDate;
import java.util.List;

public record CalendarView(String month, String monthLabel, String previousMonth, String nextMonth,
                           String todayMonth, CalendarFilter filter, long activityCount, long holidayCount,
                           List<Day> days, List<Entry> entries) {

    public record Entry(String key, String kind, String title, String owner, String period,
                        String location, String description, LocalDate firstDay, LocalDate lastDay,
                        String startTime) {
        public String url() { return entryUrl(key, kind); }
    }

    public record Day(LocalDate date, int number, boolean inMonth, boolean today, List<DayEntry> entries) {}

    public record DayEntry(String key, String kind, String title, String summary, String owner, String color, String details) {
        public String url() { return entryUrl(key, kind); }
    }

    private static String entryUrl(String key, String kind) {
        return kind.equals("activity") ? "/events/" + key.substring("activity-".length()) : "#" + key;
    }
}
