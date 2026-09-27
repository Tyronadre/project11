package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendance-workflow;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.attendance-mail.enabled=true", "spring.mail.host=example.invalid",
        "app.attendance-mail.from=test@example.test", "app.attendance-mail.base-url=http://localhost",
        "app.attendance-mail.initial-delay=3600000", "app.attendance-penalties.initial-delay=3600000"
})
@AutoConfigureMockMvc
class AttendanceWorkflowIntegrationTests {
    @Autowired AttendanceService attendance;
    @Autowired AttendancePenaltyService penalties;
    @Autowired AttendancePenaltyScheduler scheduler;
    @Autowired AttendanceMailDelivery delivery;
    @Autowired AttendanceRules rules;
    @Autowired AbsenceReviewService reviews;
    @Autowired PortalService portal;
    @Autowired AttendanceRepository sheets;
    @Autowired AttendanceEmailRepository reminders;
    @Autowired AttendancePenaltyRepository marks;
    @Autowired AbsenceDecisionEmailRepository decisions;
    @Autowired AbsenceApplicationRepository applications;
    @Autowired ActivityRepository activities;
    @Autowired HolidayRepository holidays;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @MockitoBean JavaMailSender sender;
    AppUser admin, creator, absent, vacation;
    Activity event;
    static final Instant DEADLINE = Instant.parse("2026-01-07T23:00:00Z"); // 8 January, midnight Berlin

