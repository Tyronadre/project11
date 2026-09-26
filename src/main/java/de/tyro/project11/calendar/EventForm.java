package de.tyro.project11.calendar;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalTime;

public class EventForm {
    private long revision;
    @Size(max = 200, message = "Der Ort darf höchstens 200 Zeichen enthalten.")
    private String location = "";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    public long getRevision() { return revision; }
    public void setRevision(long value) { revision = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value == null ? "" : value.strip(); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate value) { endDate = value; }
    @AssertTrue(message = "Bitte ein gültiges Enddatum ab dem Startdatum wählen (bis Jahr 9998).")
    public boolean isEndDateInRange() { return endDate == null || (endDate.getYear() >= 1 && endDate.getYear() <= 9998 && (date == null || !endDate.isBefore(date))); }
    private boolean costSelectedOnly;
    private java.util.Set<Long> costSelectedUserIds = new java.util.HashSet<>();
    public boolean isCostSelectedOnly() { return costSelectedOnly; }
    public void setCostSelectedOnly(boolean value) { costSelectedOnly = value; }
    public java.util.Set<Long> getCostSelectedUserIds() { return costSelectedUserIds; }
    public void setCostSelectedUserIds(java.util.Set<Long> value) { costSelectedUserIds = value == null ? new java.util.HashSet<>() : new java.util.HashSet<>(value); }
    @NotBlank(message = "Give your event a name.")
    @Size(max = 120, message = "Keep the name within 120 characters.")
    private String name;
    @Size(max = 2000, message = "Keep the description within 2,000 characters.")
    private String description = "";
    @NotNull(message = "Choose a date for your event.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime time;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;
    @Size(max = 20, message = "Bitte einen gültigen Eurobetrag eingeben.")
    private String costAmount = "";

    @AssertTrue(message = "Choose a date between years 0001 and 9998.")
    public boolean isDateInRange() { return date == null || (date.getYear() >= 1 && date.getYear() <= 9998); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.strip(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description.strip(); }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time; }
    public String getCostAmount() { return costAmount; }
    public void setCostAmount(String value) { costAmount = value == null ? "" : value.strip(); }
}
