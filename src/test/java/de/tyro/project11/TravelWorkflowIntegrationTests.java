package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
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
        "spring.datasource.url=jdbc:h2:mem:travel-workflow;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.attendance-mail.enabled=true", "spring.mail.host=example.invalid",
        "app.attendance-mail.from=test@example.test", "app.attendance-mail.base-url=http://localhost",
        "app.attendance-mail.initial-delay=3600000", "app.attendance-penalties.initial-delay=3600000",
        "app.travel-penalties.initial-delay=3600000"
})
@Import(AttendanceWorkflowIntegrationTests.TimeConfig.class)
@AutoConfigureMockMvc
class TravelWorkflowIntegrationTests {
    @Autowired AttendanceWorkflowIntegrationTests.MutableClock clock;
    @Autowired TravelService travel;
    @Autowired TravelReviewService reviews;
    @Autowired TravelPenaltyService penalties;
    @Autowired TravelPenaltyScheduler scheduler;
    @Autowired TravelDecisionMailDelivery delivery;
    @Autowired TravelApplicationRepository applications;
    @Autowired TravelPhotoRepository photos;
    @Autowired TravelDayPenaltyRepository days;
    @Autowired TravelDecisionEmailRepository emails;
    @Autowired UserRepository users;
    @Autowired HolidayRepository holidays;
    @Autowired ActivityRepository activities;
    @Autowired AttendanceService attendance;
    @Autowired AttendanceRules attendanceRules;
    @Autowired AttendancePenaltyService eventPenalties;
    @Autowired AttendancePenaltyRepository eventMarks;
    @Autowired AttendanceRepository sheets;
    @Autowired AttendanceEmailRepository reminders;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean JavaMailSender sender;
    AppUser admin, owner, other;
    static final Instant CUTOFF = Instant.parse("2026-01-08T23:00:00Z"); // through 8 January Berlin, exclusive 9 January

    @BeforeEach
    void setup() {
        emails.deleteAllInBatch(); days.deleteAllInBatch(); eventMarks.deleteAllInBatch(); reminders.deleteAllInBatch();
        sheets.deleteAll(); photos.deleteAllInBatch(); applications.deleteAll(); holidays.deleteAllInBatch();
        activities.deleteAllInBatch(); users.deleteAllInBatch();
        clock.set(Instant.parse("2025-12-01T12:00:00Z"));
        reset(sender); when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        admin = users.save(new AppUser("Admin", "admin@travel.test", "hash", true));
        owner = users.save(new AppUser("Urlauber", "owner@travel.test", "hash"));
        other = users.save(new AppUser("Andere Person", "other@travel.test", "hash"));
        jdbc.sql("update app_users set created_at = :at").param("at", OffsetDateTime.parse("2025-01-01T00:00:00Z")).update();
    }