    @BeforeEach
    void setup() {
        decisions.deleteAllInBatch(); reminders.deleteAllInBatch(); marks.deleteAllInBatch();
        sheets.deleteAll(); applications.deleteAll(); holidays.deleteAllInBatch();
        activities.deleteAllInBatch(); users.deleteAllInBatch();
        clock.set(Instant.parse("2026-01-03T12:00:00Z"));
        reset(sender);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        admin = users.save(new AppUser("Admin", "admin@example.test", "hash", true));
        creator = users.save(new AppUser("Creator", "creator@example.test", "hash"));
        absent = users.save(new AppUser("Absent", "absent@example.test", "hash"));
        vacation = users.save(new AppUser("Vacation", "vacation@example.test", "hash"));
        jdbc.sql("update app_users set created_at = :created").param("created", OffsetDateTime.parse("2025-01-01T00:00:00Z")).update();
        event = newEvent("Neujahr", "2026-01-01T17:00:00Z", "2026-01-01T19:00:00Z");
        holidays.save(new Holiday(vacation, "Urlaub", "", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), OffsetDateTime.parse("2025-12-01T00:00:00Z")));
    }

    @Test
    void creatorSavesDraftButOnlyDatabaseAdminCanConfirmExactRecipients() throws Exception {
        users.save(new AppUser("Joined later", "later@example.test", "hash"));
        mvc.perform(get("/events/{id}/attendance", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk());
        mvc.perform(post("/events/{id}/attendance", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("version", "-1").param("attendeeIds", admin.getId().toString(), creator.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(reminders.count()).isZero();
        var page = attendance.page(event.getId(), admin.getEmail());
        assertThat(page.members()).hasSize(4);
        assertThat(page.recipientIds()).containsExactly(absent.getId());
        assertThat(page.members().stream().filter(m -> m.id() == vacation.getId()).findFirst().orElseThrow().notification()).contains("Durch Urlaub abgedeckt");
        mvc.perform(get("/events/{id}/attendance", event.getId()).with(user(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Empfänger prüfen und bestätigen")))
                .andExpect(content().string(containsString(absent.getEmail())));
        mvc.perform(get("/events/{id}/attendance", event.getId()).with(user(absent.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/events/{id}/attendance/confirm", event.getId()).with(user(creator.getEmail()).roles("ADMIN")).with(csrf())
                        .param("version", Long.toString(page.version())).param("recipientIds", absent.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/events/{id}/attendance/confirm", event.getId()).with(user(admin.getEmail()))
                        .param("version", Long.toString(page.version())).param("recipientIds", absent.getId().toString()))
                .andExpect(status().isForbidden());
        confirm(event);
        assertThat(reminders.findByActivityId(event.getId())).singleElement().satisfies(e -> assertThat(e.getUserId()).isEqualTo(absent.getId()));
        verify(sender, never()).send(any(MimeMessage.class));
        assertThat(tally(absent)).isZero();
    }

    @Test
    void changedRecipientSnapshotAndStaleAttendanceAreRejected() {
        save(event); var page = attendance.page(event.getId(), admin.getEmail());
        application(absent, event, OffsetDateTime.now(clock));
        assertThatThrownBy(() -> attendance.confirm(event.getId(), page.version(), Set.copyOf(page.recipientIds()), admin.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(reminders.count()).isZero();
        confirm(event);
        assertThatThrownBy(() -> attendance.save(event.getId(), page.version(), Set.of(), creator.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(attendance.page(event.getId(), admin.getEmail()).confirmed()).isTrue();
    }

    @Test
    void reminderIsSentOnceAcrossRepeatedConfirmationsAndCorrections() throws Exception {
        save(event); confirm(event); confirm(event);
        var email = reminders.findByActivityId(event.getId()).getFirst();
        delivery.deliver(email.getId(), event.getId()); delivery.deliver(email.getId(), event.getId());
        save(event); confirm(event);
        delivery.deliver(email.getId(), event.getId());
        assertThat(reminders.count()).isEqualTo(1);
        assertThat(reminders.findById(email.getId()).orElseThrow().getStatus()).isEqualTo(AttendanceEmail.Status.SENT);
        var message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getAllRecipients()[0].toString()).isEqualTo(absent.getEmail());
        assertThat(message.getValue().getContent().toString()).contains("/amt/aaa?activity=" + event.getId(), "8 Jan. 2026");
    }

    @Test
    void editsCancelQueuedMailAndDeliveryRechecksNewExcusesAndDeadline() {
        save(event); confirm(event);
        long emailId = reminders.findByActivityId(event.getId()).getFirst().getId();
        save(event);
        delivery.deliver(emailId, event.getId());
        assertThat(reminders.findById(emailId).orElseThrow().getStatus()).isEqualTo(AttendanceEmail.Status.CANCELLED);
        confirm(event);
        application(absent, event, OffsetDateTime.now(clock));
        delivery.deliver(emailId, event.getId());
        assertThat(reminders.findById(emailId).orElseThrow().getStatus()).isEqualTo(AttendanceEmail.Status.CANCELLED);
        applications.deleteAll(); save(event); confirm(event);
        clock.set(DEADLINE);
        delivery.deliver(emailId, event.getId());
        assertThat(reminders.findById(emailId).orElseThrow().getStatus()).isEqualTo(AttendanceEmail.Status.CANCELLED);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void marksBecomeDueExactlyOnEighthDayAndCatchUpOnceAfterDowntime() {
        save(event); confirm(event);
        jdbc.sql("update app_users set tally_count = 7 where id = :id").param("id", absent.getId()).update();
        clock.set(DEADLINE.minusNanos(1)); scheduler.processDue();
        assertThat(tally(absent)).isEqualTo(7);
        clock.set(DEADLINE); scheduler.processDue(); scheduler.processDue();
        assertThat(tally(absent)).isEqualTo(8);
        assertThat(tally(vacation)).isZero(); assertThat(tally(creator)).isZero();
        assertThat(marks.count()).isEqualTo(1);
        clock.set(DEADLINE.plus(Duration.ofDays(30))); scheduler.processDue();
        assertThat(tally(absent)).isEqualTo(8);
        assertThat(attendance.pending(absent.getEmail()).counts()).doesNotContainKey(absent.getId());
    }

    @Test
    void unconfirmedListsNeverApplyMarksAndConfirmedCorrectionsReverseOnlyOnce() {
        save(event); clock.set(DEADLINE); scheduler.processDue();
        assertThat(tally(absent)).isZero();
        confirm(event); assertThat(tally(absent)).isEqualTo(1);
        attendance.save(event.getId(), attendance.page(event.getId(), admin.getEmail()).version(),
                Set.of(admin.getId(), creator.getId(), absent.getId()), creator.getEmail());
        scheduler.processDue(); assertThat(tally(absent)).isEqualTo(1);
        confirm(event); scheduler.processDue(); confirm(event);
        assertThat(tally(absent)).isZero();
        assertThat(marks.findByActivityId(event.getId())).singleElement().satisfies(m -> assertThat(m.isApplied()).isFalse());
        assertThat(reminders.count()).isZero();
    }

    @Test
    void timelyPendingApplicationHoldsMarkAndAcceptanceEmailsOnce() throws Exception {
        save(event); confirm(event);
        var application = application(absent, event, OffsetDateTime.now(clock));
        clock.set(DEADLINE); scheduler.processDue(); assertThat(tally(absent)).isZero();
        reviews.decide(application.getId(), AbsenceApplication.Decision.ACCEPTED, "Alles in Ordnung", admin.getEmail());
        reviews.decide(application.getId(), AbsenceApplication.Decision.ACCEPTED, "Doppelklick", admin.getEmail());
        scheduler.processDue(); assertThat(tally(absent)).isZero();
        assertThat(decisions.count()).isEqualTo(1);
        delivery.deliverDecision(application.getId(), event.getId()); delivery.deliverDecision(application.getId(), event.getId());
        var message = ArgumentCaptor.forClass(MimeMessage.class); verify(sender).send(message.capture());
        assertThat(message.getValue().getSubject()).contains("angenommen");
        assertThat(message.getValue().getContent().toString()).contains("Alles in Ordnung");
        assertThat(decisions.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(AbsenceDecisionEmail.Status.SENT);
    }

    @Test
    void rejectionAfterDeadlineAppliesMarkAndNotifiesApplicant() throws Exception {
        save(event); confirm(event); var application = application(absent, event, OffsetDateTime.now(clock));
        clock.set(DEADLINE);
        assertThatThrownBy(() -> reviews.decide(application.getId(), AbsenceApplication.Decision.REJECTED, "", admin.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        reviews.decide(application.getId(), AbsenceApplication.Decision.REJECTED, "Begründung reicht nicht aus", admin.getEmail());
        scheduler.processDue(); assertThat(tally(absent)).isEqualTo(1);
        assertThatThrownBy(() -> reviews.decide(application.getId(), AbsenceApplication.Decision.ACCEPTED, "", admin.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        delivery.deliverDecision(application.getId(), event.getId());
        var message = ArgumentCaptor.forClass(MimeMessage.class); verify(sender).send(message.capture());
        assertThat(message.getValue().getSubject()).contains("abgelehnt");
        assertThat(message.getValue().getContent().toString()).contains("Begründung reicht nicht aus");
    }

    @Test
    void rejectionBeforeDeadlineWaitsAndHolidayStillExemptsRejectedApplicant() {
        save(event); confirm(event);
        var application = application(absent, event, OffsetDateTime.now(clock));
        reviews.decide(application.getId(), AbsenceApplication.Decision.REJECTED, "Kein ausreichender Grund", admin.getEmail());
        assertThat(tally(absent)).isZero();
        clock.set(DEADLINE); scheduler.processDue(); assertThat(tally(absent)).isEqualTo(1);
        // A holiday already filed on time remains an independent exemption, even with a rejected AaA.
        var vacationApplication = application(vacation, event, OffsetDateTime.parse("2026-01-02T12:00:00Z"));
        reviews.decide(vacationApplication.getId(), AbsenceApplication.Decision.REJECTED, "Unvollständig", admin.getEmail());
        scheduler.processDue(); assertThat(tally(vacation)).isZero();
    }

    @Test
    void filingBoundaryIsEnforcedAndLegacyLateApplicationsAreVoid() {
        clock.set(DEADLINE.minusMillis(1));
        long id = portal.submit(absent.getEmail(), form(event), PortalQuestions.draw());
        assertThat(applications.findById(id)).isPresent();
        clock.set(DEADLINE);
        assertThatThrownBy(() -> portal.submit(creator.getEmail(), form(event), PortalQuestions.draw()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("abgelaufen");
        assertThat(applications.count()).isEqualTo(1);
        assertThat(portal.submit(absent.getEmail(), form(event), PortalQuestions.draw())).isEqualTo(id);
        applications.deleteAll();
        var late = application(absent, event, OffsetDateTime.now(clock));
        save(event); confirm(event); assertThat(tally(absent)).isEqualTo(1);
        assertThatThrownBy(() -> reviews.decide(late.getId(), AbsenceApplication.Decision.ACCEPTED, "", admin.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nichtig");
    }

    @Test
    void allDayAndDstDeadlinesUseStartDateAndAdjacentHolidaysCoverWholeEvent() {
        var allDay = newEvent("Ganztägig", "2026-01-01T00:00:00+01:00", "2026-01-02T00:00:00+01:00");
        assertThat(AttendanceRules.deadline(allDay).toInstant()).isEqualTo(DEADLINE);
        var dst = newEvent("Zeitumstellung", "2026-03-28T20:00:00+01:00", "2026-03-28T22:00:00+01:00");
        assertThat(AttendanceRules.deadline(dst).toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-04-04T00:00:00+02:00"));
        var overnight = newEvent("Übernachtung", "2026-01-01T20:00:00Z", "2026-01-02T09:00:00Z");
        assertThat(rules.holidayMembers(overnight)).doesNotContain(vacation.getId());
        holidays.save(new Holiday(vacation, "Verlängert", "", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2), OffsetDateTime.now(clock)));
        assertThat(rules.holidayMembers(overnight)).contains(vacation.getId());
        holidays.save(new Holiday(absent, "Zu spät", "", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), OffsetDateTime.ofInstant(DEADLINE, ZoneOffset.UTC)));
        assertThat(rules.holidayMembers(overnight)).doesNotContain(absent.getId());
    }

    @Test
    void adminsCanReviewAnswersAndPortalShowsRecordedDecision() throws Exception {
        var application = application(absent, event, OffsetDateTime.now(clock));
        mvc.perform(get("/admin/aaa").with(user(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Neujahr")));
        mvc.perform(get("/admin/aaa/{id}", application.getId()).with(user(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Testbegründung")));
        mvc.perform(get("/admin/aaa").with(user(creator.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/aaa/{id}", application.getId()).with(user(admin.getEmail())).param("decision", "ACCEPTED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/aaa/{id}", application.getId()).with(user(admin.getEmail())).with(csrf()).param("decision", "REJECTED"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Begründung erforderlich")));
        assertThatThrownBy(() -> reviews.decide(application.getId(), AbsenceApplication.Decision.ACCEPTED, "", creator.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
        mvc.perform(post("/admin/aaa/{id}", application.getId()).with(user(admin.getEmail())).with(csrf()).param("decision", "ACCEPTED"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/aaa/{id}", application.getId()).with(user(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Angenommen")));
        clock.set(DEADLINE.plus(Duration.ofDays(90)));
        mvc.perform(get("/amt/antraege/{id}", application.getId()).with(user(absent.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bearbeitungsverlauf")))
                .andExpect(content().string(containsString("Bescheid öffnen / drucken")));
    }

    @Test
    void failedDecisionEmailRetriesWithoutRepeatingSuccessfulDelivery() {
        var application = application(absent, event, OffsetDateTime.now(clock));
        reviews.decide(application.getId(), AbsenceApplication.Decision.ACCEPTED, "", admin.getEmail());
        doThrow(new MailSendException("Simulated outage")).doNothing().when(sender).send(any(MimeMessage.class));
        delivery.deliverDecision(application.getId(), event.getId());
        delivery.deliverDecision(application.getId(), event.getId());
        assertThat(decisions.findById(application.getId()).orElseThrow().getAttempts()).isEqualTo(1);
        clock.set(clock.instant().plusSeconds(60));
        delivery.deliverDecision(application.getId(), event.getId()); delivery.deliverDecision(application.getId(), event.getId());
        assertThat(decisions.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(AbsenceDecisionEmail.Status.SENT);
        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void concurrentSettlementsDoNotDuplicateOrLoseMarksAcrossEvents() throws Exception {
        var second = newEvent("Zweites Event", "2026-01-01T20:00:00Z", "2026-01-01T21:00:00Z");
        save(event); confirm(event); save(second); confirm(second); clock.set(DEADLINE);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (long id : List.of(event.getId(), second.getId(), event.getId(), second.getId())) {
                futures.add(executor.submit(() -> { start.await(); penalties.reconcile(id); return null; }));
            }
            start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertThat(tally(absent)).isEqualTo(2); assertThat(marks.count()).isEqualTo(2);
    }

    @Test
    void concurrentWorkersSendEachReminderAndDecisionOnlyOnce() throws Exception {
        save(event); confirm(event);
        var reminder = reminders.findByActivityId(event.getId()).getFirst();
        concurrently(() -> delivery.deliver(reminder.getId(), event.getId()));
        verify(sender).send(any(MimeMessage.class));
        var application = application(absent, event, OffsetDateTime.now(clock));
        concurrently(() -> reviews.decide(application.getId(), AbsenceApplication.Decision.ACCEPTED, "", admin.getEmail()));
        assertThat(decisions.count()).isEqualTo(1);
        concurrently(() -> delivery.deliverDecision(application.getId(), event.getId()));
        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    private void concurrently(Runnable operation) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> action = () -> { start.await(); operation.run(); return null; };
            var first = executor.submit(action); var second = executor.submit(action);
            start.countDown(); first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
    }

    private void save(Activity activity) {
        attendance.save(activity.getId(), attendance.page(activity.getId(), creator.getEmail()).version(),
                Set.of(admin.getId(), creator.getId()), creator.getEmail());
    }
    private void confirm(Activity activity) {
        var page = attendance.page(activity.getId(), admin.getEmail());
        attendance.confirm(activity.getId(), page.version(), Set.copyOf(page.recipientIds()), admin.getEmail());
    }
    private int tally(AppUser user) { return users.findById(user.getId()).orElseThrow().getTallyCount(); }
    private Activity newEvent(String title, String start, String end) {
        return activities.save(new Activity(title, "Darmstadt", "", OffsetDateTime.parse(start), OffsetDateTime.parse(end), creator));
    }
    private AbsenceApplication application(AppUser applicant, Activity activity, OffsetDateTime at) {
        return applications.save(new AbsenceApplication(applicant, activity, at, List.of(new ApplicationAnswer("A", "Grund", "Testbegründung"))));
    }
    private AbsenceForm form(Activity activity) {
        var form = new AbsenceForm(); form.setActivityId(activity.getId()); form.setActivityDate(LocalDate.of(2026, 1, 1));
        form.setApplicantName("Applicant"); form.setDestination("Darmstadt"); form.setCompanions("Keine"); form.setReason("Grund");
        form.setTransport("Bus"); form.setCatering("Pasta"); form.setPriorityReason("Begründung"); form.setDetailedReason("Ausführliche Begründung ".repeat(4));
        form.setPersonalAnswers(List.of("A", "B", "C")); form.setKnowledgeAnswer("Antwort"); form.setAbsurdAnswer("Ausführliche Antwort");
        form.setLoyaltyAnswer("Sehr loyal"); form.setAuditAnswer("Fusilli"); form.setAcceptedTerms(true);
        return form;
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time;
        private final ZoneId zone;
        MutableClock() { this(new AtomicReference<>(Instant.parse("2026-01-03T12:00:00Z")), CalendarTime.BERLIN); }
        MutableClock(AtomicReference<Instant> time, ZoneId zone) { this.time = time; this.zone = zone; }
        void set(Instant instant) { time.set(instant); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(time, zone); }
        @Override public Instant instant() { return time.get(); }
    }
}
