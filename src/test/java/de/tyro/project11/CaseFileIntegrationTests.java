package de.tyro.project11;

import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:case-files;DB_CLOSE_DELAY=-1",
        "app.attendance-penalties.initial-delay=3600000", "app.travel-penalties.initial-delay=3600000"})
@AutoConfigureMockMvc
class CaseFileIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired CaseFileService files;
    @Autowired PortalService portal;
    @Autowired AbsenceApplicationRepository absences;
    @Autowired TravelApplicationRepository travels;
    @Autowired UserRepository users;
    @Autowired ActivityRepository activities;
    @Autowired HolidayRepository holidays;
    AppUser owner, admin, reader;
    static final OffsetDateTime FILED = OffsetDateTime.parse("2090-12-31T23:30:00Z");
    static final OffsetDateTime DECIDED = FILED.plusHours(2);

    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        owner = users.save(new AppUser("Antragsteller", suffix + "@owner.test", "hash"));
        admin = users.save(new AppUser("Sachbearbeitung", suffix + "@admin.test", "hash", true));
        reader = users.save(new AppUser("Mitglied", suffix + "@reader.test", "hash"));
    }
    AbsenceApplication absence(boolean decided) {
        var event = activities.save(new Activity("Neujahrstreffen", "Berlin", "", FILED.plusDays(2), FILED.plusDays(2).plusHours(2), owner));
        var a = new AbsenceApplication(owner, event, FILED, List.of());
        if (decided) a.decide(AbsenceApplication.Decision.REJECTED, admin, DECIDED, "<script>alert('x')</script>\nBitte beim nächsten Mal dabei sein.");
        return absences.save(a);
    }
    TravelApplication travel(TravelKind kind, Holiday holiday, OffsetDateTime at, boolean accepted) {
        var a = new TravelApplication(kind, UUID.randomUUID().toString(), owner, holiday, "Reise nach Berlin", at, List.of());
        if (accepted) a.decide(TravelApplication.Decision.ACCEPTED, admin, at.plusHours(1), "Vollständig geprüft.");
        return travels.save(a);
    }
    Holiday holiday() {
        return holidays.save(new Holiday(owner, "Berlin", "", LocalDate.of(2091, 1, 3), LocalDate.of(2091, 1, 4), FILED));
    }
    @Test void referencesUseBerlinFilingYearAndRemainStable() {
        var a = absence(false);
        String reference = CaseReference.of(a);
        assertThat(reference).startsWith("AaA-2091-");
        assertThat(files.absence(a.getId(), owner.getEmail()).reference()).isEqualTo(reference);
        a.decide(AbsenceApplication.Decision.ACCEPTED, admin, DECIDED, ""); absences.save(a);
        assertThat(files.absence(a.getId(), owner.getEmail()).reference()).isEqualTo(reference);
        assertThat(portal.file(a.getId()).summary().reference()).isEqualTo(reference);
    }
    @Test void pendingFilesHaveAnEntryButNoNotice() throws Exception {
        var a = absence(false);
        var file = files.absence(a.getId(), owner.getEmail());
        assertThat(file.history()).hasSize(1);
        assertThat(file.history().getFirst().at().toInstant()).isEqualTo(FILED.toInstant());
        assertThat(file.issued()).isFalse();
        mvc.perform(get(file.url()).with(user(reader.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Entscheidung ausstehend")))
                .andExpect(content().string(not(containsString("Bescheid öffnen / drucken"))));
        mvc.perform(get(file.url() + "/bescheid").with(user(reader.getEmail())))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Noch kein Bescheid")));
        var leave = travel(TravelKind.LEAVE, holiday(), FILED, false);
        mvc.perform(get("/amt/reisen/" + leave.getId() + "/bescheid").with(user(owner.getEmail())))
                .andExpect(status().isConflict());
    }
    @Test void noticeShowsSavedDecisionEscapesReasonAndDoesNotMutateHistory() throws Exception {
        var a = absence(true);
        var before = files.absence(a.getId(), owner.getEmail());
        String html = mvc.perform(get(before.url() + "/bescheid").with(user(reader.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Abgelehnt")))
                .andExpect(content().string(containsString("Sachbearbeitung")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains(before.reference(), before.decided(), "/js/amt-print.js");
        assertThat(files.absence(a.getId(), reader.getEmail())).isEqualTo(before);
        assertThat(before.history()).hasSize(2);
    }
    @Test void noticesRequireAnExistingSignedInMember() throws Exception {
        var a = absence(true);
        String url = "/amt/antraege/" + a.getId() + "/bescheid";
        mvc.perform(get(url)).andExpect(status().is3xxRedirection());
        mvc.perform(get(url).with(user("missing@example.test"))).andExpect(status().isForbidden());
        mvc.perform(get("/amt/reisen/999999999/bescheid").with(user(reader.getEmail())))
                .andExpect(status().isNotFound());
    }
    @Test void linkedTravelHistoryShowsDecisionsAndLaterInvalidation() throws Exception {
        var holiday = holiday();
        var leave = travel(TravelKind.LEAVE, holiday, FILED, true);
        var report = travel(TravelKind.REPORT, holiday, FILED.plusDays(5), true);
        holiday.invalidate(FILED.plusDays(20)); holidays.save(holiday);
        var file = files.travel(leave.getId(), reader.getEmail());
        assertThat(file.history()).hasSize(5);
        assertThat(file.history().getLast().title()).isEqualTo("Beurlaubung verfallen");
        assertThat(file.history()).anyMatch(e -> ("/amt/reisen/" + report.getId()).equals(e.url()));
        mvc.perform(get(file.url() + "/bescheid").with(user(reader.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString(CaseReference.of(leave))))
                .andExpect(content().string(containsString("Beurlaubung verfallen")));
        mvc.perform(get("/amt/reisen/" + report.getId() + "/bescheid").with(user(reader.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString(CaseReference.of(report))));
        mvc.perform(get("/amt/reisen/" + leave.getId()).with(user(reader.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bearbeitungsverlauf")));
    }
    @Test void longNoticeRendersAndExportsVisualQaFixture() throws Exception {
        var a = absence(false);
        a.decide(AbsenceApplication.Decision.REJECTED, admin, DECIDED,
                "Die eingereichte Begründung wurde sorgfältig geprüft. Bitte berücksichtigen Sie die vereinbarten Gruppenregeln. ".repeat(16));
        a = absences.save(a);
        String html = mvc.perform(get("/amt/antraege/" + a.getId() + "/bescheid").with(user(reader.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var preview = java.nio.file.Path.of("build", "notice-preview");
        java.nio.file.Files.createDirectories(preview);
        java.nio.file.Files.writeString(preview.resolve("notice.html"), html);
    }
    @Test void cancellationRemainsVisibleBesideOriginalDecision() {
        var a = absence(true);
        var event = activities.findById(a.getActivity().getId()).orElseThrow();
        event.cancel("Wetter", DECIDED.plusDays(1)); activities.save(event);
        var file = files.absence(a.getId(), reader.getEmail());
        assertThat(file.decision()).isEqualTo("REJECTED");
        assertThat(file.history().getLast().title()).isEqualTo("Event abgesagt");
        assertThat(file.effect()).contains("keine Event-Striche");
    }
}
