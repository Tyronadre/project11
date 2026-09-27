package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.dashboard.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import de.tyro.project11.tallies.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:optional-office;DB_CLOSE_DELAY=-1",
        "app.attendance-penalties.initial-delay=3600000", "app.travel-penalties.initial-delay=3600000"})
@Import(AttendanceWorkflowIntegrationTests.TimeConfig.class)
@AutoConfigureMockMvc
class OptionalOfficeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AttendanceWorkflowIntegrationTests.MutableClock clock;
    @Autowired UserRepository users;
    @Autowired ActivityRepository activities;
    @Autowired AttendanceRepository sheets;
    @Autowired AttendanceService attendance;
    @Autowired AttendancePenaltyService penalties;
    @Autowired AttendanceEmailRepository reminders;
    @Autowired AfeaService afea;
    @Autowired AfeaRepository declarations;
    @Autowired ComplaintService complaints;
    @Autowired ComplaintRepository complaintRows;
    @Autowired DashboardService dashboard;
    @Autowired TallyHistoryService history;
    @Autowired TallyRecordRepository historyRows;
    @Autowired HolidayRepository holidays;
    @Autowired TravelApplicationRepository travels;
    @Autowired TravelPenaltyService travelPenalties;
    @Autowired OfficeAtmosphere atmosphere;
    AppUser owner, admin, other;
    Activity event;
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        clock.set(Instant.parse("2090-01-12T12:00:00Z"));
        owner = users.save(new AppUser("Teilnehmer", suffix + "@owner.test", "unused"));
        admin = users.save(new AppUser("Verwaltung", suffix + "@admin.test", "unused", true));
        other = users.save(new AppUser("Noch jemand", suffix + "@other.test", "unused"));
        event = activities.save(new Activity("Wintertreffen", "", "", OffsetDateTime.parse("2090-01-01T12:00:00Z"),
                OffsetDateTime.parse("2090-01-01T14:00:00Z"), admin));
    }
    @Test void afeaOnlyPrefillsNewListsAndNeverConfirmsAttendance() throws Exception {
        long mails = reminders.count();
        afea.submit(event.getId(), 0, owner.getEmail());
        afea.submit(event.getId(), 1, owner.getEmail());
        assertThat(declarations.findByActivityId(event.getId())).hasSize(1);
        assertThat(sheets.existsById(event.getId())).isFalse();
        assertThat(attendance.page(event.getId(), admin.getEmail()).members()).anyMatch(m -> m.id() == owner.getId() && m.attended());
        assertThat(tally(owner)).isZero(); assertThat(reminders.count()).isEqualTo(mails);
        mvc.perform(get("/events/" + event.getId()).with(user(owner.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("AFeA zurückziehen")));
        mvc.perform(get("/events/" + event.getId() + "/attendance").with(user(admin.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Freiwilliger AFeA")));
        attendance.save(event.getId(), -1, Set.of(other.getId()), admin.getEmail()); // other attended without any AFeA
        afea.withdraw(event.getId(), owner.getEmail());
        afea.submit(event.getId(), 2, owner.getEmail());
        var page = attendance.page(event.getId(), admin.getEmail());
        assertThat(page.members()).anyMatch(m -> m.id() == owner.getId() && !m.attended());
        assertThat(page.members()).anyMatch(m -> m.id() == other.getId() && m.attended());
        assertThat(page.confirmed()).isFalse();
        assertThat(tally(owner)).isZero(); assertThat(reminders.count()).isEqualTo(mails);
    }
    @Test void afeaRequiresCsrfAndCannotBeFiledForAnotherMemberOrFutureEvent() throws Exception {
        String url = "/events/" + event.getId() + "/afea";
        mvc.perform(post(url).with(user(owner.getEmail())).param("evidence", "0")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(owner.getEmail())).with(csrf()).param("evidence", "0").param("userId", other.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(afea.load(event.getId(), other.getEmail()).submitted()).isFalse();
        clock.set(event.getStartsAt().minusDays(1).toInstant());
        mvc.perform(post(url).with(user(other.getEmail())).with(csrf()).param("evidence", "0")).andExpect(status().isConflict());
    }
    @Test void manualChangesAreRecordedWithActorAndUnchangedRequestsAddNothing() throws Exception {
        dashboard.changeTally(admin.getEmail(), owner.getId(), TallyChange.ADD_FIVE, null);
        dashboard.changeTally(admin.getEmail(), owner.getId(), TallyChange.SET, 5);
        dashboard.changeTally(admin.getEmail(), owner.getId(), TallyChange.DECREMENT, null);
        var page = history.page(owner.getId(), 0);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().getFirst().before()).isEqualTo(5);
        assertThat(page.getContent().getFirst().after()).isEqualTo(4);
        assertThat(page.getContent().getFirst().actor()).isEqualTo("Verwaltung");
        mvc.perform(get("/users/" + owner.getId()).with(user(other.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Änderungsverlauf der Striche")))
                .andExpect(content().string(containsString("Manuell einen Strich entfernt")));
        assertThatThrownBy(() -> dashboard.changeTally(owner.getEmail(), other.getId(), TallyChange.INCREMENT, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void eventPenaltyAndReversalAppearOnceInHistoryAndOnProfileCards() throws Exception {
        attendance.save(event.getId(), -1, Set.of(admin.getId()), admin.getEmail());
        var page = attendance.page(event.getId(), admin.getEmail());
        attendance.confirm(event.getId(), page.version(), Set.copyOf(page.recipientIds()), admin.getEmail());
        penalties.reconcile(event.getId());
        assertThat(history.page(owner.getId(), 0).getTotalElements()).isEqualTo(1);
        mvc.perform(get("/users/" + owner.getId()).with(user(owner.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("1 automatischer Strich für dieses Event vergeben")));
        var stored = activities.findById(event.getId()).orElseThrow(); stored.cancel("Wetter", OffsetDateTime.now(clock)); activities.save(stored);
        penalties.reconcile(event.getId()); penalties.reconcile(event.getId());
        assertThat(tally(owner)).isZero();
        assertThat(history.page(owner.getId(), 0).getTotalElements()).isEqualTo(2);
        mvc.perform(get("/users/" + owner.getId()).with(user(owner.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Automatischer Event-Strich zurückgenommen")));
    }
    @Test void travelDayPenaltiesAreRecordedOnceWithRelatedApplication() {
        var holiday = holidays.save(new Holiday(owner, "Winterreise", "", LocalDate.of(2090,1,1), LocalDate.of(2090,1,2), OffsetDateTime.now(clock).minusDays(20)));
        var leave = new TravelApplication(TravelKind.LEAVE, UUID.randomUUID().toString(), owner, holiday, "Winterreise", holiday.getSubmittedAt(), List.of());
        leave.decide(TravelApplication.Decision.ACCEPTED, admin, OffsetDateTime.now(clock).minusDays(15), ""); leave = travels.save(leave);
        travelPenalties.reconcile(owner.getId()); travelPenalties.reconcile(owner.getId());
        assertThat(tally(owner)).isEqualTo(2);
        var entries = history.page(owner.getId(), 0);
        assertThat(entries.getTotalElements()).isEqualTo(1);
        assertThat(entries.getContent().getFirst().url()).isEqualTo("/amt/reisen/" + leave.getId());
    }
    @Test void complaintsAreEscapedOwnedAndHaveNoEffectsOutsideTheirOwnRecords() throws Exception {
        long events = activities.count(), sheetsBefore = sheets.count(), mails = reminders.count(), marks = historyRows.count();
        var form = new ComplaintForm(); form.setSubject("Faxstau"); form.setText("<script>Unzufriedenheit</script>");
        long id = complaints.submit(form, owner.getEmail());
        mvc.perform(get("/amt/bub/" + id).with(user(other.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>Unzufriedenheit"))));
        mvc.perform(post("/amt/bub/" + id + "/close").with(user(owner.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(post("/amt/bub/" + id + "/close").with(user(other.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/amt/bub/" + id + "/rate").with(user(owner.getEmail())).with(csrf()).param("rating", "3")).andExpect(status().isConflict());
        complaints.close(id, owner.getEmail()); complaints.rate(id, 5, owner.getEmail());
        form.setParentId(id); long child = complaints.submit(form, owner.getEmail());
        assertThat(complaints.load(child, owner.getEmail()).parentId()).isEqualTo(id);
        assertThat(complaints.load(id, owner.getEmail()).rating()).isEqualTo(5);
        mvc.perform(get("/amt/bub").with(user(owner.getEmail()))).andExpect(status().isOk());
        mvc.perform(get("/amt/bub/neu").param("parent", "" + id).with(user(owner.getEmail()))).andExpect(status().isOk());
        assertThat(activities.count()).isEqualTo(events); assertThat(sheets.count()).isEqualTo(sheetsBefore);
        assertThat(reminders.count()).isEqualTo(mails); assertThat(historyRows.count()).isEqualTo(marks); assertThat(tally(owner)).isZero();
    }
    @Test void decorativeOfficeMoodChangesWithoutChangingAnything() throws Exception {
        long count = historyRows.count(); var first = atmosphere.current(); clock.set(clock.instant().plusSeconds(3600));
        assertThat(atmosphere.current()).isNotEqualTo(first); assertThat(historyRows.count()).isEqualTo(count);
        mvc.perform(get("/amt").with(user(owner.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("reine Dekoration")));
    }
    private int tally(AppUser u) { return users.findById(u.getId()).orElseThrow().getTallyCount(); }
}
