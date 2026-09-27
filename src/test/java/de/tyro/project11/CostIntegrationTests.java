package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.costs.*;
import de.tyro.project11.registration.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:event-costs;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.attendance-mail.enabled=false", "app.attendance-mail.initial-delay=3600000",
        "app.attendance-penalties.initial-delay=3600000"
})
@AutoConfigureMockMvc
class CostIntegrationTests {
    @Autowired CostService costs;
    @Autowired EventService events;
    @Autowired EventCostRepository requests;
    @Autowired ActivityRepository activities;
    @Autowired UserRepository users;
    @Autowired AttendanceService attendance;
    @Autowired AttendanceRepository sheets;
    @Autowired AttendanceEmailRepository reminders;
    @Autowired AttendancePenaltyRepository marks;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    AppUser admin, creator, member, outsider;
    Activity event;

    @BeforeEach
    void setup() {
        requests.deleteAll(); reminders.deleteAllInBatch(); marks.deleteAllInBatch(); sheets.deleteAll();
        activities.deleteAllInBatch(); users.deleteAllInBatch();
        admin = users.save(new AppUser("Admin", "admin@example.test", "hash", true));
        creator = users.save(new AppUser("Creator", "creator@example.test", "hash"));
        member = users.save(new AppUser("Member", "member@example.test", "hash"));
        outsider = users.save(new AppUser("Absent", "absent@example.test", "hash"));
        jdbc.sql("update app_users set created_at = :date").param("date", OffsetDateTime.parse("2025-01-01T00:00:00Z")).update();
        event = activity("Neujahr");
    }

