package de.tyro.project11;

import de.tyro.project11.attendance.*;
import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.profile.*;
import de.tyro.project11.registration.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profile-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProfileIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileService profiles;
    @Autowired UserProfileRepository profileRows;
    @Autowired BlogEntryRepository blogs;
    @Autowired ActivityRepository activities;
    @Autowired HolidayRepository holidays;
    @Autowired TravelApplicationRepository travel;
    @Autowired AttendanceRepository attendance;
    @Autowired AbsenceApplicationRepository absences;
    @Autowired TravelPhotoRepository photos;
    @Autowired JdbcClient jdbc;
    @Autowired Clock clock;
    AppUser owner, other;
    OffsetDateTime now;

    @BeforeEach
    void setup() {
        String key = UUID.randomUUID().toString();
        owner = users.save(new AppUser("Profilperson", key + "@example.com", "hash"));
        other = users.save(new AppUser("Andere Person", "other-" + key + "@example.com", "hash", true));
        now = OffsetDateTime.now(clock);
        jdbc.sql("update app_users set created_at = :created where id = :id")
                .param("created", now.minusYears(1)).param("id", owner.getId()).update();
    }

    @Test
    void existingUsersHaveProfilesWithoutMigrationAndNavigationLinks() throws Exception {
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Profilperson")))
                .andExpect(content().string(containsString("Wie viele Geschwister hast du?")))
                .andExpect(content().string(not(containsString("Profil bearbeiten"))));
        assertThat(profileRows.findById(owner.getId())).isEmpty();
        mvc.perform(get("/users/me").with(user(owner.getEmail())))
                .andExpect(redirectedUrl("/users/" + owner.getId()));
        mvc.perform(get("/welcome").with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/users/" + owner.getId() + "\"")));
        mvc.perform(get("/users/{id}", owner.getId())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/users/999999").with(user(owner.getEmail()))).andExpect(status().isNotFound());
    }

    @Test
    void savesOptionalProfileFieldsAndRevealsPaymentsOnlyAfterExplicitPost() throws Exception {
        mvc.perform(get("/users/{id}/edit", owner.getId()).with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Dein Steckbrief")));
        mvc.perform(post("/users/{id}/profile", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("color", "#7854ab").param("birthday", "1996-05-14")
                        .param("paypal", "https://paypal.me/secretperson").param("iban", "DE89 3704 0044 0532 0130 00")
                        .param("answers[movie]", "Arrival").param("answers[unknown]", "ignored"))
                .andExpect(redirectedUrl("/users/" + owner.getId()));
        var saved = profiles.edit(owner.getId(), owner.getEmail());
        assertThat(saved.getAnswers()).containsEntry("movie", "Arrival").doesNotContainKey("unknown");
        assertThat(saved.getIban()).isEqualTo("DE89370400440532013000");
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("14.05.1996")))
                .andExpect(content().string(containsString("#7854ab")))
                .andExpect(content().string(containsString("Arrival")))
                .andExpect(content().string(not(containsString("secretperson"))))
                .andExpect(content().string(not(containsString("DE89370400440532013000"))));
        mvc.perform(post("/users/{id}/payments", owner.getId()).with(user(other.getEmail())).with(csrf()).param("fragment", "true"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("secretperson")))
                .andExpect(content().string(containsString("DE89370400440532013000")));
        mvc.perform(post("/users/{id}/payments", owner.getId()).with(user(other.getEmail())).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Zahlungsdaten verbergen")));
        mvc.perform(get("/users/{id}/payments", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/payments", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/profile", owner.getId()).with(user(owner.getEmail())).with(csrf()).param("color", "#52734d"))
                .andExpect(status().is3xxRedirection());
        assertThat(profiles.edit(owner.getId(), owner.getEmail()).getAnswers()).isEmpty();
        assertThat(profiles.payments(owner.getId(), other.getEmail()).iban()).isEmpty();
    }

    @Test
    void enforcesOwnershipEvenForAdminsAndValidatesUntrustedProfileValues() throws Exception {
        mvc.perform(get("/users/{id}/edit", owner.getId()).with(user(other.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/profile", owner.getId()).with(user(other.getEmail())).with(csrf()).param("color", "#123456"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/profile", owner.getId()).with(user(owner.getEmail())).param("color", "#123456"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/profile", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("color", "red; background:url(https://example.com)")
                        .param("birthday", LocalDate.now(clock).plusDays(2).toString())
                        .param("iban", "DE00370400440532013000").param("paypal", "javascript:alert(1)")
                        .param("answers[movie]", "a".repeat(301)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bitte prüfe deine Angaben")));
        assertThat(profileRows.findById(owner.getId())).isEmpty();
        mvc.perform(post("/users/{id}/profile", owner.getId()).with(user(owner.getEmail())).with(csrf()).param("birthday", "invalid"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("gültiges Geburtsdatum")));
    }

    @Test
    void timelineConnectsReportsAndUsesRecordedAttendanceWithoutGuessing() throws Exception {
        var yes = event("Dabei gewesen", 7);
        var no = event("Nicht dabei", 6);
        var unknown = event("Noch offen", 5);
        var outside = event("Anderer Teilnehmerkreis", 4);
        record(yes, Set.of(owner, other), Set.of(owner));
        record(no, Set.of(owner, other), Set.of(other));
        record(outside, Set.of(other), Set.of(other));
        event("Zukunft bleibt im Kalender", -5);
        var today = LocalDate.now(clock.withZone(CalendarTime.BERLIN));
        var holiday = holidays.save(new Holiday(owner, "Italien", "Meer und Pasta", today.minusDays(15), today.minusDays(8), now.minusDays(20)));
        holidays.save(new Holiday(owner, "Zukünftiger Urlaub", "", today.plusDays(3), today.plusDays(6), now));
        holidays.save(new Holiday(other, "Fremder Urlaub", "", today.minusDays(20), today.minusDays(18), now));
        var report = travel.save(new TravelApplication(TravelKind.REPORT, UUID.randomUUID().toString(), owner, holiday, "Italienbericht", now, List.of()));
        var timeline = profiles.load(owner.getId(), other.getEmail()).timeline();
        assertThat(timeline).extracting(ProfileService.TimelineItem::title).contains("Italien", "Dabei gewesen", "Nicht dabei", "Noch offen")
                .doesNotContain("Zukünftiger Urlaub", "Fremder Urlaub", "Zukunft bleibt im Kalender");
        assertThat(timeline.stream().filter(i -> i.id() == yes.getId() && i.kind().equals("event")).findFirst().orElseThrow().status()).isEqualTo("Teilgenommen");
        assertThat(timeline.stream().filter(i -> i.id() == no.getId() && i.kind().equals("event")).findFirst().orElseThrow().status()).isEqualTo("Nicht teilgenommen");
        assertThat(timeline.stream().filter(i -> i.id() == unknown.getId() && i.kind().equals("event")).findFirst().orElseThrow().status()).isEqualTo("Teilnahme noch nicht erfasst");
        assertThat(timeline.stream().filter(i -> i.id() == outside.getId() && i.kind().equals("event")).findFirst().orElseThrow().status()).isEqualTo("Nicht im damaligen Teilnehmerkreis");
        assertThat(timeline.stream().filter(i -> i.title().equals("Italien")).findFirst().orElseThrow().reportId()).isEqualTo(report.getId());
        assertThat(timeline).extracting(ProfileService.TimelineItem::sortAt).isSortedAccordingTo(Comparator.reverseOrder());
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/amt/reisen/" + report.getId() + "\"")));
    }

    @Test
    void authorCanCreateEditAndDeleteStyledPostsAndTextIsEscaped() throws Exception {
        mvc.perform(get("/users/{id}/blog/new", owner.getId()).with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Textformatierung")));
        mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("title", "Ein Wochenende").param("blocks[0].text", "<script>alert('x')</script>")
                        .param("blocks[0].font", "mono").param("blocks[0].color", "#8546a2").param("blocks[0].bold", "true")
                        .param("blocks[1].text", "Ein Zitat").param("blocks[1].kind", "quote").param("blocks[1].italic", "true"))
                .andExpect(status().is3xxRedirection());
        long entryId = blogs.findByAuthorIdOrderByCreatedAtDescIdDesc(owner.getId()).getFirst().getId();
        var form = profiles.editBlog(owner.getId(), entryId, owner.getEmail());
        assertThat(form.getBlocks()).hasSize(2);
        assertThat(form.getBlocks().getFirst().getFont()).isEqualTo("mono");
        assertThat(form.getBlocks().getFirst().isBold()).isTrue();
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("font-mono")))
                .andExpect(content().string(containsString("<blockquote")));
        mvc.perform(get("/users/{id}/blog/{entryId}/edit", owner.getId(), entryId).with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Ein Wochenende")));
        mvc.perform(post("/users/{id}/blog/{entryId}", owner.getId(), entryId).with(user(owner.getEmail())).with(csrf())
                        .param("title", "Überarbeitet").param("blocks[0].text", "Nur ein Absatz"))
                .andExpect(status().is3xxRedirection());
        assertThat(profiles.editBlog(owner.getId(), entryId, owner.getEmail()).getBlocks()).hasSize(1);
        mvc.perform(post("/users/{id}/blog/{entryId}/delete", owner.getId(), entryId).with(user(owner.getEmail())).with(csrf()))
                .andExpect(redirectedUrl("/users/" + owner.getId()));
        assertThat(blogs.findById(entryId)).isEmpty();
    }

    @Test
    void blogOwnershipCsrfAndFormattingValidationCannotBeBypassed() throws Exception {
        var form = new BlogForm(); form.setTitle("Nur meins"); form.getBlocks().getFirst().setText("Text");
        long entryId = profiles.saveBlog(owner.getId(), null, owner.getEmail(), form);
        mvc.perform(get("/users/{id}/blog/{entryId}/edit", owner.getId(), entryId).with(user(other.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/blog/{entryId}", owner.getId(), entryId).with(user(other.getEmail())).with(csrf())
                        .param("title", "Fremde Änderung").param("blocks[0].text", "Text"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/blog/{entryId}/delete", other.getId(), entryId).with(user(other.getEmail())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/users/{id}/blog/{entryId}/delete", owner.getId(), entryId).with(user(owner.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("title", "Unsicher").param("blocks[0].text", "Text")
                        .param("blocks[0].color", "red;display:none").param("blocks[0].font", "evil").param("blocks[0].kind", "script"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bitte prüfe deinen Beitrag")));
        mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf()).param("title", ""))
                .andExpect(status().isOk());
        assertThat(blogs.findByAuthorIdOrderByCreatedAtDescIdDesc(owner.getId())).hasSize(1);
    }
    @Test
    void rejectsOversizedOrEmptyBlogCollectionsWithoutSaving() throws Exception {
        mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("title", "Zu viele Absätze").param("blocks[40].text", "Text"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("title", "Leerer Beitrag").param("blocks[2].text", "  "))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bitte prüfe deinen Beitrag")));
        assertThat(blogs.findByAuthorIdOrderByCreatedAtDescIdDesc(owner.getId())).isEmpty();
    }

    @Test
    void inlineSelectionsAndBlankParagraphsSurviveSavingAndReopening() throws Exception {
        String runs = """
                [{"text":"Normal ","font":"serif","color":"#203c32"},
                 {"text":"farbig","font":"mono","color":"#7854ab","bold":true,"underline":true},
                 {"text":" danach","font":"sans","color":"#203c32","italic":true,"strike":true}]
                """;
        mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf())
                        .param("title", "Formatierte Passagen").param("blocks[0].text", "Normal farbig danach")
                        .param("blocks[0].inlineContent", runs).param("blocks[1].text", "")
                        .param("blocks[1].inlineContent", "[]").param("blocks[2].text", "Neuer Absatz"))
                .andExpect(status().is3xxRedirection());
        long entryId = blogs.findByAuthorIdOrderByCreatedAtDescIdDesc(owner.getId()).getFirst().getId();
        var saved = profiles.editBlog(owner.getId(), entryId, owner.getEmail());
        assertThat(saved.getBlocks()).hasSize(3);
        assertThat(saved.getBlocks().get(1).getText()).isEmpty();
        assertThat(saved.getBlocks().getFirst().getRuns()).hasSize(3);
        assertThat(saved.getBlocks().getFirst().getRuns().get(1).underline()).isTrue();
        mvc.perform(get("/users/{id}/blog/{entryId}/edit", owner.getId(), entryId).with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-underline=\"true\"")))
                .andExpect(content().string(containsString("data-color=\"#7854ab\"")));
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("font-mono text-bold text-underline")))
                .andExpect(content().string(containsString("font-sans text-italic text-strike")));
    }

    @Test
    void invalidInlineContentCannotPersistExecutableMarkupOrMismatchedText() throws Exception {
        for (String json : List.of(
                "[{\"text\":\"Text\",\"font\":\"serif\",\"color\":\"red;display:none\"}]",
                "[{\"text\":\"Other\",\"font\":\"serif\",\"color\":\"#203c32\"}]",
                "[{\"text\":\"Text\",\"font\":\"serif\",\"color\":\"#203c32\",\"html\":\"<img src=x>\"}]",
                "not json", "[null]")) {
            mvc.perform(post("/users/{id}/blog", owner.getId()).with(user(owner.getEmail())).with(csrf())
                            .param("title", "Ungültig").param("blocks[0].text", "Text").param("blocks[0].inlineContent", json))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Bitte prüfe deinen Beitrag")));
        }
        assertThat(blogs.findByAuthorIdOrderByCreatedAtDescIdDesc(owner.getId())).isEmpty();
    }

    @Test
    void holidayAndSubmittedReportAreReadableInlineByOrdinaryMembersWithOnlyFiledPhotos() throws Exception {
        var reader = users.save(new AppUser("Leserin", UUID.randomUUID() + "@example.com", "hash"));
        var today = LocalDate.now(clock.withZone(CalendarTime.BERLIN));
        var holiday = holidays.save(new Holiday(owner, "Bergsommer", "Eine Woche in den Bergen", today.minusDays(10), today.minusDays(3), now.minusDays(20)));
        travel.save(new TravelApplication(TravelKind.LEAVE, UUID.randomUUID().toString(), owner, holiday, "Die Reise in die Berge", now.minusDays(20),
                List.of(new ApplicationAnswer("Reisepläne", "Wohin?", "Unser Ausgangspunkt ist Innsbruck."))));
        var report = travel.save(new TravelApplication(TravelKind.REPORT, UUID.randomUUID().toString(), owner, holiday, "Sonnenaufgang am Gipfel", now,
                List.of(new ApplicationAnswer("Reisebericht", "Dein Bericht", "Am Morgen ging es los.\nOben gab es Kaffee. <script>unsafe()</script>"))));
        String draft = UUID.randomUUID().toString();
        var image = photos.save(new TravelPhoto(owner, draft, "Gipfel.png", "image/png", new byte[]{1}, now));
        jdbc.sql("update travel_photos set application_id = :application where id = :id").param("application", report.getId()).param("id", image.getId()).update();
        var privateImage = photos.save(new TravelPhoto(owner, UUID.randomUUID().toString(), "Entwurf-geheim.png", "image/png", new byte[]{1}, now));
        mvc.perform(get("/users/{id}", owner.getId()).with(user(reader.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unser Ausgangspunkt ist Innsbruck.")))
                .andExpect(content().string(containsString("Am Morgen ging es los.")))
                .andExpect(content().string(containsString("Oben gab es Kaffee. &lt;script&gt;unsafe()&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>unsafe()"))))
                .andExpect(content().string(containsString("src=\"/amt/fotos/" + image.getId() + "\"")))
                .andExpect(content().string(not(containsString("/amt/fotos/" + privateImage.getId() + "\""))))
                .andExpect(content().string(not(containsString("Entwurf-geheim"))));
        var story = profiles.load(owner.getId(), reader.getEmail()).timeline().stream().filter(i -> i.kind().equals("holiday") && i.id() == holiday.getId()).findFirst().orElseThrow();
        assertThat(story.report().passages()).hasSize(1);
        assertThat(story.report().photos()).hasSize(1);
    }

    @Test
    void aaaIsAttachedToItsEventAndDoesNotReplaceActualAttendanceOrLeakAnotherUsersApplication() throws Exception {
        var event = event("Abend am See", 2); record(event, Set.of(owner, other), Set.of(owner));
        absences.save(new AbsenceApplication(owner, event, now.minusDays(1), List.of(
                new ApplicationAnswer("Begründung", "Warum?", "Ich hatte ursprünglich einen anderen Termin."),
                new ApplicationAnswer("Erklärung", "Genauer?", "Der Termin wurde später verschoben."))));
        absences.save(new AbsenceApplication(other, event, now.minusDays(1), List.of(new ApplicationAnswer("Grund", "Warum?", "FremderAntragNurImAnderenProfil"))));
        var item = profiles.load(owner.getId(), other.getEmail()).timeline().stream().filter(i -> i.kind().equals("event") && i.id() == event.getId()).findFirst().orElseThrow();
        assertThat(item.status()).isEqualTo("Teilgenommen");
        assertThat(item.absence().passages()).hasSize(2);
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Ich hatte ursprünglich einen anderen Termin.")))
                .andExpect(content().string(containsString("Der Termin wurde später verschoben.")))
                .andExpect(content().string(containsString("Teilgenommen")))
                .andExpect(content().string(not(containsString("FremderAntragNurImAnderenProfil"))));
    }

    @Test
    void submittedAaasRemainReadableForUpcomingAndCancelledEvents() throws Exception {
        var upcoming = event("Nächste Woche", -7);
        var cancelled = event("Abgesagtes Treffen", -6); cancelled.cancel("Regen", now); activities.save(cancelled);
        absences.save(new AbsenceApplication(owner, upcoming, now, List.of(new ApplicationAnswer("A", "Grund", "Bereits eingereichte Erklärung für nächste Woche"))));
        absences.save(new AbsenceApplication(owner, cancelled, now, List.of(new ApplicationAnswer("A", "Grund", "Erklärung bleibt trotz Eventabsage lesbar"))));
        mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bereits eingereichte Erklärung für nächste Woche")))
                .andExpect(content().string(containsString("Erklärung bleibt trotz Eventabsage lesbar")))
                .andExpect(content().string(containsString("Event noch nicht beendet")))
                .andExpect(content().string(containsString("Event abgesagt")));
    }

    @Test
    void applicationsAndReportsStartCollapsedAndTermsAreSummarizedWithoutChangingStoredDocuments() throws Exception {
        var today = LocalDate.now(clock.withZone(CalendarTime.BERLIN));
        var holiday = holidays.save(new Holiday(owner, "Kurze Reise", "Meer", today.minusDays(5), today.minusDays(2), now.minusDays(8)));
        var leave = travel.save(new TravelApplication(TravelKind.LEAVE, UUID.randomUUID().toString(), owner, holiday, "Reiseantrag", now.minusDays(8),
                List.of(new ApplicationAnswer("Reise", "Ziel", "Küste"), PortalTerms.acceptedSnapshot())));
        travel.save(new TravelApplication(TravelKind.REPORT, UUID.randomUUID().toString(), owner, holiday, "Mein Reisebericht", now,
                List.of(new ApplicationAnswer("Bericht", "Dein Bericht", "Wir waren am Strand."))));
        var activity = event("Abendessen", 3);
        var absence = absences.save(new AbsenceApplication(owner, activity, now.minusDays(4), List.of(
                new ApplicationAnswer("Begründung", "Warum?", "War auf Reisen."), PortalTerms.acceptedSnapshot())));
        String html = mvc.perform(get("/users/{id}", owner.getId()).with(user(other.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Akzeptiert.")))
                .andExpect(content().string(not(containsString(PortalTerms.CURRENT.clauses().getFirst().text()))))
                .andReturn().getResponse().getContentAsString();
        var disclosures = java.util.regex.Pattern.compile("<details\\b[^>]*>").matcher(html).results()
                .map(java.util.regex.MatchResult::group).filter(tag -> tag.contains("profile-story")).toList();
        assertThat(disclosures).hasSize(3).allSatisfy(tag -> assertThat(tag).doesNotContain(" open"));
        assertThat(java.util.regex.Pattern.compile("<summary class=\"profile-story-heading\">").matcher(html).results().count()).isEqualTo(3);
        assertThat(profiles.load(owner.getId(), other.getEmail()).timeline().stream()
                .filter(i -> i.leave() != null && i.leave().id() == leave.getId()).findFirst().orElseThrow().leave().passages())
                .anySatisfy(p -> { assertThat(p.question()).startsWith("Allgemeine Gruppenbedingungen"); assertThat(p.text()).isEqualTo("Akzeptiert."); });
        String retained = jdbc.sql("select answer_text from absence_application_answers where application_id = :id and question_text like 'Allgemeine Gruppenbedingungen%'")
                .param("id", absence.getId()).query(String.class).single();
        assertThat(retained).contains(PortalTerms.CURRENT.clauses().getFirst().text());
    }

    private Activity event(String title, int daysAgo) {
        return activities.save(new Activity(title, "Darmstadt", "Zusammen unterwegs", now.minusDays(daysAgo).minusHours(2), now.minusDays(daysAgo), owner));
    }
    private void record(Activity event, Set<AppUser> roster, Set<AppUser> present) {
        var sheet = new AttendanceSheet(event, roster); sheet.record(present, other, now); attendance.save(sheet);
    }
}
