package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.costs.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:event-management;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.attendance-mail.enabled=true", "spring.mail.host=example.invalid",
        "app.attendance-mail.from=test@example.test", "app.attendance-mail.base-url=http://localhost",
        "app.attendance-mail.initial-delay=3600000", "app.attendance-penalties.initial-delay=3600000"
})
@AutoConfigureMockMvc
@Import(AttendanceWorkflowIntegrationTests.TimeConfig.class)
class EventManagementIntegrationTests {
    @Autowired EventService events;
    @Autowired ActivityRepository activities;
    @Autowired UserRepository users;
    @Autowired EventChangeEmailRepository emails;
    @Autowired EventChangeMailDelivery delivery;
    @Autowired AttendanceService attendance;
    @Autowired AttendanceRepository sheets;
    @Autowired AttendanceEmailRepository reminders;
    @Autowired AttendancePenaltyRepository marks;
    @Autowired AttendancePenaltyService penalties;
    @Autowired AbsenceApplicationRepository absences;
    @Autowired EventCostRepository costs;
    @Autowired CostService costService;
    @Autowired CalendarService calendar;
    @Autowired PortalService portal;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @Autowired AttendanceWorkflowIntegrationTests.MutableClock clock;
    @MockitoBean JavaMailSender sender;
    AppUser creator, admin, member;
    Activity event;

    @BeforeEach
    void setup() {
        emails.deleteAllInBatch(); reminders.deleteAllInBatch(); marks.deleteAllInBatch(); costs.deleteAll();
        sheets.deleteAll(); absences.deleteAll(); activities.deleteAllInBatch(); users.deleteAllInBatch();
        clock.set(Instant.parse("2026-01-03T12:00:00Z"));
        reset(sender); when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        creator = users.save(new AppUser("Creator", "creator@example.test", "hash"));
        admin = users.save(new AppUser("Admin", "admin@example.test", "hash", true));
        member = users.save(new AppUser("Member", "member@example.test", "hash"));
        jdbc.sql("update app_users set created_at = :date").param("date", OffsetDateTime.parse("2025-01-01T00:00:00Z")).update();
        event = activities.save(new Activity("Dinner", "Alter Ort", "Plan", OffsetDateTime.parse("2026-02-01T18:00:00Z"),
                OffsetDateTime.parse("2026-02-01T20:00:00Z"), creator));
    }