    @Test
    void sharedEntryPageIsLinkedFromOverviewAndEventAndRendersForAnyMember() throws Exception {
        mvc.perform(get("/costs")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/costs").with(user(member.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Noch keine Kosten eingetragen")));
        long id = costs.create(event.getId(), form("10,00"), member.getEmail());
        String link = "/events/" + event.getId() + "/costs/new";
        for (String url : List.of("/costs", "/events/" + event.getId(), "/events/" + event.getId() + "/costs"))
            mvc.perform(get(url).with(user(member.getEmail()))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"" + link + "\"")));
        mvc.perform(get("/costs/new").param("eventId", event.getId().toString()).with(user(member.getEmail())))
                .andExpect(redirectedUrl(link));
        mvc.perform(get(link).with(user(member.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Was hast du ausgelegt?")));
        mvc.perform(get("/events/{event}/costs/{cost}/edit", event.getId(), id).with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("10,00")));
    }

    @Test
    void allocationWaitsForConfirmedAttendanceAndExcludesAbsentees() {
        long id = costs.create(event.getId(), form("10"), creator.getEmail());
        assertThat(view().awaitingDistribution()).isTrue();
        assertThat(row(id).shares()).isEmpty();
        assertThatThrownBy(() -> pay(id, member)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        record(false, admin, creator, member);
        assertThat(row(id).shares()).isEmpty();
        confirm();
        assertThat(view().awaitingDistribution()).isFalse();
        assertThat(row(id).shares()).extracting(CostService.Share::userId).containsExactly(admin.getId(), creator.getId(), member.getId());
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(334L, 333L, 333L);
        assertThat(row(id).shares().stream().mapToLong(CostService.Share::cents).sum()).isEqualTo(1000);
        assertThat(view().myShareCents()).isEqualTo(333);
        assertThat(view().myDueCents()).isZero();
        assertThat(view().myReceivableCents()).isEqualTo(667);
        assertThat(costs.event(event.getId(), member.getEmail()).myDueCents()).isEqualTo(333);
        assertThat(costs.event(event.getId(), outsider.getEmail()).myShareCents()).isZero();
    }

    @Test
    void individualPaymentsUpdateStatusesAndOnlyLeaveUnpaidSharesInTotals() throws Exception {
        record(true, admin, creator, member);
        long id = costs.create(event.getId(), form("10"), creator.getEmail());

        pay(id, member);
        var partial = row(id);
        assertThat(partial.partiallyPaid()).isTrue();
        assertThat(partial.paid()).isFalse();
        assertThat(partial.paymentTracking()).isTrue();
        assertThat(partial.openReimbursement()).isEqualTo(Money.display(334));
        assertThat(view().openCents()).isEqualTo(334);
        assertThat(view().myReceivableCents()).isEqualTo(334);
        assertThat(costs.event(event.getId(), member.getEmail()).myDueCents()).isZero();
        assertThat(costs.event(event.getId(), admin.getEmail()).myDueCents()).isEqualTo(334);
        assertThat(partial.shares().stream().filter(share -> share.userId() == member.getId()).findFirst().orElseThrow().paidAt()).isNotBlank();
        assertThatThrownBy(() -> costs.edit(event.getId(), id, creator.getEmail())).hasMessageContaining("409");

        mvc.perform(get("/events/{id}/costs", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teilweise bezahlt")))
                .andExpect(content().string(containsString("Wieder als offen markieren")))
                .andExpect(content().string(containsString("Als bezahlt markieren")));

        record(true, outsider, admin, creator, member);
        assertThat(row(id).shares()).hasSize(3); // Payment tracking freezes the original distribution.
        pay(id, admin);
        assertThat(row(id).paid()).isTrue();
        assertThat(view().openCents()).isZero();

        costs.markShareUnpaid(event.getId(), id, member.getId(), row(id).version(), creator.getEmail());
        assertThat(row(id).partiallyPaid()).isTrue();
        assertThat(costs.event(event.getId(), member.getEmail()).myDueCents()).isEqualTo(333);
        assertThat(costs.event(event.getId(), admin.getEmail()).myDueCents()).isZero();
    }

    @Test
    void multipleRequestsAreSummedPerEventAndNonParticipantCanBeReimbursed() {
        record(true, creator, member);
        costs.create(event.getId(), form("10.00"), creator.getEmail());
        costs.create(event.getId(), form("4,00"), member.getEmail());
        costs.create(event.getId(), form("2"), outsider.getEmail());
        var second = activity("Zweites Event");
        costs.create(second.getId(), form("99"), creator.getEmail());
        assertThat(view().totalCents()).isEqualTo(1600);
        assertThat(view().myShareCents()).isEqualTo(800);
        assertThat(view().myDueCents()).isEqualTo(300);
        assertThat(view().myReceivableCents()).isEqualTo(500);
        assertThat(costs.event(event.getId(), outsider.getEmail()).myReceivableCents()).isEqualTo(200);
        assertThat(costs.overview(creator.getEmail()).events()).hasSize(2)
                .extracting(CostService.Event::totalCents).containsExactlyInAnyOrder(1600L, 9900L);
    }

    @Test
    void paidSnapshotSurvivesAttendanceCorrectionsAndReopeningUsesCurrentList() throws Exception {
        record(true, creator, member);
        long id = costs.create(event.getId(), form("10"), creator.getEmail());
        pay(id, member);
        assertThat(row(id).paid()).isTrue();
        assertThat(row(id).shares()).filteredOn(CostService.Share::paymentRequired)
                .allMatch(CostService.Share::paid);
        assertThat(view().openCents()).isZero();
        assertThat(view().myReceivableCents()).isZero();
        assertThatThrownBy(() -> costs.edit(event.getId(), id, creator.getEmail())).hasMessageContaining("409");
        record(true, admin, creator, member);
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(500L, 500L);
        mvc.perform(get("/events/{id}/costs", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Alle Zahlungsstände zurücksetzen")));
        costs.reopen(event.getId(), id, row(id).version(), creator.getEmail());
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(334L, 333L, 333L);
        assertThat(view().myReceivableCents()).isEqualTo(667);
        payAll(id);
        assertThat(row(id).shares()).hasSize(3);
    }

    @Test
    void staleCostAndAttendanceVersionsCannotSettleChangedAmountsOrParticipants() {
        record(true, creator, member);
        long id = costs.create(event.getId(), form("10"), creator.getEmail());
        var old = row(id); long oldAttendance = view().attendanceVersion();
        var edited = costs.edit(event.getId(), id, creator.getEmail()); edited.setAmount("20");
        costs.update(event.getId(), id, edited, creator.getEmail());
        assertThatThrownBy(() -> costs.update(event.getId(), id, edited, creator.getEmail())).hasMessageContaining("409");
        assertThatThrownBy(() -> costs.markSharePaid(event.getId(), id, member.getId(), old.version(), oldAttendance, creator.getEmail())).hasMessageContaining("409");
        record(true, creator, member, admin);
        assertThatThrownBy(() -> costs.markSharePaid(event.getId(), id, member.getId(), row(id).version(), oldAttendance, creator.getEmail())).hasMessageContaining("409");
        assertThat(row(id).paid()).isFalse();
        payAll(id);
        assertThatThrownBy(() -> costs.reopen(event.getId(), id, old.version(), creator.getEmail())).hasMessageContaining("409");
        assertThat(row(id).shares().stream().mapToLong(CostService.Share::cents).sum()).isEqualTo(2000);
    }

    @Test
    void emptyConfirmedListNeverDividesByZeroAndSmallAmountsConserveEveryCent() {
        record(true);
        long id = costs.create(event.getId(), form("0,01"), creator.getEmail());
        assertThat(row(id).allocated()).isFalse();
        assertThatThrownBy(() -> pay(id, admin)).hasMessageContaining("409");
        record(true, admin, creator, member);
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(1L, 0L, 0L);
        pay(id, admin);
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(1L, 0L, 0L);
    }

    @Test
    void onlyRequestCreatorCanChangeOrSettleEvenWhenAnotherUserIsAdmin() throws Exception {
        record(true, creator, member);
        long id = costs.create(event.getId(), form("10"), member.getEmail());
        for (var other : List.of(creator, admin)) {
            assertThatThrownBy(() -> costs.edit(event.getId(), id, other.getEmail())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> costs.update(event.getId(), id, form("20"), other.getEmail())).isInstanceOf(AccessDeniedException.class);
            mvc.perform(post("/events/{event}/costs/{cost}/shares/{participant}/paid", event.getId(), id, creator.getId()).with(user(other.getEmail())).with(csrf())
                            .param("version", "0").param("attendanceVersion", Long.toString(view().attendanceVersion())))
                    .andExpect(status().isForbidden());
        }
        costs.markSharePaid(event.getId(), id, creator.getId(), 0, view().attendanceVersion(), member.getEmail());
        assertThatThrownBy(() -> costs.reopen(event.getId(), id, row(id).version(), admin.getEmail())).isInstanceOf(AccessDeniedException.class);
        long otherEvent = activity("Anderes Event").getId();
        assertThatThrownBy(() -> costs.edit(otherEvent, id, member.getEmail())).hasMessageContaining("404");
    }

    @Test
    void postsUseSignedInCreatorAndRequireCsrfAndOwnerCanEditSettleAndReopen() throws Exception {
        record(true, creator, member);
        String url = "/events/" + event.getId() + "/costs";
        String key = UUID.randomUUID().toString();
        mvc.perform(post(url).with(user(member.getEmail())).param("description", "Snacks").param("amount", "12,50"))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(member.getEmail())).with(csrf()).param("description", "Snacks").param("amount", "12,50")
                        .param("requestKey", key).param("creatorId", admin.getId().toString()).param("paidAt", "2026-01-01"))
                .andExpect(redirectedUrl(url));
        var request = view().requests().getFirst();
        assertThat(request.creatorId()).isEqualTo(member.getId()); assertThat(request.paid()).isFalse();
        mvc.perform(post(url + "/" + request.id()).with(user(member.getEmail())).with(csrf())
                        .param("description", "Pizza").param("amount", "14,50").param("requestKey", key).param("version", "0"))
                .andExpect(redirectedUrl(url));
        assertThat(row(request.id()).cents()).isEqualTo(1450);
        mvc.perform(post(url + "/" + request.id() + "/shares/" + creator.getId() + "/paid").with(user(member.getEmail())).with(csrf())
                        .param("version", Long.toString(row(request.id()).version())).param("attendanceVersion", Long.toString(view().attendanceVersion())))
                .andExpect(redirectedUrl(url));
        assertThat(row(request.id()).paid()).isTrue();
        mvc.perform(post(url + "/" + request.id() + "/shares/" + creator.getId() + "/unpaid").with(user(member.getEmail())).with(csrf())
                        .param("version", Long.toString(row(request.id()).version())))
                .andExpect(redirectedUrl(url));
        assertThat(row(request.id()).paid()).isFalse();
        costs.markSharePaid(event.getId(), request.id(), creator.getId(), row(request.id()).version(), view().attendanceVersion(), member.getEmail());
        mvc.perform(post(url + "/" + request.id() + "/reopen").with(user(member.getEmail())).with(csrf())
                        .param("version", Long.toString(row(request.id()).version())))
                .andExpect(redirectedUrl(url));
        assertThat(row(request.id()).paid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.001", "1,001", "1e3", "1.000,00", "NaN", "10000000", "99999999999999999999999"})
    void malformedMoneyIsRejectedWithoutSaving(String amount) throws Exception {
        assertThatThrownBy(() -> costs.create(event.getId(), form(amount), creator.getEmail())).hasMessageContaining("400");
        mvc.perform(post("/events/{id}/costs", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("description", "Pizza").param("amount", amount).param("requestKey", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("costForm", "amount"));
        assertThat(requests.count()).isZero();
    }

    @Test
    void acceptedMoneyFormatsAreExact() {
        for (String amount : List.of("1", "1.2", "1,23", " 4,50 ", "9999999.99")) costs.create(event.getId(), form(amount), creator.getEmail());
        assertThat(view().requests()).extracting(CostService.Request::cents).containsExactly(100L, 120L, 123L, 450L, 999999999L);
    }

    @Test
    void initialEventCostIsOptionalOwnedByOrganizerAndSavedWithEvent() throws Exception {
        var form = eventForm("64,50");
        long eventId = events.create(form, creator.getEmail());
        assertThat(costs.event(eventId, creator.getEmail()).requests()).singleElement().satisfies(r -> {
            assertThat(r.creatorId()).isEqualTo(creator.getId()); assertThat(r.cents()).isEqualTo(6450); assertThat(r.allocated()).isFalse();
        });
        long free = events.create(eventForm(""), member.getEmail());
        assertThat(costs.event(free, member.getEmail()).requests()).isEmpty();
        long previous = activities.count();
        assertThatThrownBy(() -> events.create(eventForm("1,111"), creator.getEmail())).hasMessageContaining("400");
        mvc.perform(post("/events").with(user(creator.getEmail())).with(csrf())
                        .param("name", "Invalid").param("date", "2026-02-01").param("costAmount", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("eventForm", "costAmount"));
        assertThat(activities.count()).isEqualTo(previous);
        var result = mvc.perform(post("/events").with(user(member.getEmail())).with(csrf())
                        .param("name", "Dinner").param("date", "2026-02-01").param("costAmount", "12.34"))
                .andExpect(status().is3xxRedirection()).andReturn();
        long postedId = Long.parseLong(result.getResponse().getRedirectedUrl().substring("/events/".length()));
        assertThat(costs.event(postedId, member.getEmail()).totalCents()).isEqualTo(1234);
    }

    @Test
    void replayedAndConcurrentCreatesAndPaymentsDoNotDuplicateRequestsOrShares() throws Exception {
        record(true, creator, member);
        var form = form("10");
        concurrent(() -> costs.create(event.getId(), form, creator.getEmail()));
        assertThat(requests.count()).isEqualTo(1);
        long id = costs.create(event.getId(), form, creator.getEmail());
        var row = row(id); long attendanceVersion = view().attendanceVersion();
        concurrent(() -> { costs.markSharePaid(event.getId(), id, member.getId(), row.version(), attendanceVersion, creator.getEmail()); return id; });
        assertThat(row(id).shares()).hasSize(2); assertThat(row(id).paid()).isTrue();
        long other = activity("Another event").getId();
        assertThatThrownBy(() -> costs.create(other, form, creator.getEmail())).hasMessageContaining("409");
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void descriptionsAreEscapedAndConflictPagesExplainHowToRecover() throws Exception {
        var form = form("10"); form.setDescription("<script>alert(1)</script>");
        long id = costs.create(event.getId(), form, creator.getEmail());
        mvc.perform(get("/events/{id}/costs", event.getId()).with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        mvc.perform(post("/events/{event}/costs/{cost}/shares/{participant}/paid", event.getId(), id, member.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("version", "0").param("attendanceVersion", "-1"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Zuerst muss eine Teilnehmerliste")));
    }

    @Test
    void selectedGroupExcludesOtherAttendeesAndSplitsExactly() {
        record(true, admin, creator, member);
        var form = form("10,01"); form.setSelectedOnly(true); form.setSelectedUserIds(Set.of(admin.getId(), member.getId()));
        long id = costs.create(event.getId(), form, creator.getEmail());
        assertThat(row(id).selectedOnly()).isTrue();
        assertThat(row(id).shares()).extracting(CostService.Share::userId).containsExactly(admin.getId(), member.getId());
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(501L, 500L);
        assertThat(view().myShareCents()).isZero();
        assertThat(view().myReceivableCents()).isEqualTo(1001);
        assertThat(costs.edit(event.getId(), id, creator.getEmail()).getSelectedUserIds()).containsExactlyInAnyOrder(admin.getId(), member.getId());
        payAll(id);
        assertThat(row(id).shares()).hasSize(2);
    }

    @Test
    void selectedAbsenteesKeepTheirSharesAndAttendanceChangesDoNotBlockPayment() {
        var form = form("10"); form.setSelectedOnly(true); form.setSelectedUserIds(Set.of(creator.getId(), member.getId()));
        long id = costs.create(event.getId(), form, creator.getEmail());
        assertThat(row(id).allocated()).isTrue();
        var original = row(id);
        long originalAttendance = view().attendanceVersion();
        record(true, admin);
        assertThat(row(id).shares()).extracting(CostService.Share::userId).containsExactly(creator.getId(), member.getId());
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(500L, 500L);
        costs.markSharePaid(event.getId(), id, member.getId(), original.version(), originalAttendance, creator.getEmail());
        record(true, admin, member, creator);
        assertThat(row(id).shares()).extracting(CostService.Share::userId).containsExactly(creator.getId(), member.getId());
        costs.reopen(event.getId(), id, row(id).version(), creator.getEmail());
        assertThat(row(id).shares()).extracting(CostService.Share::userId).containsExactly(creator.getId(), member.getId());
    }

    @Test
    void selectedPeopleCanPayWithoutAnyAttendanceSheet() throws Exception {
        var form = form("10,01"); form.setSelectedOnly(true); form.setSelectedUserIds(Set.of(admin.getId(), member.getId()));
        long id = costs.create(event.getId(), form, creator.getEmail());
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(501L, 500L);
        assertThat(costs.event(event.getId(), member.getEmail()).myDueCents()).isEqualTo(500);
        assertThat(view().awaitingDistribution()).isFalse();
        mvc.perform(post("/events/{event}/costs/{cost}/shares/{person}/paid", event.getId(), id, member.getId())
                        .with(user(creator.getEmail())).with(csrf())
                        .param("version", Long.toString(row(id).version())).param("attendanceVersion", "-1"))
                .andExpect(status().is3xxRedirection());
        pay(id, admin);
        assertThat(row(id).paid()).isTrue();
    }

    @Test
    void selectedPeopleCanPayWithUnconfirmedOrEmptyAttendance() {
        record(false, creator);
        var form = form("10"); form.setSelectedOnly(true); form.setSelectedUserIds(Set.of(member.getId()));
        long id = costs.create(event.getId(), form, creator.getEmail());
        pay(id, member);
        assertThat(row(id).paid()).isTrue();
        costs.reopen(event.getId(), id, row(id).version(), creator.getEmail());
        record(true);
        assertThat(row(id).shares()).extracting(CostService.Share::userId).containsExactly(member.getId());
        pay(id, member);
        assertThat(row(id).paid()).isTrue();
    }

    @Test
    void selectionChangesInvalidateStalePaymentAndSwitchingToAllClearsSelection() {
        record(true, admin, creator, member);
        long id = costs.create(event.getId(), form("10"), creator.getEmail());
        var original = row(id);
        var edit = costs.edit(event.getId(), id, creator.getEmail());
        edit.setSelectedOnly(true); edit.setSelectedUserIds(Set.of(member.getId()));
        costs.update(event.getId(), id, edit, creator.getEmail());
        assertThat(row(id).shares()).hasSize(1);
        assertThatThrownBy(() -> costs.markSharePaid(event.getId(), id, member.getId(), original.version(), view().attendanceVersion(), creator.getEmail())).hasMessageContaining("409");
        edit = costs.edit(event.getId(), id, creator.getEmail()); edit.setSelectedOnly(false);
        costs.update(event.getId(), id, edit, creator.getEmail());
        assertThat(row(id).shares()).hasSize(3);
        assertThat(costs.edit(event.getId(), id, creator.getEmail()).getSelectedUserIds()).isEmpty();
    }

    @Test
    void emptyOrUnknownCustomSelectionIsRejectedWithoutChanges() throws Exception {
        var form = form("10"); form.setSelectedOnly(true);
        assertThatThrownBy(() -> costs.create(event.getId(), form, creator.getEmail())).hasMessageContaining("400");
        form.setSelectedUserIds(Set.of(Long.MAX_VALUE));
        assertThatThrownBy(() -> costs.create(event.getId(), form, creator.getEmail())).hasMessageContaining("400");
        mvc.perform(post("/events/{id}/costs", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("amount", "10").param("description", "Pizza").param("selectedOnly", "true"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("costForm", "selectedUserIds"));
        assertThat(requests.count()).isZero();
        long count = activities.count();
        var eventForm = eventForm("10"); eventForm.setCostSelectedOnly(true);
        assertThatThrownBy(() -> events.create(eventForm, creator.getEmail())).hasMessageContaining("400");
        assertThat(activities.count()).isEqualTo(count);
    }

    @Test
    void selectionFormsBindAndRenderForCostsAndInitialEventCosts() throws Exception {
        mvc.perform(get("/events/new").with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Nur ausgewählte Personen")));
        mvc.perform(post("/events/{id}/costs", event.getId()).with(user(creator.getEmail())).with(csrf())
                        .param("amount", "10").param("description", "Pizza").param("selectedOnly", "true")
                        .param("selectedUserIds", member.getId().toString()))
                .andExpect(status().is3xxRedirection());
        long id = view().requests().getFirst().id();
        assertThat(costs.edit(event.getId(), id, creator.getEmail()).getSelectedUserIds()).containsExactly(member.getId());
        mvc.perform(get("/events/{event}/costs/{id}/edit", event.getId(), id).with(user(creator.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("checked=\"checked\"")));
        var response = mvc.perform(post("/events").with(user(creator.getEmail())).with(csrf())
                        .param("name", "Dinner").param("date", "2026-02-01").param("costAmount", "10")
                        .param("costSelectedOnly", "true").param("costSelectedUserIds", member.getId().toString()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        long eventId = Long.parseLong(response.getRedirectedUrl().substring("/events/".length()));
        var request = costs.event(eventId, creator.getEmail()).requests().getFirst();
        assertThat(request.selectedOnly()).isTrue();
        assertThat(costs.edit(eventId, request.id(), creator.getEmail()).getSelectedUserIds()).containsExactly(member.getId());
        mvc.perform(post("/events").with(user(creator.getEmail())).with(csrf())
                        .param("name", "Dinner").param("date", "2026-02-01").param("costAmount", "10").param("costSelectedOnly", "true"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("eventForm", "costSelectedUserIds"));
    }

    @Test
    void reopeningOnePaymentPreservesTheOtherPersonsOriginalTimestamp() {
        record(true, admin, creator, member);
        long id = costs.create(event.getId(), form("12"), creator.getEmail());
        payAll(id);
        var earlier = OffsetDateTime.parse("2026-01-02T10:00:00Z");
        jdbc.sql("update event_cost_shares set paid_at = :paid where cost_id = :cost and participant_id = :person")
                .param("paid", earlier).param("cost", id).param("person", admin.getId()).update();

        costs.markShareUnpaid(event.getId(), id, member.getId(), row(id).version(), creator.getEmail());

        assertThat(jdbc.sql("select paid_at from event_cost_shares where cost_id = :cost and participant_id = :person")
                .param("cost", id).param("person", admin.getId()).query(OffsetDateTime.class).single()).isEqualTo(earlier);
        assertThat(row(id).partiallyPaid()).isTrue();
        assertThat(view().openCents()).isEqualTo(400);
    }

    @Test
    void legacyCompletedRequestKeepsOtherSharesPaidWhenOnePersonIsReopened() {
        record(true, admin, creator, member);
        long id = costs.create(event.getId(), form("12"), creator.getEmail());
        payAll(id);
        // Reproduce old stored rows: only the request had a completion timestamp.
        jdbc.sql("update event_cost_shares set paid_at = null where cost_id = :cost").param("cost", id).update();
        assertThat(row(id).shares()).filteredOn(CostService.Share::paymentRequired).allMatch(CostService.Share::paid);
        assertThat(view().openCents()).isZero();
        record(false, creator, member); // A pending attendance edit must not replace the snapshot.

        costs.markShareUnpaid(event.getId(), id, member.getId(), row(id).version(), creator.getEmail());
        assertThat(row(id).shares()).hasSize(3);
        assertThat(costs.event(event.getId(), admin.getEmail()).myDueCents()).isZero();
        assertThat(costs.event(event.getId(), member.getEmail()).myDueCents()).isEqualTo(400);
        assertThat(jdbc.sql("select paid_at from event_cost_shares where cost_id = :cost and participant_id = :person")
                .param("cost", id).param("person", admin.getId()).query(OffsetDateTime.class).single()).isNotNull();
        pay(id, member);
        assertThat(row(id).paid()).isTrue();
    }

    @Test
    void paymentEndpointsRequireCsrfAndCreatorAndRejectNonPayablePeople() throws Exception {
        record(true, admin, creator, member);
        long id = costs.create(event.getId(), form("0,01"), creator.getEmail());
        for (var person : List.of(creator, member, outsider)) {
            assertThatThrownBy(() -> pay(id, person)).hasMessageContaining("409");
            assertThat(row(id).paymentTracking()).isFalse();
        }
        String base = "/events/" + event.getId() + "/costs/" + id + "/shares/" + admin.getId();
        mvc.perform(post(base + "/paid").with(user(creator.getEmail()))
                        .param("version", "0").param("attendanceVersion", Long.toString(view().attendanceVersion())))
                .andExpect(status().isForbidden());
        pay(id, admin);
        mvc.perform(post(base + "/unpaid").with(user(creator.getEmail())).param("version", Long.toString(row(id).version())))
                .andExpect(status().isForbidden());
        for (var person : List.of(admin, member, outsider)) {
            mvc.perform(post(base + "/unpaid").with(user(person.getEmail())).with(csrf())
                            .param("version", Long.toString(row(id).version())))
                    .andExpect(status().isForbidden());
        }
        assertThat(row(id).paid()).isTrue();
        mvc.perform(get("/events/{id}/costs", event.getId()).with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("cost-payment-toggle"))));
    }

    @Test
    void partialPaymentVersionsPreventLostUpdatesAndLastUndoRestoresCurrentDistribution() {
        record(true, admin, creator, member);
        long id = costs.create(event.getId(), form("12"), creator.getEmail());
        long originalVersion = row(id).version();
        pay(id, admin);
        long partialVersion = row(id).version();
        assertThat(partialVersion).isGreaterThan(originalVersion);
        assertThatThrownBy(() -> costs.markSharePaid(event.getId(), id, member.getId(), originalVersion,
                view().attendanceVersion(), creator.getEmail())).hasMessageContaining("409");
        pay(id, member);
        assertThatThrownBy(() -> costs.markShareUnpaid(event.getId(), id, admin.getId(), partialVersion,
                creator.getEmail())).hasMessageContaining("409");
        costs.markShareUnpaid(event.getId(), id, member.getId(), row(id).version(), creator.getEmail());
        record(true, creator, member);
        long beforeUndo = row(id).version();
        costs.markShareUnpaid(event.getId(), id, admin.getId(), beforeUndo, creator.getEmail());
        assertThat(row(id).paymentTracking()).isFalse();
        assertThat(row(id).shares()).extracting(CostService.Share::cents).containsExactly(600L, 600L);
        assertThat(costs.edit(event.getId(), id, creator.getEmail())).isNotNull();
        long afterUndo = row(id).version();
        costs.markShareUnpaid(event.getId(), id, admin.getId(), beforeUndo, creator.getEmail());
        assertThat(row(id).version()).isEqualTo(afterUndo);
    }

    private void concurrent(Callable<Long> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> run = () -> { start.await(); return action.call(); };
            var first = executor.submit(run); var second = executor.submit(run); start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
    }
    private CostService.Event view() { return costs.event(event.getId(), creator.getEmail()); }
    private CostService.Request row(long id) { return view().requests().stream().filter(r -> r.id() == id).findFirst().orElseThrow(); }
    private void pay(long id, AppUser participant) {
        var eventView = view();
        costs.markSharePaid(event.getId(), id, participant.getId(), row(id).version(), eventView.attendanceVersion(), creator.getEmail());
    }
    private void payAll(long id) {
        while (true) {
            var unpaid = row(id).shares().stream().filter(share -> share.paymentRequired() && !share.paid()).findFirst();
            if (unpaid.isEmpty()) return;
            var eventView = view();
            costs.markSharePaid(event.getId(), id, unpaid.get().userId(), row(id).version(), eventView.attendanceVersion(), creator.getEmail());
        }
    }
    private void record(boolean confirmed, AppUser... attendees) {
        attendance.save(event.getId(), attendance.page(event.getId(), creator.getEmail()).version(),
                new HashSet<>(Arrays.stream(attendees).map(AppUser::getId).toList()), creator.getEmail());
        if (confirmed) confirm();
    }
    private void confirm() { var page = attendance.page(event.getId(), admin.getEmail()); attendance.confirm(event.getId(), page.version(), Set.copyOf(page.recipientIds()), admin.getEmail()); }
    private CostForm form(String amount) { var form = new CostForm(); form.setAmount(amount); return form; }
    private EventForm eventForm(String amount) { var form = new EventForm(); form.setName("Dinner"); form.setDate(LocalDate.of(2026, 2, 1)); form.setCostAmount(amount); return form; }
    private Activity activity(String title) { return activities.save(new Activity(title, "", "", OffsetDateTime.parse("2026-01-01T17:00:00Z"), OffsetDateTime.parse("2026-01-01T19:00:00Z"), creator)); }
    @TestConfiguration
    static class TimeConfig { @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2026-01-03T12:00:00Z"), CalendarTime.BERLIN); } }
}
