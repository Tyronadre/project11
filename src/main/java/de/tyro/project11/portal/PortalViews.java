package de.tyro.project11.portal;

import java.util.List;

public final class PortalViews {
    private PortalViews() {}
    public record Citizen(long id, String name, String number, long applicationCount, int reliability) {}
    public record Member(long id, String name, String number) {}
    public record ActivityOption(long id, String title, String date, String period, String deadline) {}
    public record Summary(long id, String reference, long applicantId, String applicantName,
                          String activityTitle, String submittedAt, String status, String kind, String url,
                          java.time.Instant submittedInstant) {}
    public record Section(String name, List<ApplicationAnswer> answers) {}
    public record File(Summary summary, List<Section> sections) {}
}