    @Test
    void ownerChangesScheduleLocationAndTextAndQueuesOneSnapshotForEveryMember() {
        var form = events.edit(event.getId(), creator.getEmail());
        form.setDate(LocalDate.of(2026, 2, 2)); form.setEndDate(LocalDate.of(2026, 2, 2));
        form.setTime(LocalTime.of(20, 0)); form.setEndTime(LocalTime.of(22, 0));
        form.setLocation("Neuer Ort"); form.setDescription("Neuer Plan");
        assertThat(events.update(event.getId(), form, creator.getEmail())).isTrue();
        var saved = activities.findById(event.getId()).orElseThrow();
        assertThat(saved.getStartsAt().toInstant()).isEqualTo(Instant.parse("2026-02-02T19:00:00Z"));
        assertThat(saved.getEndsAt().toInstant()).isEqualTo(Instant.parse("2026-02-02T21:00:00Z"));
        assertThat(saved.getLocation()).isEqualTo("Neuer Ort");
        assertThat(emails.findAll()).hasSize(3).allSatisfy(e -> {
            assertThat(e.getRevision()).isEqualTo(1);
            assertThat(e.getBody()).contains("Alter Ort", "Neuer Ort", "Neuer Plan", "Bisher:", "Neu:");
        });
        assertThat(emails.findAll()).extracting(EventChangeEmail::getUserId).containsExactlyInAnyOrder(creator.getId(), admin.getId(), member.getId());
        assertThatThrownBy(() -> events.update(event.getId(), form, creator.getEmail())).hasMessageContaining("409");
        assertThat(events.update(event.getId(), events.edit(event.getId(), creator.getEmail()), creator.getEmail())).isFalse();
        assertThat(emails.count()).isEqualTo(3);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void onlyOwnerOrPersistedAdminCanEditCancelAndPostsRequireCsrf() throws Exception {
        mvc.perform(get("/events/{id}/edit", event.getId())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/events/{id}/edit", event.getId()).with(user(member.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/events/{id}/cancel", event.getId()).with(user(creator.getEmail())).param("revision", "0"))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> events.cancel(event.getId(), 0, "", member.getEmail())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> events.update(event.getId(), new EventForm(), member.getEmail())).isInstanceOf(AccessDeniedException.class);
        var form = events.edit(event.getId(), admin.getEmail()); form.setLocation("Admin-Ort");
        events.update(event.getId(), form, admin.getEmail());
        assertThat(activities.findById(event.getId()).orElseThrow().getLocation()).isEqualTo("Admin-Ort");
        assertThatThrownBy(() -> events.cancel(event.getId(), 0, "", creator.getEmail())).hasMessageContaining("409");
    }

    @Test
    void editAndCancellationRoutesRenderAndPreserveValidationInputs() throws Exception {
        mvc.perform(get("/events/{id}", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Event bearbeiten")));
        mvc.perform(get("/events/{id}/edit", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Alter Ort")));
        mvc.perform(post("/events/{id}/edit", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("name", "Dinner").param("date", "2026-02-03").param("location", "Treffpunkt").param("revision", "0"))
                .andExpect(redirectedUrl("/events/" + event.getId()));
        mvc.perform(post("/events/{id}/edit", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("name", "Dinner").param("date", "2026-02-03").param("location", "x".repeat(201)).param("revision", "1"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("eventForm", "location"));
        mvc.perform(post("/events/{id}/cancel", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("revision", "1").param("reason", "<script>Kein Raum</script>"))
                .andExpect(redirectedUrl("/events/" + event.getId()));
        mvc.perform(get("/events/{id}", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Dieses Event wurde abgesagt")))
                .andExpect(content().string(containsString("&lt;script&gt;Kein Raum&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("Event bearbeiten →"))));
        assertThat(events.cancel(event.getId(), 1, "Noch einmal", creator.getEmail())).isFalse();
        assertThat(emails.count()).isEqualTo(6);
        assertThatThrownBy(() -> events.edit(event.getId(), creator.getEmail())).hasMessageContaining("409");
    }

    @Test
    void cancellationStopsRemindersAndStrikesReversesOnlyEventMarksAndKeepsCosts() {
        event = pastEvent(); record();
        var cost = new CostForm(); cost.setAmount("12"); costService.create(event.getId(), cost, creator.getEmail());
        jdbc.sql("update app_users set tally_count = 7 where id = :id").param("id", member.getId()).update();
        clock.set(Instant.parse("2026-01-09T12:00:00Z")); penalties.reconcile(event.getId());
        assertThat(users.findById(member.getId()).orElseThrow().getTallyCount()).isEqualTo(8);
        events.cancel(event.getId(), 0, "Fand nicht statt", creator.getEmail());
        penalties.reconcile(event.getId()); penalties.reconcile(event.getId());
        assertThat(users.findById(member.getId()).orElseThrow().getTallyCount()).isEqualTo(7);
        assertThat(marks.findByActivityId(event.getId())).allSatisfy(m -> assertThat(m.isApplied()).isFalse());
        assertThat(reminders.findByActivityId(event.getId())).allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(AttendanceEmail.Status.CANCELLED));
        assertThat(attendance.pending(member.getEmail()).reminders()).isEmpty();
        assertThat(attendance.mailReminder(event.getId(), member.getId())).isEmpty();
        assertThatThrownBy(() -> attendance.page(event.getId(), creator.getEmail())).hasMessageContaining("409");
        assertThat(costService.event(event.getId(), creator.getEmail()).totalCents()).isEqualTo(1200);
    }

    @Test
    void cancelledEventsAreLabelledInCalendarAndExcludedFromUpcomingAndAaaOptions() {
        events.cancel(event.getId(), 0, "", admin.getEmail());
        assertThat(calendar.upcoming()).extracting(CalendarService.UpcomingEvent::id).doesNotContain(event.getId());
        assertThat(calendar.load("2026-02", CalendarFilter.ALL).entries()).anySatisfy(e -> assertThat(e.title()).isEqualTo("Abgesagt · Dinner"));
        assertThat(portal.availableActivities(member.getEmail())).isEmpty();
        var form = new AbsenceForm(); form.setActivityId(event.getId()); form.setAcceptedTerms(true);
        assertThatThrownBy(() -> portal.submit(member.getEmail(), form, PortalQuestions.draw())).hasMessageContaining("abgesagt");
    }

    @Test
    void existingAttendanceOrAaaProtectScheduleButLocationChangesRemainPossible() {
        event = pastEvent(); record();
        var form = events.edit(event.getId(), creator.getEmail()); form.setDate(LocalDate.of(2026, 1, 2)); form.setEndDate(LocalDate.of(2026, 1, 2));
        assertThatThrownBy(() -> events.update(event.getId(), form, creator.getEmail())).hasMessageContaining("409");
        var place = events.edit(event.getId(), creator.getEmail()); place.setLocation("Korrigierter Ort");
        assertThat(events.update(event.getId(), place, creator.getEmail())).isTrue();
        var future = activities.save(new Activity("Future", "", "", OffsetDateTime.parse("2026-03-01T18:00:00Z"), OffsetDateTime.parse("2026-03-01T20:00:00Z"), creator));
        absences.save(new AbsenceApplication(member, future, OffsetDateTime.now(clock), List.of(new ApplicationAnswer("A", "Grund", "Reise"))));
        var change = events.edit(future.getId(), creator.getEmail()); change.setTime(LocalTime.of(17, 0));
        assertThatThrownBy(() -> events.update(future.getId(), change, creator.getEmail())).hasMessageContaining("409");
    }

    @Test
    void invalidTimeIntervalsAndNonexistentBerlinTimesAreRejectedAndMultidayEventsArePreserved() {
        var form = events.edit(event.getId(), creator.getEmail());
        form.setDate(LocalDate.of(2026, 3, 29)); form.setEndDate(LocalDate.of(2026, 3, 29)); form.setTime(LocalTime.of(2, 30));
        assertThatThrownBy(() -> events.update(event.getId(), form, creator.getEmail())).hasMessageContaining("400");
        form.setDate(LocalDate.of(2026, 2, 1)); form.setEndDate(LocalDate.of(2026, 2, 1)); form.setTime(LocalTime.of(23, 0)); form.setEndTime(LocalTime.of(20, 0));
        assertThatThrownBy(() -> events.update(event.getId(), form, creator.getEmail())).hasMessageContaining("400");
        form.setEndDate(LocalDate.of(2026, 2, 3));
        events.update(event.getId(), form, creator.getEmail());
        var next = events.edit(event.getId(), creator.getEmail()); next.setLocation("Mehrere Tage");
        events.update(event.getId(), next, creator.getEmail());
        assertThat(activities.findById(event.getId()).orElseThrow().getEndsAt().toInstant()).isEqualTo(Instant.parse("2026-02-03T19:00:00Z"));
    }

    @Test
    void allDayAndOvernightChangesAreDisplayedAndInitialCreationStoresLocation() {
        var form = new EventForm(); form.setName("Ausflug"); form.setDate(LocalDate.of(2026, 4, 1));
        form.setEndDate(LocalDate.of(2026, 4, 3)); form.setLocation("Wald");
        long id = events.create(form, creator.getEmail());
        assertThat(events.update(id, events.edit(id, creator.getEmail()), creator.getEmail())).isFalse();
        assertThat(activities.findById(id).orElseThrow().getLocation()).isEqualTo("Wald");
        assertThat(calendar.load("2026-04", CalendarFilter.ALL).entries()).anySatisfy(e -> assertThat(e.period()).contains("1 Apr", "3 Apr"));
        var overnight = events.edit(id, creator.getEmail()); overnight.setEndDate(null); overnight.setTime(LocalTime.of(23, 0)); overnight.setEndTime(LocalTime.of(2, 0));
        events.update(id, overnight, creator.getEmail());
        assertThat(events.edit(id, creator.getEmail()).getEndDate()).isEqualTo(LocalDate.of(2026, 4, 2));
    }

    @Test
    void smtpUsesStoredChangeDetailsAndRetriesWithoutRepeatedSuccessfulDelivery() throws Exception {
        var form = events.edit(event.getId(), creator.getEmail()); form.setLocation("Neuer Ort"); events.update(event.getId(), form, creator.getEmail());
        long id = emails.findAll().getFirst().getId();
        doThrow(new MailSendException("temporary")).doNothing().when(sender).send(any(MimeMessage.class));
        delivery.deliver(id); delivery.deliver(id);
        assertThat(emails.findById(id).orElseThrow().getAttempts()).isEqualTo(1);
        clock.set(clock.instant().plusSeconds(61)); delivery.deliver(id); delivery.deliver(id);
        assertThat(emails.findById(id).orElseThrow().getStatus()).isEqualTo(EventChangeEmail.Status.SENT);
        var messages = ArgumentCaptor.forClass(MimeMessage.class); verify(sender, times(2)).send(messages.capture());
        var message = messages.getValue();
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getContent().toString()).contains("Alter Ort", "Neuer Ort", "/events/" + event.getId(), "Änderung 1");
    }

    @Test
    void concurrentCancellationAndDeliveryAreIdempotent() throws Exception {
        concurrent(() -> events.cancel(event.getId(), 0, "Kein Raum", creator.getEmail()));
        assertThat(emails.count()).isEqualTo(3);
        long id = emails.findAll().getFirst().getId();
        concurrent(() -> delivery.deliver(id));
        verify(sender).send(any(MimeMessage.class));
        assertThat(emails.findById(id).orElseThrow().getStatus()).isEqualTo(EventChangeEmail.Status.SENT);
    }

    @Test
    void editingLocationPreservesTheLaterOccurrenceOfAnAmbiguousAutumnTime() {
        var overlap = activities.save(new Activity("Zeitumstellung", "Alt", "", OffsetDateTime.parse("2026-10-25T02:30:00+01:00"),
                OffsetDateTime.parse("2026-10-25T04:00:00+01:00"), creator));
        var form = events.edit(overlap.getId(), creator.getEmail()); form.setLocation("Neu");
        events.update(overlap.getId(), form, creator.getEmail());
        assertThat(activities.findById(overlap.getId()).orElseThrow().getStartsAt().toInstant()).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"));
    }

    private Activity pastEvent() { return activities.save(new Activity("Past", "", "", OffsetDateTime.parse("2026-01-01T17:00:00Z"), OffsetDateTime.parse("2026-01-01T19:00:00Z"), creator)); }
    private void record() {
        attendance.save(event.getId(), -1, Set.of(creator.getId(), admin.getId()), creator.getEmail());
        var page = attendance.page(event.getId(), admin.getEmail()); attendance.confirm(event.getId(), page.version(), Set.copyOf(page.recipientIds()), admin.getEmail());
    }
    private void concurrent(Runnable action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> work = () -> { start.await(); action.run(); return null; };
            var a = executor.submit(work); var b = executor.submit(work); start.countDown();
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
    }
}