    @Test
    void submittedLeaveIsPendingAndAdminCanReadAndDecideIt() throws Exception {
        var draft = leaveDraft("2025-12-30", "2026-01-01");
        long id = travel.submit(draft, owner.getEmail());
        assertThat(travel.submit(draft, owner.getEmail())).isEqualTo(id);
        assertThat(applications.count()).isEqualTo(1);
        assertThat(applications.findById(id).orElseThrow().getDecision()).isEqualTo(TravelApplication.Decision.PENDING);
        mvc.perform(get("/admin/travel").with(user(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Urlauber")));
        mvc.perform(get("/admin/travel/{id}", id).with(user(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Urlaub in Italien")))
                .andExpect(content().string(containsString("Noch nicht entschieden")));
        mvc.perform(get("/admin/travel/{id}", id).with(user(owner.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/travel/{id}", id).with(user(owner.getEmail()).roles("ADMIN")).with(csrf()).param("decision", "ACCEPTED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/travel/{id}", id).with(user(admin.getEmail())).param("decision", "ACCEPTED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/travel/{id}", id).with(user(admin.getEmail())).with(csrf()).param("decision", "REJECTED"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Begründung erforderlich")));
        mvc.perform(post("/admin/travel/{id}", id).with(user(admin.getEmail())).with(csrf()).param("decision", "ACCEPTED"))
                .andExpect(redirectedUrl("/admin/travel/" + id));
        assertThat(applications.findById(id).orElseThrow().getDecision()).isEqualTo(TravelApplication.Decision.ACCEPTED);
        assertThat(emails.count()).isEqualTo(1);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void reportDeadlineIncludesEntireEighthAndHandlesBerlinDst() {
        var leave = leave("2025-12-30", "2026-01-01", true);
        var holiday = holiday(leave);
        assertThat(TravelRules.deadline(holiday).toInstant()).isEqualTo(CUTOFF);
        var dstHoliday = new Holiday(owner, "DST", "", LocalDate.of(2026, 3, 25), LocalDate.of(2026, 3, 28), OffsetDateTime.now(clock));
        assertThat(TravelRules.deadline(dstHoliday).toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-04-05T00:00:00+02:00"));
        clock.set(CUTOFF.minusMillis(1));
        var report = reportDraft(holiday); stagePhotos(report);
        long reportId = travel.submit(report, owner.getEmail());
        assertThat(reviews.file(reportId, admin.getEmail()).late()).isFalse();
        clock.set(CUTOFF); scheduler.processDue();
        assertThat(tally()).isZero();
        assertThat(holidays.findById(holiday.getId()).orElseThrow().isInvalidated()).isFalse();
    }

    @Test
    void missingReportInvalidatesAcceptedLeaveAndChargesEveryDayOnce() {
        var leave = leave("2025-12-30", "2026-01-01", true);
        long holidayId = holiday(leave).getId();
        jdbc.sql("update app_users set tally_count = 7 where id = :id").param("id", owner.getId()).update();
        clock.set(CUTOFF.minusNanos(1)); scheduler.processDue(); assertThat(tally()).isEqualTo(7);
        clock.set(CUTOFF); scheduler.processDue(); scheduler.processDue();
        assertThat(tally()).isEqualTo(10); assertThat(days.count()).isEqualTo(3);
        assertThat(holidays.findById(holidayId).orElseThrow().isInvalidated()).isTrue();
        clock.set(CUTOFF.plus(Duration.ofDays(100))); scheduler.processDue(); assertThat(tally()).isEqualTo(10);
    }

    @Test
    void pendingOrRejectedLeaveHasNoDailyPenaltyAndRejectedLeaveDoesNotExcuseEvents() {
        var leave = leave("2025-12-30", "2026-01-01", false);
        clock.set(CUTOFF);
        var event = event("2025-12-31"); confirmAbsence(event);
        scheduler.processDue(); eventPenalties.reconcile(event.getId());
        assertThat(tally()).isZero(); // Pending review holds the event mark too.
        reviews.decide(leave, TravelApplication.Decision.REJECTED, "Kein Urlaub genehmigt", admin.getEmail());
        scheduler.processDue(); eventPenalties.reconcile(event.getId());
        assertThat(tally()).isEqualTo(1); assertThat(days.count()).isZero();
        assertThat(travel.reportableHolidays(owner.getEmail())).isEmpty();
        var report = reportDraft(holiday(leave)); stagePhotos(report);
        assertThatThrownBy(() -> travel.submit(report, owner.getEmail())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("abgelehnten AaB");
    }

    @Test
    void timelyReportCanBeFiledWhileLeavePendingButNeedsBothAdminConfirmations() {
        var leave = leave("2025-12-30", "2026-01-01", false);
        clock.set(Instant.parse("2026-01-02T12:00:00Z"));
        var draft = reportDraft(holiday(leave)); stagePhotos(draft);
        long report = travel.submit(draft, owner.getEmail());
        assertThat(reviews.file(report, admin.getEmail()).canAccept()).isFalse();
        assertThatThrownBy(() -> reviews.decide(report, TravelApplication.Decision.ACCEPTED, "", admin.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("zuerst");
        clock.set(CUTOFF.plus(Duration.ofDays(30))); scheduler.processDue(); assertThat(tally()).isZero();
        reviews.decide(leave, TravelApplication.Decision.ACCEPTED, "", admin.getEmail());
        scheduler.processDue(); assertThat(tally()).isZero();
        assertThat(travel.file(leave, owner.getEmail()).recognized()).isFalse();
        reviews.decide(report, TravelApplication.Decision.ACCEPTED, "Bericht geprüft", admin.getEmail());
        assertThat(travel.file(leave, owner.getEmail()).recognized()).isTrue();
        assertThat(tally()).isZero(); assertThat(days.count()).isZero();
    }

    @Test
    void rejectionOfTimelyReportAfterDeadlineImmediatelyInvalidatesLeave() {
        var leave = leave("2025-12-30", "2026-01-01", true);
        clock.set(CUTOFF.minusSeconds(60));
        var draft = reportDraft(holiday(leave)); stagePhotos(draft); long report = travel.submit(draft, owner.getEmail());
        clock.set(CUTOFF); scheduler.processDue(); assertThat(tally()).isZero();
        reviews.decide(report, TravelApplication.Decision.REJECTED, "Unbrauchbarer Bericht", admin.getEmail());
        assertThat(tally()).isEqualTo(3); assertThat(holidays.findById(holiday(leave).getId()).orElseThrow().isInvalidated()).isTrue();
        reviews.decide(report, TravelApplication.Decision.REJECTED, "Doppelklick", admin.getEmail());
        scheduler.processDue(); assertThat(tally()).isEqualTo(3);
        assertThatThrownBy(() -> reviews.decide(report, TravelApplication.Decision.ACCEPTED, "", admin.getEmail()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void reportRejectedBeforeCutoffIsAssessedOnlyAtCutoff() {
        var leave = leave("2025-12-30", "2026-01-01", true);
        clock.set(CUTOFF.minusSeconds(60));
        var draft = reportDraft(holiday(leave)); stagePhotos(draft); long report = travel.submit(draft, owner.getEmail());
        reviews.decide(report, TravelApplication.Decision.REJECTED, "Fehlende Angaben", admin.getEmail());
        assertThat(tally()).isZero();
        clock.set(CUTOFF); scheduler.processDue(); assertThat(tally()).isEqualTo(3);
    }

    @Test
    void lateReportCannotRestoreLeaveEvenIfLaterConfirmed() {
        var leave = leave("2025-12-30", "2026-01-01", true);
        clock.set(CUTOFF);
        var draft = reportDraft(holiday(leave)); stagePhotos(draft); long report = travel.submit(draft, owner.getEmail());
        assertThat(tally()).isEqualTo(3);
        assertThat(reviews.file(report, admin.getEmail()).late()).isTrue();
        reviews.decide(report, TravelApplication.Decision.ACCEPTED, "Inhalt bestätigt, Eingang bleibt verspätet", admin.getEmail());
        scheduler.processDue(); assertThat(tally()).isEqualTo(3);
        assertThat(travel.file(leave, owner.getEmail()).recognized()).isFalse();
        assertThat(travel.submit(draft, owner.getEmail())).isEqualTo(report);
    }

    @Test
    void dayPenaltiesReplaceExistingEventPenaltiesAndDoNotDoubleCountOverlaps() {
        clock.set(CUTOFF.plus(Duration.ofDays(10)));
        var inside = event("2025-12-31"); var outside = event("2026-01-04");
        confirmAbsence(inside); confirmAbsence(outside);
        assertThat(tally()).isEqualTo(2);
        // Retroactively filed leave cannot clear an event mark merely by being filed.
        var first = leave("2025-12-30", "2026-01-01", false);
        assertThat(tally()).isEqualTo(2);
        reviews.decide(first, TravelApplication.Decision.ACCEPTED, "", admin.getEmail());
        assertThat(tally()).isEqualTo(4); // three vacation days + the outside event, replaces the inside mark
        leave("2026-01-01", "2026-01-03", true);
        scheduler.processDue(); eventPenalties.reconcile(inside.getId()); eventPenalties.reconcile(outside.getId());
        assertThat(tally()).isEqualTo(6); assertThat(days.count()).isEqualTo(5);
        assertThat(eventMarks.findByActivityId(inside.getId())).singleElement().satisfies(p -> assertThat(p.isApplied()).isFalse());
        assertThat(eventMarks.findByActivityId(outside.getId())).singleElement().satisfies(p -> assertThat(p.isApplied()).isTrue());
        assertThat(attendance.pending(owner.getEmail()).counts()).doesNotContainKey(owner.getId());
        assertThat(attendance.mailReminder(inside.getId(), owner.getId())).isEmpty();
    }

    @Test
    void normalApprovedHolidayExcludesEventsBeforeAndAfterReportInvalidation() {
        leave("2025-12-30", "2026-01-01", true);
        clock.set(CUTOFF.minusSeconds(1));
        var event = event("2025-12-31"); confirmAbsence(event); eventPenalties.reconcile(event.getId());
        assertThat(tally()).isZero(); assertThat(eventMarks.count()).isZero(); assertThat(reminders.count()).isZero();
        clock.set(CUTOFF); scheduler.processDue(); eventPenalties.reconcile(event.getId());
        assertThat(tally()).isEqualTo(3); assertThat(eventMarks.count()).isZero();
    }

    @Test
    void reportSubmissionChecksOwnerEndDatePhotosAndUniqueness() {
        var leave = leave("2025-12-30", "2026-01-01", true);
        var draft = reportDraft(holiday(leave)); stagePhotos(draft);
        assertThatThrownBy(() -> travel.submit(draft, owner.getEmail())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("letzten Urlaubstag");
        clock.set(Instant.parse("2026-01-02T12:00:00Z"));
        assertThatThrownBy(() -> travel.submit(draft, other.getEmail())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("eigenen Beurlaubungen");
        var noPhotos = reportDraft(holiday(leave));
        assertThatThrownBy(() -> travel.submit(noPhotos, owner.getEmail())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Fotos");
        long id = travel.submit(draft, owner.getEmail());
        assertThat(travel.submit(draft, owner.getEmail())).isEqualTo(id);
        assertThat(photos.findByApplicationIdOrderByIdAsc(id)).hasSize(3);
        assertThat(travel.reportableHolidays(owner.getEmail())).isEmpty();
        assertThat(applications.count()).isEqualTo(2);
    }

    @Test
    void oldPortalShowsComicDelaysInsteadOfClaimingRecognition() throws Exception {
        var leave = leave("2025-12-30", "2026-01-01", true);
        String first = travel.file(leave, owner.getEmail()).summary().status();
        clock.set(clock.instant().plusSeconds(180));
        assertThat(travel.file(leave, owner.getEmail()).summary().status()).isNotEqualTo(first);
        mvc.perform(get("/amt/reisen/{id}", leave).with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Statusautomaten")))
                .andExpect(content().string(containsString("Fehler 1024")))
                .andExpect(content().string(not(containsString("endgültig anerkannt"))));
    }

    @Test
    void decisionsNotifyOnceAndSmtpFailuresRetry() throws Exception {
        var leave = leave("2025-12-30", "2026-01-01", true);
        reviews.decide(leave, TravelApplication.Decision.ACCEPTED, "Doppelklick", admin.getEmail());
        doThrow(new MailSendException("Temporary failure")).doNothing().when(sender).send(any(MimeMessage.class));
        delivery.deliver(leave, owner.getId()); delivery.deliver(leave, owner.getId());
        assertThat(emails.findById(leave).orElseThrow().getAttempts()).isEqualTo(1);
        clock.set(clock.instant().plusSeconds(60)); delivery.deliver(leave, owner.getId()); delivery.deliver(leave, owner.getId());
        assertThat(emails.findById(leave).orElseThrow().getStatus()).isEqualTo(TravelDecisionEmail.Status.SENT);
        var messages = ArgumentCaptor.forClass(MimeMessage.class); verify(sender, times(2)).send(messages.capture());
        assertThat(messages.getValue().getSubject()).contains("AaB angenommen");
        assertThat(messages.getValue().getContent().toString()).contains("08.01.2026", "/amt/eer?holiday=");
        clock.set(CUTOFF.minusSeconds(60));
        var draft = reportDraft(holiday(leave)); stagePhotos(draft); long report = travel.submit(draft, owner.getEmail());
        reviews.decide(report, TravelApplication.Decision.REJECTED, "Bericht ohne Zusammenhang", admin.getEmail());
        delivery.deliver(report, owner.getId()); delivery.deliver(report, owner.getId());
        verify(sender, times(3)).send(messages.capture());
        assertThat(messages.getValue().getSubject()).contains("EeR abgelehnt");
        assertThat(messages.getValue().getContent().toString()).contains("Bericht ohne Zusammenhang");
    }

    @Test
    void concurrentTravelAndEventSettlementsPreserveOneChargePerDay() throws Exception {
        clock.set(CUTOFF.plus(Duration.ofDays(10)));
        var event = event("2025-12-31"); confirmAbsence(event); assertThat(tally()).isEqualTo(1);
        var leave = leave("2025-12-30", "2026-01-01", false);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            List<Callable<Void>> jobs = List.of(
                    () -> { start.await(); reviews.decide(leave, TravelApplication.Decision.ACCEPTED, "", admin.getEmail()); return null; },
                    () -> { start.await(); eventPenalties.reconcile(event.getId()); return null; },
                    () -> { start.await(); penalties.reconcile(owner.getId()); return null; },
                    () -> { start.await(); reviews.decide(leave, TravelApplication.Decision.ACCEPTED, "", admin.getEmail()); return null; });
            var futures = jobs.stream().map(executor::submit).toList(); start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
            var sendStart = new CountDownLatch(1);
            Callable<Void> send = () -> { sendStart.await(); delivery.deliver(leave, owner.getId()); return null; };
            var firstSend = executor.submit(send); var secondSend = executor.submit(send);
            sendStart.countDown(); firstSend.get(20, TimeUnit.SECONDS); secondSend.get(20, TimeUnit.SECONDS);
        }
        scheduler.processDue(); eventPenalties.reconcile(event.getId());
        assertThat(tally()).isEqualTo(3); assertThat(days.count()).isEqualTo(3); assertThat(emails.count()).isEqualTo(1);
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void legacyHolidaysAreNotRetroactivelyChargedWithoutAcceptedLeave() {
        var holiday = holidays.save(new Holiday(owner, "Altdaten", "", LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 1), OffsetDateTime.now(clock)));
        clock.set(CUTOFF.plus(Duration.ofDays(10))); scheduler.processDue(); penalties.reconcile(owner.getId());
        assertThat(tally()).isZero(); assertThat(holiday.isInvalidated()).isFalse();
        var event = event("2025-12-31"); confirmAbsence(event); assertThat(tally()).isZero();
        var report = reportDraft(holiday); stagePhotos(report); long id = travel.submit(report, owner.getEmail());
        reviews.decide(id, TravelApplication.Decision.ACCEPTED, "Historischer Bericht", admin.getEmail());
        assertThat(tally()).isZero();
    }

    private long leave(String start, String end, boolean accept) {
        long id = travel.submit(leaveDraft(start, end), owner.getEmail());
        if (accept) reviews.decide(id, TravelApplication.Decision.ACCEPTED, "", admin.getEmail());
        return id;
    }
    private Holiday holiday(long leaveId) {
        long id = jdbc.sql("select holiday_id from travel_applications where id = :id").param("id", leaveId).query(Long.class).single();
        return holidays.findById(id).orElseThrow();
    }
    private TravelDraft leaveDraft(String start, String end) {
        var draft = baseDraft(TravelKind.LEAVE);
        draft.getForm().getValues().putAll(Map.of("startsOn", start, "endsOn", end, "destination", "Urlaub in Italien", "purpose", "Erholung"));
        return draft;
    }
    private TravelDraft reportDraft(Holiday holiday) {
        var draft = baseDraft(TravelKind.REPORT); draft.getForm().getValues().put("holidayId", holiday.getId().toString()); return draft;
    }
    private TravelDraft baseDraft(TravelKind kind) {
        var draft = new TravelDraft(kind); var form = draft.getForm();
        for (var field : TravelFields.forKind(kind)) form.getValues().put(field.key(), "Ausführliche Antwort für die Akte");
        form.setPersonalAnswers(List.of("A", "B", "C")); form.setKnowledgeAnswer(draft.getQuestions().knowledge().correctAnswer());
        form.setAbsurdAnswer("Eine hinreichend lange Antwort"); form.setLoyaltyAnswer("Loyal"); form.setAuditAnswer("Fusilli"); form.setAcceptedTerms(true);
        return draft;
    }
    private void stagePhotos(TravelDraft draft) {
        for (int i = 0; i < 3; i++) photos.save(new TravelPhoto(owner, draft.getId(), "Urlaub-" + i + ".png", "image/png", new byte[]{1, 2, 3}, OffsetDateTime.now(clock)));
    }
    private Activity event(String date) {
        return activities.save(new Activity("Event " + date, "", "", OffsetDateTime.parse(date + "T17:00:00+01:00"), OffsetDateTime.parse(date + "T21:00:00+01:00"), admin));
    }
    private void confirmAbsence(Activity event) {
        attendance.save(event.getId(), -1, Set.of(admin.getId(), other.getId()), admin.getEmail());
        var page = attendance.page(event.getId(), admin.getEmail());
        attendance.confirm(event.getId(), page.version(), Set.copyOf(page.recipientIds()), admin.getEmail());
    }
    private int tally() { return users.findById(owner.getId()).orElseThrow().getTallyCount(); }
}
