package de.tyro.project11;

import de.tyro.project11.registration.RegistrationForm;
import de.tyro.project11.registration.RegistrationService;
import de.tyro.project11.registration.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dashboard-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DashboardIntegrationTests {

    private static final String PASSWORD = "river stone morning 42";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired RegistrationService registrations;
    @Autowired UserRepository users;

    long adminId;
    long memberId;

    @BeforeEach
    void createUsers() {
        jdbc.sql("DELETE FROM app_users").update();
        registrations.register(form("Ada Admin", "ada@example.com"));
        registrations.register(form("Mina Member", "mina@example.com"));
        adminId = users.findByEmail("ada@example.com").orElseThrow().getId();
        memberId = users.findByEmail("mina@example.com").orElseThrow().getId();
    }

    @Test
    void generatedSchemaSuppliesDefaultsAndRejectsNegativeTallies() {
        jdbc.sql("INSERT INTO app_users (display_name, email, password_hash) VALUES ('SQL User', 'sql@example.com', 'test-hash')")
                .update();
        var inserted = users.findByEmail("sql@example.com").orElseThrow();
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.isAdmin()).isFalse();
        assertThat(inserted.getTallyCount()).isZero();

        assertThatThrownBy(() -> jdbc.sql("UPDATE app_users SET tally_count = -1 WHERE id = :id")
                .param("id", inserted.getId()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void firstAccountIsAdminAndDashboardShowsEveryUsersTally() throws Exception {
        assertThat(users.findById(adminId).orElseThrow().isAdmin()).isTrue();
        assertThat(users.findById(memberId).orElseThrow().isAdmin()).isFalse();

        mvc.perform(get("/welcome").with(user("ada@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Das steht als Nächstes an.")))
                .andExpect(content().string(containsString("Ada Admin")))
                .andExpect(content().string(containsString("Mina Member")))
                .andExpect(content().string(containsString("Striche bearbeiten")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("class=\"admin-controls\""))))
                .andExpect(content().string(containsString("href=\"/users/" + memberId + "\"")));
    }

    @Test
    void editModeIsExplicitAndRestrictedToAdmins() throws Exception {
        mvc.perform(get("/welcome").param("editTallies", "true").with(user("ada@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bearbeitung beenden")))
                .andExpect(content().string(containsString("class=\"admin-controls\"")))
                .andExpect(content().string(containsString("Genaue Anzahl festlegen")))
                .andExpect(content().string(containsString("name=\"editTallies\" value=\"true\"")));
        mvc.perform(get("/welcome").param("editTallies", "true").with(user("mina@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"admin-controls\""))))
                .andExpect(content().string(not(containsString("Bearbeitung beenden"))));
        mvc.perform(post("/users/{id}/tally", memberId).with(user("ada@example.com")).with(csrf())
                        .param("change", "INCREMENT").param("editTallies", "true"))
                .andExpect(redirectedUrl("/welcome?editTallies=true#user-" + memberId));
        assertThat(users.findById(memberId).orElseThrow().getTallyCount()).isEqualTo(1);
    }

    @Test
    void sevenMembersHaveProfileLinksAndAGroupOverview() throws Exception {
        for (int i = 0; i < 5; i++) registrations.register(form("Person " + i, "person" + i + "@example.com"));
        var html = mvc.perform(get("/welcome").with(user("ada@example.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("7 Personen", "tally-overview");
        for (var account : users.findAll()) assertThat(html).contains("href=\"/users/" + account.getId() + "\"");
        assertThat(html).doesNotContain("class=\"admin-controls\"");
    }

    @Test
    void regularUsersSeeTalliesButCannotEditThem() throws Exception {
        mvc.perform(get("/welcome").with(user("mina@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Striche bearbeiten"))))
                .andExpect(content().string(not(containsString("class=\"admin-controls\""))));

        mvc.perform(post("/users/{id}/tally", memberId)
                        .with(user("mina@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("change", "INCREMENT"))
                .andExpect(status().isForbidden());
        assertThat(tallyFor(memberId)).isZero();
    }

    @Test
    void adminCanUseQuickAndExactTallyEdits() throws Exception {
        mvc.perform(post("/users/{id}/tally", memberId)
                        .with(user("ada@example.com"))
                        .with(csrf())
                        .param("change", "ADD_FIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/welcome#user-" + memberId));
        assertThat(tallyFor(memberId)).isEqualTo(5);

        mvc.perform(post("/users/{id}/tally", memberId)
                        .with(user("ada@example.com"))
                        .with(csrf())
                        .param("change", "SET")
                        .param("value", "17"))
                .andExpect(status().is3xxRedirection());
        assertThat(tallyFor(memberId)).isEqualTo(17);

        mvc.perform(post("/users/{id}/tally", memberId)
                        .with(user("ada@example.com"))
                        .with(csrf())
                        .param("change", "RESET"))
                .andExpect(status().is3xxRedirection());
        assertThat(tallyFor(memberId)).isZero();
    }

    @Test
    void adminCanPromoteAUserAndPromotionIsEffectiveImmediately() throws Exception {
        mvc.perform(post("/users/{id}/admin", memberId)
                        .with(user("ada@example.com"))
                        .with(csrf())
                        .param("admin", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findById(memberId).orElseThrow().isAdmin()).isTrue();

        // The persisted role is authoritative, even if an existing session still
        // contains the ROLE_USER authority from before the promotion.
        mvc.perform(post("/users/{id}/tally", adminId)
                        .with(user("mina@example.com").roles("USER"))
                        .with(csrf())
                        .param("change", "INCREMENT"))
                .andExpect(status().is3xxRedirection());
        assertThat(tallyFor(adminId)).isOne();
    }

    @Test
    void finalAdminCannotBeRemovedAndEditsRequireCsrf() throws Exception {
        mvc.perform(post("/users/{id}/admin", adminId)
                        .with(user("ada@example.com"))
                        .with(csrf())
                        .param("admin", "false"))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(adminId).orElseThrow().isAdmin()).isTrue();

        mvc.perform(post("/users/{id}/tally", memberId)
                        .with(user("ada@example.com"))
                        .param("change", "INCREMENT"))
                .andExpect(status().isForbidden());
        assertThat(tallyFor(memberId)).isZero();
    }

    @Test
    void exactTallyMustStayInsideSupportedRange() throws Exception {
        mvc.perform(post("/users/{id}/tally", memberId)
                        .with(user("ada@example.com"))
                        .with(csrf())
                        .param("change", "SET")
                        .param("value", "1000"))
                .andExpect(status().isBadRequest());
        assertThat(tallyFor(memberId)).isZero();
    }

    private int tallyFor(long id) {
        return jdbc.sql("SELECT tally_count FROM app_users WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    private RegistrationForm form(String name, String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName(name);
        form.setEmail(email);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        return form;
    }
}
