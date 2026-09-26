package de.tyro.project11.polls;
import jakarta.validation.constraints.*;
import java.util.*;
public class PollForm {
    @NotBlank @Size(max = 120) private String title = "";
    @Size(max = 200) private String location = "";
    @Size(max = 2000) private String description = "";
    @Min(15) @Max(1440) private int durationMinutes = 120;
    @Size(min = 2, max = 6) private List<@Size(max = 30) String> slots = new ArrayList<>(Collections.nCopies(6, ""));
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value == null ? "" : value.strip(); }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value == null ? "" : value.strip(); }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value == null ? "" : value.strip(); }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int value) { durationMinutes = value; }
    public List<String> getSlots() { return slots; }
    public void setSlots(List<String> value) { slots = value; }
}
