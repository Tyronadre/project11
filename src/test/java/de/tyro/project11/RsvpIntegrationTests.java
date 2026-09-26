package de.tyro.project11;

import de.tyro.project11.calendar.*;
import de.tyro.project11.rsvp.*;
import de.tyro.project11.attendance.*;
import de.tyro.project11.costs.*;
import de.tyro.project11.registration.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:rsvp;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.attendance-mail.enabled=false", "app.attendance-mail.initial-delay=3600000", "app.attendance-penalties.initial-delay=3600000"})
@AutoConfigureMockMvc
@Import(AttendanceWorkflowIntegrationTests.TimeConfig.class)
class RsvpIntegrationTests {
    @Autowired RsvpService rsvp;
    @Autowired EventRsvpRepository responses;
    @Autowired EventService events;
    @Autowired EventChangeEmailRepository emails;
    @Autowired ActivityRepository activities;
    @Autowired UserRepository users;
    @Autowired AttendanceRepository sheets;
    @Autowired AttendanceService attendance;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired AttendanceEmailRepository reminders;
    @Autowired AttendancePenaltyRepository marks;
    @Autowired CostService costs;
    @Autowired EventCostRepository requests;
    @Autowired MockMvc mvc;
    @Autowired AttendanceWorkflowIntegrationTests.MutableClock clock;
    AppUser owner, member;
    Activity event;
    @BeforeEach void setup() {
        responses.deleteAll(); requests.deleteAll(); emails.deleteAllInBatch();
        reminders.deleteAllInBatch(); marks.deleteAllInBatch(); sheets.deleteAll();
        activities.deleteAllInBatch(); users.deleteAllInBatch();
        clock.set(Instant.parse("2026-01-03T12:00:00Z"));
        owner = users.save(new AppUser("Owner", "owner@example.test", "hash", true));
        member = users.save(new AppUser("Member", "member@example.test", "hash"));
        event = activities.save(new Activity("Dinner", "", "", OffsetDateTime.parse("2026-02-01T18:00:00Z"), OffsetDateTime.parse("2026-02-01T20:00:00Z"), owner));
    }
    @Test void threeAnswersCanBeChangedWithoutAffectingAttendancePenaltiesOrCosts() {
        assertThat(page(owner).groups().get(3).members()).hasSize(2);
        var form = new CostForm(); form.setAmount("10"); costs.create(event.getId(), form, owner.getEmail());
        respond(member, EventRsvp.Answer.YES);
        assertThat(page(owner).groups().getFirst().members()).extracting(RsvpService.Member::id).containsExactly(member.getId());
        respond(member, EventRsvp.Answer.MAYBE);
        respond(owner, EventRsvp.Answer.NO);
        assertThat(page(member).mine()).isEqualTo("MAYBE");
        assertThat(page(owner).groups().get(1).members()).hasSize(1);
        assertThat(page(owner).groups().get(2).members()).hasSize(1);
        assertThat(responses.count()).isEqualTo(2);
        assertThat(sheets.count()).isZero(); assertThat(marks.count()).isZero(); assertThat(reminders.count()).isZero();
        assertThat(costs.event(event.getId(), owner.getEmail()).requests().getFirst().allocated()).isFalse();
        assertThat(users.findById(member.getId()).orElseThrow().getTallyCount()).isZero();
    }
    @Test void postIsAuthenticatedCsrfProtectedAndAlwaysAppliesToSignedInUser() throws Exception {
        mvc.perform(post("/events/{id}/rsvp", event.getId()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/events/{id}/rsvp", event.getId()).with(user(member.getEmail()))) .andExpect(status().isForbidden());
        mvc.perform(post("/events/{id}/rsvp", event.getId()).with(user(member.getEmail())).with(csrf())
                        .param("answer", "YES").param("version", "-1").param("eventRevision", "0").param("userId", owner.getId().toString()))
                .andExpect(redirectedUrl("/events/" + event.getId() + "#rsvp"));
        assertThat(page(owner).mine()).isEmpty(); assertThat(page(member).mine()).isEqualTo("YES");
        mvc.perform(get("/events/{id}", event.getId()).with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Wer ist dabei?")))
                .andExpect(content().string(containsString("aria-pressed=\"true\"")));
        mvc.perform(post("/events/{id}/rsvp", event.getId()).with(user(member.getEmail())).with(csrf())
                        .param("answer", "INVALID").param("version", "0").param("eventRevision", "0"))
                .andExpect(status().isBadRequest());
        assertThat(responses.count()).isEqualTo(1);
    }
    @Test void cutoffIsExactlyEventStartAndCancellationPreventsResponses() {
        clock.set(event.getStartsAt().toInstant().minusNanos(1)); respond(member, EventRsvp.Answer.YES);
        clock.set(event.getStartsAt().toInstant());
        assertThat(page(member).open()).isFalse();
        assertThatThrownBy(() -> respond(member, EventRsvp.Answer.NO)).hasMessageContaining("409");
        assertThat(page(member).mine()).isEqualTo("YES");
        clock.set(Instant.parse("2026-01-03T12:00:00Z")); events.cancel(event.getId(), 0, "", owner.getEmail());
        assertThat(page(owner).open()).isFalse();
        assertThatThrownBy(() -> respond(owner, EventRsvp.Answer.MAYBE)).hasMessageContaining("409");
    }
    @Test void allDayEventsCloseAtBerlinMidnight() {
        var form = new EventForm(); form.setName("Ausflug"); form.setDate(LocalDate.of(2026, 7, 1));
        long id = events.create(form, owner.getEmail());
        clock.set(Instant.parse("2026-06-30T21:59:59Z"));
        assertThat(rsvp.load(id, member.getEmail()).open()).isTrue();
        clock.set(Instant.parse("2026-06-30T22:00:00Z"));
        assertThat(rsvp.load(id, member.getEmail()).open()).isFalse();
    }
    @Test void reschedulingRequiresReconfirmationButLocationChangesKeepAnswers() {
        respond(member, EventRsvp.Answer.YES);
        var form = events.edit(event.getId(), owner.getEmail()); form.setLocation("Neuer Ort"); events.update(event.getId(), form, owner.getEmail());
        assertThat(page(member).mine()).isEqualTo("YES");
        form = events.edit(event.getId(), owner.getEmail()); form.setDate(LocalDate.of(2026, 2, 2)); form.setEndDate(LocalDate.of(2026, 2, 2));
        events.update(event.getId(), form, owner.getEmail());
        assertThat(page(member).outdated()).isTrue(); assertThat(page(member).mine()).isEmpty();
        assertThat(page(owner).groups().getFirst().members()).isEmpty();
        assertThat(page(owner).groups().get(3).members()).hasSize(2);
        respond(member, EventRsvp.Answer.YES);
        assertThat(page(member).outdated()).isFalse(); assertThat(page(member).mine()).isEqualTo("YES");
    }
    @Test void staleEventAndResponseFormsCannotOverwriteNewDecisions() {
        respond(member, EventRsvp.Answer.YES);
        assertThatThrownBy(() -> rsvp.respond(event.getId(), EventRsvp.Answer.NO, -1, 0, member.getEmail())).hasMessageContaining("409");
        var form = events.edit(event.getId(), owner.getEmail()); form.setLocation("Neu"); events.update(event.getId(), form, owner.getEmail());
        assertThatThrownBy(() -> rsvp.respond(event.getId(), EventRsvp.Answer.NO, 0, 0, member.getEmail())).hasMessageContaining("409");
        assertThat(page(member).mine()).isEqualTo("YES");
    }
    @Test void concurrentRepeatedResponsesProduceOnlyOneRow() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> work = () -> { start.await(); rsvp.respond(event.getId(), EventRsvp.Answer.YES, -1, 0, member.getEmail()); return null; };
            var a = pool.submit(work); var b = pool.submit(work); start.countDown();
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        assertThat(responses.count()).isEqualTo(1);
    }
    @Test void actualAttendanceCanDifferFromPlansAndAloneDeterminesCostShares() {
        jdbc.sql("update app_users set created_at = :date").param("date", OffsetDateTime.parse("2025-01-01T00:00:00Z")).update();
        respond(owner, EventRsvp.Answer.YES); respond(member, EventRsvp.Answer.NO);
        var cost = new CostForm(); cost.setAmount("10"); costs.create(event.getId(), cost, owner.getEmail());
        clock.set(Instant.parse("2026-02-02T12:00:00Z"));
        attendance.save(event.getId(), -1, java.util.Set.of(member.getId()), owner.getEmail());
        var sheet = attendance.page(event.getId(), owner.getEmail());
        attendance.confirm(event.getId(), sheet.version(), java.util.Set.copyOf(sheet.recipientIds()), owner.getEmail());
        assertThat(costs.event(event.getId(), owner.getEmail()).requests().getFirst().shares())
                .extracting(CostService.Share::userId).containsExactly(member.getId());
        assertThat(page(owner).mine()).isEqualTo("YES"); assertThat(page(member).mine()).isEqualTo("NO");
    }

    private RsvpService.Page page(AppUser user) { return rsvp.load(event.getId(), user.getEmail()); }
    private void respond(AppUser user, EventRsvp.Answer answer) {
        var page = page(user); rsvp.respond(event.getId(), answer, page.version(), page.eventRevision(), user.getEmail());
    }
}
