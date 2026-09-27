package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.dashboard.OpenItemsService;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pending-reviews;DB_CLOSE_DELAY=-1",
        "app.attendance-penalties.initial-delay=3600000", "app.travel-penalties.initial-delay=3600000"})
@AutoConfigureMockMvc
@Transactional
class PendingReviewsIntegrationTests {
    @Autowired OpenItemsService items;
    @Autowired UserRepository users;
    @Autowired ActivityRepository activities;
    @Autowired HolidayRepository holidays;
    @Autowired AttendanceRepository attendance;
    @Autowired AbsenceApplicationRepository absences;
    @Autowired TravelApplicationRepository travel;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;

    @Test void panelTracksApplicationsAndAttendanceUntilEverythingIsDecided() throws Exception {
        var admin = users.save(new AppUser("Admin", "admin@reviews.test", "hash", true));
        var member = users.save(new AppUser("Mitglied", "member@reviews.test", "hash"));
        var now = OffsetDateTime.now();
        var event = activities.save(new Activity("Offene Anwesenheit", "", "", now.minusDays(2), now.minusDays(1), member));
        assertThat(page(admin)).doesNotContain("id=\"pending-reviews\"");
        var sheet = new AttendanceSheet(event, Set.of(member));
        sheet.record(Set.of(member), member, now);
        attendance.saveAndFlush(sheet);
        assertThat(items.pendingReviews(admin.getEmail()).attendance()).hasSize(1);
        String html = page(admin);
        assertThat(html).contains("Anwesenheit bestätigen: Offene Anwesenheit", "/events/" + event.getId() + "/attendance");
        assertThat(html.indexOf("id=\"pending-reviews\"")).isLessThan(html.indexOf("class=\"upcoming-section\""));
        assertThat(page(member)).doesNotContain("id=\"pending-reviews\"");
        sheet.confirm(admin, now); attendance.saveAndFlush(sheet);
        assertThat(page(admin)).doesNotContain("id=\"pending-reviews\"");

        var absence = absences.saveAndFlush(new AbsenceApplication(member, event, now, List.of()));
        var holiday = holidays.save(new Holiday(member, "Urlaub", "", LocalDate.now().minusDays(4), LocalDate.now().minusDays(3), now));
        var leave = travel.saveAndFlush(new TravelApplication(TravelKind.LEAVE, UUID.randomUUID().toString(), member, holiday, "Urlaub", now, List.of()));
        var report = travel.saveAndFlush(new TravelApplication(TravelKind.REPORT, UUID.randomUUID().toString(), member, holiday, "Bericht", now, List.of()));
        assertThat(items.pendingReviews(admin.getEmail()).absences()).isEqualTo(1);
        assertThat(items.pendingReviews(admin.getEmail()).travel()).isEqualTo(2);
        assertThat(page(admin)).contains("1 AaAs prüfen", "2 AaBs und Reiseberichte prüfen");
        // Both persisted representations of pending must be counted.
        jdbc.sql("update absence_applications set decision = 'PENDING' where id = :id").param("id", absence.getId()).update();
        jdbc.sql("update travel_applications set decision = 'PENDING' where id = :id").param("id", leave.getId()).update();
        assertThat(items.pendingReviews(admin.getEmail()).absences()).isEqualTo(1);
        assertThat(items.pendingReviews(admin.getEmail()).travel()).isEqualTo(2);
        absence.decide(AbsenceApplication.Decision.ACCEPTED, admin, now, ""); absences.saveAndFlush(absence);
        leave.decide(TravelApplication.Decision.ACCEPTED, admin, now, ""); travel.saveAndFlush(leave);
        assertThat(page(admin)).contains("id=\"pending-reviews\"");
        report.decide(TravelApplication.Decision.REJECTED, admin, now, "Unvollständig"); travel.saveAndFlush(report);
        assertThat(page(admin)).doesNotContain("id=\"pending-reviews\"");
        sheet.record(Set.of(member), member, now); attendance.saveAndFlush(sheet);
        assertThat(page(admin)).contains("id=\"pending-reviews\"");
    }

    private String page(AppUser account) throws Exception {
        return mvc.perform(get("/welcome").with(user(account.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
