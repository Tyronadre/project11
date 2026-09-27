package de.tyro.project11;
import de.tyro.project11.polls.*;
import de.tyro.project11.calendar.ActivityRepository;
import de.tyro.project11.registration.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:poll-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PollIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired PollService polls;
    @Autowired PollRepository repository;
    @Autowired UserRepository users;
    @Autowired ActivityRepository activities;
    String owner;
    String member;
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        owner = "owner" + suffix + "@example.com"; member = "member" + suffix + "@example.com";
        users.save(new AppUser("Owner", owner, "unused"));
        users.save(new AppUser("Member", member, "unused"));
    }
    PollForm form() {
        var f = new PollForm(); f.setTitle("Gemeinsam essen"); f.setLocation("Darmstadt");
        f.setDescription("Ein schöner Abend"); f.setSlots(List.of("2090-06-12T18:00", "2090-06-13T19:00"));
        return f;
    }
    @Test void rendersCreatesAndUpdatesOwnBallot() throws Exception {
        mvc.perform(get("/polls")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/polls/new").with(user(owner))).andExpect(status().isOk());
        long id = polls.create(form(), owner);
        mvc.perform(get("/polls").with(user(member))).andExpect(status().isOk()).andExpect(content().string(containsString("Gemeinsam essen")));
        mvc.perform(post("/polls/" + id + "/vote").with(user(member)).with(csrf()).param("slots", "0", "1"))
                .andExpect(status().is3xxRedirection());
        assertThat(polls.load(id, member).slots()).allMatch(s -> s.votes() == 1 && s.selected());
        mvc.perform(get("/polls/" + id).with(user(owner))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Event erstellen")));
        polls.vote(id, Set.of(1), member);
        assertThat(polls.load(id, member).responses()).isEqualTo(1);
        assertThat(polls.load(id, member).slots().getFirst().votes()).isZero();
        polls.vote(id, Set.of(), member);
        assertThat(polls.load(id, member).answered()).isTrue();
        assertThat(polls.load(id, member).slots()).allMatch(s -> s.votes() == 0);
    }
    @Test void enforcesCsrfOwnershipAndClosedState() throws Exception {
        long id = polls.create(form(), owner);
        mvc.perform(post("/polls/" + id + "/vote").with(user(member)).param("slots", "0")).andExpect(status().isForbidden());
        mvc.perform(post("/polls/" + id + "/vote").with(user(member)).with(csrf()).param("slots", "8")).andExpect(status().isBadRequest());
        mvc.perform(post("/polls/" + id + "/finish").with(user(member)).with(csrf()).param("slot", "0")).andExpect(status().isForbidden());
        long event = polls.finish(id, 1, owner);
        assertThat(polls.finish(id, 0, owner)).isEqualTo(event);
        var created = activities.findById(event).orElseThrow();
        assertThat(created.getTitle()).isEqualTo("Gemeinsam essen");
        assertThat(created.getLocation()).isEqualTo("Darmstadt");
        assertThat(created.getStartsAt().getDayOfMonth()).isEqualTo(13);
        assertThat(java.time.Duration.between(created.getStartsAt(), created.getEndsAt()).toMinutes()).isEqualTo(120);
        mvc.perform(post("/polls/" + id + "/vote").with(user(member)).with(csrf()).param("slots", "0")).andExpect(status().isConflict());
        mvc.perform(get("/polls/" + id).with(user(member))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Geplantes Event öffnen")));
    }
    @Test void validatesDatesAndPreservesInvalidForm() throws Exception {
        mvc.perform(post("/polls").with(user(owner)).with(csrf()).param("title", "Test")
                .param("slots[0]", "2090-06-12T18:00").param("slots[1]", "2090-06-12T18:00"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("unterschiedliche Termine")));
        var invalid = form(); invalid.setSlots(List.of("2020-01-01T12:00", "2090-06-12T18:00"));
        assertThatThrownBy(() -> polls.create(invalid, owner)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        mvc.perform(post("/polls").with(user(owner)).with(csrf()).param("title", "Valid")
                .param("slots[0]", "2090-06-12T18:00").param("slots[1]", "2090-06-13T18:00"))
                .andExpect(status().is3xxRedirection());
    }
    @Test void concurrentCompletionCreatesOneEvent() throws Exception {
        long id = polls.create(form(), owner);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> finish = () -> { start.await(); return polls.finish(id, 0, owner); };
            var first = executor.submit(finish); var second = executor.submit(finish); start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @Test void exposesChosenDateAndUnavailableResponses() throws Exception {
        long id = polls.create(form(), owner);
        polls.vote(id, Set.of(), member);
        assertThat(polls.load(id, owner).unavailable()).isEqualTo(1);
        polls.finish(id, 1, owner);
        var view = polls.load(id, member);
        assertThat(view.votingOpen()).isFalse();
        assertThat(view.slots().get(1).chosen()).isTrue();
        assertThat(view.slots().get(0).chosen()).isFalse();
        mvc.perform(get("/polls/" + id).with(user(member))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Als Event festgelegt")));
    }
    @Test void expiredPollsCannotBeVotedOnOrFinalized() throws Exception {
        var old = new DatePoll(form(), List.of(java.time.LocalDateTime.of(2020, 1, 1, 18, 0),
                java.time.LocalDateTime.of(2020, 1, 2, 18, 0)), users.findByEmail(owner).orElseThrow());
        long id = repository.save(old).getId();
        assertThat(polls.load(id, member).votingOpen()).isFalse();
        assertThat(polls.load(id, member).slots()).allMatch(PollService.Slot::expired);
        mvc.perform(get("/polls/" + id).with(user(member))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Alle Vorschläge sind vergangen")));
        mvc.perform(post("/polls/" + id + "/vote").with(user(member)).with(csrf()).param("slots", "0"))
                .andExpect(status().isConflict());
        mvc.perform(post("/polls/" + id + "/finish").with(user(owner)).with(csrf()).param("slot", "0"))
                .andExpect(status().isBadRequest());
    }
    @Test void adminCanFinishAnOvernightEventForOriginalOwner() throws Exception {
        var admin = users.save(new AppUser("Admin", "admin" + UUID.randomUUID() + "@example.com", "unused", true));
        var f = form(); f.setSlots(List.of("2090-06-12T23:00", "2090-06-13T23:00"));
        long id = polls.create(f, owner);
        long eventId = polls.finish(id, 0, admin.getEmail());
        var event = activities.findById(eventId).orElseThrow();
        assertThat(event.getEndsAt().atZoneSameInstant(de.tyro.project11.calendar.CalendarTime.BERLIN).toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2090, 6, 13));
        assertThat(event.getCreatedBy().getId()).isEqualTo(users.findByEmail(owner).orElseThrow().getId());
        mvc.perform(post("/polls/" + id + "/finish").with(user(owner)).param("slot", "0"))
                .andExpect(status().isForbidden());
    }
    @Test void rejectsClockChangesAndInvalidDurations() {
        var transition = de.tyro.project11.calendar.CalendarTime.BERLIN.getRules()
                .nextTransition(java.time.Instant.parse("2090-01-01T00:00:00Z"));
        var clockChange = form();
        clockChange.setSlots(List.of(transition.getDateTimeBefore().minusMinutes(30).toString(), "2090-06-13T18:00"));
        assertThatThrownBy(() -> polls.create(clockChange, owner))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var f = form(); f.setDurationMinutes(0);
        var invalidDuration = f;
        assertThatThrownBy(() -> polls.create(invalidDuration, owner))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void concurrentMembersKeepBothBallots() throws Exception {
        long id = polls.create(form(), owner);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); polls.vote(id, Set.of(0), owner); return true; });
            var second = executor.submit(() -> { start.await(); polls.vote(id, Set.of(1), member); return true; });
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(polls.load(id, owner).responses()).isEqualTo(2);
        assertThat(polls.load(id, owner).slots()).allMatch(slot -> slot.votes() == 1);
    }
    @Test void validationRemainsGermanForEnglishBrowserLanguage() throws Exception {
        mvc.perform(post("/polls").with(user(owner)).with(csrf())
                .header("Accept-Language", "en-US,en;q=0.9")
                .param("title", "").param("durationMinutes", "120")
                .param("slots[0]", "2090-06-12T18:00").param("slots[1]", "2090-06-13T18:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"de\"")))
                .andExpect(content().string(containsString("darf nicht leer sein")));
    }
    @Test void closeWithoutEventPreservesVotesAndBlocksFurtherChanges() throws Exception {
        long id = polls.create(form(), owner);
        polls.vote(id, Set.of(0), member);
        long before = activities.count();
        mvc.perform(post("/polls/" + id + "/close").with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(post("/polls/" + id + "/close").with(user(member)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/polls/" + id + "/close").with(user(owner)).with(csrf()).param("reason", "Kein Termin passt"))
                .andExpect(status().is3xxRedirection());
        polls.close(id, "Wiederholung", owner);
        var view = polls.load(id, member);
        assertThat(view.closedWithoutEvent()).isTrue();
        assertThat(view.votingOpen()).isFalse();
        assertThat(view.closeReason()).isEqualTo("Kein Termin passt");
        assertThat(view.slots().getFirst().votes()).isEqualTo(1);
        assertThat(activities.count()).isEqualTo(before);
        mvc.perform(get("/polls/" + id).with(user(member))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ohne Ergebnis geschlossen")));
        mvc.perform(post("/polls/" + id + "/finish").with(user(owner)).with(csrf()).param("slot", "0")).andExpect(status().isConflict());
        mvc.perform(post("/polls/" + id + "/vote").with(user(member)).with(csrf()).param("slots", "1")).andExpect(status().isConflict());
    }
    @Test void concurrentCloseAndFinishHaveOnlyOneOutcome() throws Exception {
        long id = polls.create(form(), owner);
        long before = activities.count();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var close = executor.submit(() -> { start.await(); try { polls.close(id, "Kein Treffen", owner); return true; }
                catch (org.springframework.web.server.ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(409); return false; } });
            var finish = executor.submit(() -> { start.await(); try { polls.finish(id, 0, owner); return true; }
                catch (org.springframework.web.server.ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(409); return false; } });
            start.countDown();
            assertThat(close.get(10, java.util.concurrent.TimeUnit.SECONDS)).isNotEqualTo(finish.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        var view = polls.load(id, owner);
        assertThat(activities.count() - before).isEqualTo(view.closedWithoutEvent() ? 0 : 1);
    }
}
