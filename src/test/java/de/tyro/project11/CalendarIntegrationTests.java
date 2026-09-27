package de.tyro.project11;

import de.tyro.project11.calendar.*;
import de.tyro.project11.profile.ProfileForm;
import de.tyro.project11.profile.ProfileService;
import de.tyro.project11.profile.UserProfileRepository;
import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:calendar-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(CalendarIntegrationTests.FixedTime.class)
class CalendarIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired CalendarService calendar;
    @Autowired ActivityRepository activities;
    @Autowired HolidayRepository holidays;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;
    @Autowired UserProfileRepository profiles;
    @Autowired ProfileService profileService;

    AppUser alex;
    AppUser mina;

    @BeforeEach
    void prepareUsers() {
        holidays.deleteAllInBatch();
        activities.deleteAllInBatch();
        profiles.deleteAll();
        users.deleteAllInBatch();
        alex = users.save(new AppUser("Alex Morgan", "alex@example.com", "test-hash", true));
        mina = users.save(new AppUser("Mina Member", "mina@example.com", "test-hash"));
    }

    @Test
    void calendarRequiresSignInAndHasNoCreationRoutes() throws Exception {
        mvc.perform(get("/calendar")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signin"));
        for (String path : new String[]{"/calendar/activities", "/calendar/holidays"}) {
            mvc.perform(post(path).with(user(mina.getEmail())).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path + "/new").with(user(alex.getEmail())))
                    .andExpect(status().isForbidden());
        }
        assertThat(activities.count()).isZero();
        assertThat(holidays.count()).isZero();
    }

    @Test
    void defaultMonthAndTodayUseBerlinInsteadOfTheClockZone() throws Exception {
        // Fixed instant is still August in UTC, but September in Berlin.
        var view = calendar.load(null, CalendarFilter.ALL);
        assertThat(view.month()).isEqualTo("2026-09");
        assertThat(view.todayMonth()).isEqualTo("2026-09");
        assertThat(view.days().getFirst().date()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(view.days()).filteredOn(CalendarView.Day::today)
                .extracting(CalendarView.Day::date).containsExactly(LocalDate.of(2026, 9, 1));

        mvc.perform(get("/calendar").with(user(mina.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("September 2026")))
                .andExpect(content().string(not(containsString("Europe/Berlin"))))
                .andExpect(content().string(not(containsString("Add activity"))))
                .andExpect(content().string(not(containsString("Submit holiday"))));
    }

    @Test
    void allMembersSeeOtherPeoplesEntriesAndEscapedDetails() throws Exception {
        var activity = activity("Dinner <script>alert('x')</script>", "2026-09-10T16:00:00Z", "2026-09-10T19:00:00Z");
        holiday("Mina's holiday", "2026-09-11", "2026-09-13");

        var response = mvc.perform(get("/calendar").param("month", "2026-09").with(user(mina.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alex Morgan")))
                .andExpect(content().string(containsString("Mina Member")))
                .andExpect(content().string(containsString("Mina&#39;s holiday")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("10 Sept. 2026, 18:00 MESZ")))
                .andExpect(content().string(containsString("href=\"/events/" + activity.getId() + "\"")))
                .andExpect(content().string(containsString("id=\"activity-" + activity.getId() + "\""))).andReturn();
        String grid = response.getResponse().getContentAsString().split("<div class=\"calendar-caption\">")[0];
        assertThat(grid).contains("class=\"holiday-name\">Mina&#39;s holiday</span>", "--holiday-color: #52734d",
                        "title=\"Mina&#39;s holiday", "Mina Member", "11 Sept. 2026 – 13 Sept. 2026", "Away with friends.")
                .doesNotContain("class=\"holiday-name\">Mina Member</span>", "Mina Member · all day");
        mvc.perform(get("/welcome").with(user(mina.getEmail())))
                .andExpect(content().string(containsString("href=\"/calendar\"")));
    }

    @Test
    void filtersAffectBothGridAndAgendaAndPersistAcrossMonthNavigation() throws Exception {
        activity("Board games", "2026-09-10T16:00:00Z", "2026-09-10T19:00:00Z");
        holiday("Seaside holiday", "2026-09-11", "2026-09-13");

        var activityOnly = calendar.load("2026-09", CalendarFilter.ACTIVITIES);
        assertThat(activityOnly.entries()).extracting(CalendarView.Entry::title).containsExactly("Board games");
        assertThat(activityOnly.days().stream().flatMap(day -> day.entries().stream()))
                .allMatch(entry -> entry.kind().equals("activity"));
        assertThat(activityOnly.activityCount()).isOne();
        assertThat(activityOnly.holidayCount()).isOne();

        mvc.perform(get("/calendar").param("month", "2026-09").param("show", "HOLIDAYS")
                        .with(user(alex.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Seaside holiday")))
                .andExpect(content().string(not(containsString("Board games"))))
                .andExpect(content().string(containsString("month=2026-08&amp;show=HOLIDAYS")))
                .andExpect(content().string(containsString("month=2026-10&amp;show=HOLIDAYS")));
    }

    @Test
    void overlappingEntriesAppearOnEveryCoveredDayButMidnightEndDoesNotOccupyTheNextDay() {
        var form = new ProfileForm();
        form.setColor("#7854ab");
        profileService.save(mina.getId(), mina.getEmail(), form);
        activity("Overnight", "2026-08-31T21:00:00Z", "2026-09-02T22:00:00Z");
        activity("Ends before September", "2026-08-31T18:00:00Z", "2026-08-31T22:00:00Z");
        activity("Starts in October", "2026-09-30T22:00:00Z", "2026-10-01T01:00:00Z");
        holiday("Cross-month holiday", "2026-08-29", "2026-09-02");
        holiday("One day away", "2026-09-30", "2026-09-30");

        var view = calendar.load("2026-09", CalendarFilter.ALL);
        assertThat(view.activityCount()).isOne();
        assertThat(view.holidayCount()).isEqualTo(2);
        assertThat(day(view, "2026-09-01").entries()).extracting(CalendarView.DayEntry::kind)
                .containsExactly("activity", "holiday");
        assertThat(day(view, "2026-09-01").entries().getLast().color()).isEqualTo("#7854ab");
        assertThat(day(view, "2026-09-01").entries().getLast().owner()).isEqualTo("Mina Member");
        form.setColor("#1480a0");
        profileService.save(mina.getId(), mina.getEmail(), form);
        assertThat(day(calendar.load("2026-09", CalendarFilter.ALL), "2026-09-01").entries().getLast().color())
                .isEqualTo("#1480a0");
        assertThat(day(view, "2026-09-02").entries()).hasSize(2);
        assertThat(day(view, "2026-09-03").entries()).isEmpty();
        assertThat(day(view, "2026-09-30").entries()).extracting(CalendarView.DayEntry::title)
                .containsExactly("One day away");
    }

    @Test
    void daylightSavingChangesDisplayTheActualBerlinOffsets() {
        activity("Spring clock change", "2026-03-29T00:30:00Z", "2026-03-29T01:30:00Z");
        activity("Autumn clock change", "2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z");

        assertThat(calendar.load("2026-03", CalendarFilter.ALL).entries().getFirst().period())
                .isEqualTo("29 März 2026, 01:30 MEZ – 29 März 2026, 03:30 MESZ");
        assertThat(calendar.load("2026-10", CalendarFilter.ALL).entries().getFirst().period())
                .isEqualTo("25 Okt. 2026, 02:30 MESZ – 25 Okt. 2026, 02:30 MEZ");
    }

    @Test
    void monthGridHandlesLeapDaysAndYearNavigation() throws Exception {
        holiday("Leap day", "2028-02-29", "2028-02-29");
        var leap = calendar.load("2028-02", CalendarFilter.HOLIDAYS);
        assertThat(leap.days()).filteredOn(CalendarView.Day::inMonth).hasSize(29);
        assertThat(day(leap, "2028-02-29").entries()).hasSize(1);
        assertThat(calendar.load("2026-01", CalendarFilter.ALL).previousMonth()).isEqualTo("2025-12");
        assertThat(calendar.load("2026-12", CalendarFilter.ALL).nextMonth()).isEqualTo("2027-01");
        mvc.perform(get("/calendar").param("month", "2028-02").with(user(mina.getEmail())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "nonsense", "0000-01", "+10000-01", "2026-9", ""})
    void invalidMonthsReturnBadRequest(String month) throws Exception {
        mvc.perform(get("/calendar").param("month", month).with(user(mina.getEmail())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidFilterReturnsBadRequest() throws Exception {
        mvc.perform(get("/calendar").param("show", "UNKNOWN").with(user(mina.getEmail())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatedDatabaseConstraintsRejectReversedPeriods() {
        var activity = activity("Valid activity", "2026-09-10T16:00:00Z", "2026-09-10T19:00:00Z");
        var holiday = holiday("Valid holiday", "2026-09-11", "2026-09-13");
        assertThatThrownBy(() -> jdbc.sql("UPDATE activities SET ends_at = starts_at WHERE id = :id")
                .param("id", activity.getId()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE holidays SET ends_on = DATE '2026-09-01' WHERE id = :id")
                .param("id", holiday.getId()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Activity activity(String title, String start, String end) {
        return activities.save(new Activity(title, "Berlin", "Bring snacks.",
                OffsetDateTime.parse(start), OffsetDateTime.parse(end), alex));
    }

    private Holiday holiday(String title, String start, String end) {
        return holidays.save(new Holiday(mina, title, "Away with friends.", LocalDate.parse(start),
                LocalDate.parse(end), OffsetDateTime.parse("2026-08-01T10:00:00Z")));
    }

    private CalendarView.Day day(CalendarView view, String date) {
        return view.days().stream().filter(day -> day.date().equals(LocalDate.parse(date))).findFirst().orElseThrow();
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-31T22:30:00Z"), ZoneOffset.UTC);
        }
    }
}
