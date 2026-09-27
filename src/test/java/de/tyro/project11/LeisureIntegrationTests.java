package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.costs.EventCostRepository;
import de.tyro.project11.portal.*;
import de.tyro.project11.portal.leisure.*;
import de.tyro.project11.registration.*;
import de.tyro.project11.tallies.TallyRecordRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:leisure;DB_CLOSE_DELAY=-1",
        "app.attendance-penalties.initial-delay=3600000", "app.travel-penalties.initial-delay=3600000"})
@AutoConfigureMockMvc
@Import(AttendanceWorkflowIntegrationTests.TimeConfig.class)
class LeisureIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AttendanceWorkflowIntegrationTests.MutableClock clock;
    @Autowired LeisureService leisure;
    @Autowired LeisureCaseRepository cases;
    @Autowired LostPropertyRepository property;
    @Autowired UserRepository users;
    @Autowired ActivityRepository events;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEmailRepository emails;
    @Autowired AbsenceApplicationRepository absences;
    @Autowired TravelApplicationRepository travel;
    @Autowired TallyRecordRepository history;
    @Autowired EventCostRepository costs;
    AppUser owner, other;
    Activity past;

    @BeforeEach void setup() {
        clock.set(Instant.parse("2090-06-12T12:00:00Z"));
        String suffix = UUID.randomUUID().toString();
        owner = users.save(new AppUser("Mara Muster", suffix + "@one.test", "unused"));
        other = users.save(new AppUser("Kim Kollegium", suffix + "@two.test", "unused"));
        past = events.save(new Activity("Kartoffelabend", "", "", OffsetDateTime.now(clock).minusDays(2), OffsetDateTime.now(clock).minusDays(2).plusHours(2), owner));
    }
    @Test void formsRenderAndIssueEscapedPersistentCertificatesWithoutSideEffects() throws Exception {
        var before = unaffectedCounts();
        for (var kind : LeisureKind.forms()) {
            mvc.perform(get("/amt/extra/formular/" + kind.getSlug()).with(user(owner.getEmail())))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
            var form = form();
            String url = mvc.perform(post("/amt/extra/formular/" + kind.getSlug()).with(user(owner.getEmail())).with(csrf())
                    .param("token", form.getToken()).param("subject", form.getSubject()).param("explanation", form.getExplanation())
                    .param("activityId", past.getId().toString()).param("enthusiasm", "erheblich").param("ownerId", other.getId().toString()))
                    .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
            long id = Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
            assertThat(cases.findById(id).orElseThrow().getOwnerId()).isEqualTo(owner.getId());
            if (kind == LeisureKind.JURISDICTION) for (int step = 0; step < 3; step++) leisure.advance(id, step, owner.getEmail());
            String html = mvc.perform(get(url + "/urkunde").with(user(other.getEmail()))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("&lt;script&gt;", "DIENSTSIEGEL", "Spaßurkunde").doesNotContain("<script>alert");
            assertThat(leisure.submit(kind, form, owner.getEmail())).isEqualTo(id);
            if (kind == LeisureKind.NICKNAME) {
                preview("certificate", html);
                preview("case", mvc.perform(get(url).with(user(owner.getEmail()))).andReturn().getResponse().getContentAsString());
            }
        }
        assertThat(unaffectedCounts()).isEqualTo(before);
        var unchanged = users.findById(owner.getId()).orElseThrow();
        assertThat(unchanged.getDisplayName()).isEqualTo("Mara Muster"); assertThat(unchanged.getTallyCount()).isZero();
        assertThat(leisure.statistics(other.getEmail()).forms()).isZero();
        assertThat(leisure.statistics(owner.getEmail()).forms()).isEqualTo(5);
        preview("index", mvc.perform(get("/amt/extra").with(user(owner.getEmail()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        preview("statistics", mvc.perform(get("/amt/extra/statistik").with(user(owner.getEmail()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void jurisdictionUsesOwnedReplaySafeStepsAndOnlyIssuesFinalCertificate() throws Exception {
        long id = leisure.submit(LeisureKind.JURISDICTION, form(), owner.getEmail());
        String url = "/amt/extra/akten/" + id;
        mvc.perform(get(url + "/urkunde").with(user(owner.getEmail()))).andExpect(status().isConflict());
        mvc.perform(post(url + "/weiter").with(user(other.getEmail())).with(csrf()).param("stage", "0")).andExpect(status().isForbidden());
        leisure.advance(id, 0, owner.getEmail()); leisure.advance(id, 0, owner.getEmail());
        assertThat(leisure.load(id, owner.getEmail()).stage()).isEqualTo(1);
        leisure.advance(id, 1, owner.getEmail()); leisure.advance(id, 2, owner.getEmail()); leisure.advance(id, 3, owner.getEmail());
        var result = leisure.load(id, other.getEmail());
        assertThat(result.stage()).isEqualTo(3); assertThat(result.finished()).isTrue(); assertThat(result.route()).hasSize(4);
        mvc.perform(get(url).with(user(other.getEmail()))).andExpect(status().isOk()).andExpect(content().string(containsString("zweifelsfrei festgestellt")));
    }
    @Test void waitingRoomHasPersonalReusableTicketClockProgressAndUnrestrictedExit() throws Exception {
        long id = leisure.takeTicket(owner.getEmail());
        assertThat(leisure.takeTicket(owner.getEmail())).isEqualTo(id);
        assertThat(leisure.load(id, owner.getEmail()).ahead()).isEqualTo(3);
        String url = "/amt/extra/akten/" + id;
        mvc.perform(get(url).with(user(other.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(post(url + "/verlassen").with(user(other.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(url + "/schalter").with(user(owner.getEmail())).with(csrf())).andExpect(status().isConflict());
        preview("waiting", mvc.perform(get(url).with(user(owner.getEmail()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        clock.set(clock.instant().plusSeconds(15)); assertThat(leisure.load(id, owner.getEmail()).ahead()).isEqualTo(2);
        clock.set(clock.instant().plusSeconds(30)); assertThat(leisure.load(id, owner.getEmail()).waitingReady()).isTrue();
        mvc.perform(post(url + "/schalter").with(user(owner.getEmail())).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(leisure.load(id, owner.getEmail()).outcome()).contains("Schalter 7 hat festgestellt");
        long next = leisure.takeTicket(owner.getEmail()); assertThat(next).isNotEqualTo(id);
        leisure.endWaiting(next, true, owner.getEmail()); leisure.endWaiting(next, false, owner.getEmail());
        assertThat(leisure.load(next, owner.getEmail()).leftWaitingRoom()).isTrue();
        assertThat(leisure.load(next, owner.getEmail()).printable()).isFalse();
    }
    @Test void anticipationRejectsFutureCancelledAndPreMembershipEventsAndInvalidValues() {
        var form = form();
        for (var event : List.of(
                events.save(new Activity("Zukunft", "", "", OffsetDateTime.now(clock).plusDays(1), OffsetDateTime.now(clock).plusDays(1).plusHours(2), owner)),
                events.save(new Activity("Vor Mitgliedschaft", "", "", OffsetDateTime.parse("2000-01-01T12:00Z"), OffsetDateTime.parse("2000-01-01T14:00Z"), owner)))) {
            form.setActivityId(event.getId()); assertThatThrownBy(() -> leisure.submit(LeisureKind.ANTICIPATION, form, owner.getEmail())).isInstanceOf(ResponseStatusException.class);
        }
        past.cancel("Wetter", OffsetDateTime.now(clock)); events.save(past); form.setActivityId(past.getId());
        assertThatThrownBy(() -> leisure.submit(LeisureKind.ANTICIPATION, form, owner.getEmail())).isInstanceOf(ResponseStatusException.class);
        form.setEnthusiasm("x".repeat(100)); assertThat(leisure.formProblem(LeisureKind.ANTICIPATION, form)).isNotNull();
        assertThat(leisure.events(owner.getEmail())).extracting(LeisureService.EventChoice::id).doesNotContain(past.getId());
    }
    @Test void lostPropertyConnectsMembersButOnlyOwnerCanResolveTheirOwnNotice() throws Exception {
        var before = unaffectedCounts();
        var form = propertyForm(); long lost = leisure.postProperty(form, owner.getEmail());
        form.setKind(LostProperty.Kind.FOUND); form.setParentId(lost);
        long found = leisure.postProperty(form, other.getEmail());
        assertThat(leisure.replies(lost, owner.getEmail(), 0).getContent()).extracting(LeisureService.PropertyView::id).containsExactly(found);
        String url = "/amt/extra/fundbuero/" + lost;
        mvc.perform(post(url + "/erledigt").with(user(other.getEmail())).with(csrf())).andExpect(status().isForbidden());
        preview("property", mvc.perform(get(url).with(user(owner.getEmail()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/amt/extra/fundbuero/neu").param("parent", "" + lost).with(user(other.getEmail()))).andExpect(status().isOk());
        leisure.resolveProperty(lost, owner.getEmail()); leisure.resolveProperty(lost, owner.getEmail());
        assertThat(leisure.property(found, other.getEmail()).resolved()).isNull();
        assertThatThrownBy(() -> leisure.postProperty(form, other.getEmail())).isInstanceOf(ResponseStatusException.class);
        assertThat(unaffectedCounts()).isEqualTo(before);
        assertThat(leisure.statistics(owner.getEmail()).propertyNotices()).isEqualTo(1);
        assertThat(leisure.statistics(owner.getEmail()).paperclips()).isEqualTo(2);
        preview("property-list", mvc.perform(get("/amt/extra/fundbuero").with(user(owner.getEmail()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void validationSecurityAndPostRoutesWorkWithoutJavaScript() throws Exception {
        long count = cases.count();
        for (String path : List.of("/amt/extra", "/amt/extra/wartezimmer", "/amt/extra/statistik", "/amt/extra/fundbuero")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
            mvc.perform(get(path).with(user(owner.getEmail()))).andExpect(status().isOk());
        }
        for (String path : List.of("/amt/extra/wartezimmer", "/amt/extra/formular/spitzname", "/amt/extra/fundbuero"))
            mvc.perform(post(path).with(user(owner.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(post("/amt/extra/formular/spitzname").with(user(owner.getEmail())).with(csrf()).param("subject", "   "))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bitte den gewünschten Spitznamen")));
        mvc.perform(post("/amt/extra/formular/spitzname").with(user(owner.getEmail())).with(csrf()).param("subject", "x".repeat(121)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("höchstens 120")));
        assertThat(cases.count()).isEqualTo(count);
        mvc.perform(get("/amt/extra/formular/unbekannt").with(user(owner.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get("/amt/extra").param("page", "-1").with(user(owner.getEmail()))).andExpect(status().isBadRequest());
        mvc.perform(post("/amt/extra/fundbuero").with(user(owner.getEmail())).with(csrf()).param("kind", "LOST").param("subject", "Geduld").param("description", ""))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bitte die Fundumstände")));
        String url = mvc.perform(post("/amt/extra/fundbuero").with(user(owner.getEmail())).with(csrf()).param("kind", "LOST").param("subject", "Geduld").param("description", "Im Referat verschwunden"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(url + "/erledigt").with(user(owner.getEmail())).with(csrf())).andExpect(status().is3xxRedirection());
        String ticket = mvc.perform(post("/amt/extra/wartezimmer").with(user(owner.getEmail())).with(csrf())).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(ticket + "/verlassen").with(user(owner.getEmail())).with(csrf())).andExpect(status().is3xxRedirection());
        preview("form", mvc.perform(get("/amt/extra/formular/spitzname").with(user(owner.getEmail()))).andReturn().getResponse().getContentAsString());
    }
    @Test void concurrentSubmissionsIssueOneDocumentAndConcurrentTicketsReuseOneSeat() throws Exception {
        var form = form();
        assertThat(concurrently(() -> leisure.submit(LeisureKind.NICKNAME, form, owner.getEmail()))).hasSize(1);
        assertThat(concurrently(() -> leisure.takeTicket(owner.getEmail()))).hasSize(1);
        assertThat(leisure.statistics(owner.getEmail()).forms()).isEqualTo(2);
    }
    @Test void caseAndPropertyPaginationKeepOlderEntriesAccessible() {
        for (int i = 0; i < 21; i++) leisure.submit(LeisureKind.CERTIFICATE, form(), owner.getEmail());
        assertThat(leisure.list(owner.getEmail(), 0).getContent()).hasSize(20);
        assertThat(leisure.list(owner.getEmail(), 1).getContent()).hasSize(1);
        assertThat(leisure.list(other.getEmail(), 0).getContent()).isEmpty();
    }
    private LeisureForm form() { var form = new LeisureForm(); form.setSubject("Kartoffelbeauftragte"); form.setExplanation("<script>alert('Kartoffeln')</script>"); form.setActivityId(past.getId()); return form; }
    private LostPropertyForm propertyForm() { var form = new LostPropertyForm(); form.setSubject("Der rote Faden"); form.setDescription("Zuletzt in Referat C gesehen."); return form; }
    private List<Long> unaffectedCounts() { return List.of(events.count(), attendance.count(), emails.count(), absences.count(), travel.count(), history.count(), costs.count()); }
    private Set<Long> concurrently(Callable<Long> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1); Callable<Long> call = () -> { start.await(); return action.call(); };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            return new HashSet<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        }
    }
    private void preview(String name, String html) throws Exception {
        var directory = Path.of("build", "leisure-preview"); Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".html"), html);
    }
}
