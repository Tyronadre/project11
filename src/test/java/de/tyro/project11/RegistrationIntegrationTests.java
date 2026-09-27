package de.tyro.project11;

import de.tyro.project11.registration.EmailAlreadyRegisteredException;
import de.tyro.project11.registration.RegistrationForm;
import de.tyro.project11.registration.RegistrationService;
import de.tyro.project11.registration.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registration-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RegistrationIntegrationTests {

    private static final String PASSWORD = "river stone morning 42";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired RegistrationService registrations;
    @Autowired UserRepository users;

    @BeforeEach
    void clearUsers() {
        jdbc.sql("DELETE FROM app_users").update();
    }

    @Test
    void pageAndStylesArePublicAndFormContainsCsrfToken() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signin"));
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Konto erstellen")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void registrationPersistsNormalizedDetailsAndHashedPasswordThenRedirects() throws Exception {
        var result = mvc.perform(post("/register").with(csrf())
                        .param("displayName", "  Alex Morgan  ").param("email", "  ALEX@Example.com  ")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/success"))
                .andReturn();

        var user = jdbc.sql("SELECT display_name, email, password_hash FROM app_users").query().singleRow();
        assertThat(user.get("display_name")).isEqualTo("Alex Morgan");
        assertThat(user.get("email")).isEqualTo("alex@example.com");
        String hash = (String) user.get("password_hash");
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(jdbc.sql("SELECT created_at FROM app_users").query().singleRow().get("created_at")).isNotNull();

        var entity = users.findByEmail("alex@example.com").orElseThrow();
        assertThat(entity.getId()).isPositive();
        assertThat(entity.getDisplayName()).isEqualTo("Alex Morgan");
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(users.existsByEmail("alex@example.com")).isTrue();

        mvc.perform(get("/register/success").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Konto erstellt")))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @ParameterizedTest
    @CsvSource({
            "'', alex@example.com, river-stone-morning, river-stone-morning, displayName",
            "Alex, not-an-email, river-stone-morning, river-stone-morning, email",
            "Alex, '', river-stone-morning, river-stone-morning, email",
            "Alex, alex@example.com, abc, abc, password",
            "Alex, alex@example.com, river-stone-morning, different-password, passwordsMatching",
            "Alex, alex@example.com, river-stone-morning, '', confirmPassword"
    })
    void invalidFormsShowErrorsWithoutWritingAUser(String name, String email, String password,
                                                  String confirmation, String errorField) throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", name).param("email", email)
                        .param("password", password).param("confirmPassword", confirmation))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", errorField))
                .andExpect(content().string(not(containsString("value=\"" + password + "\""))));
        assertThat(countUsers()).isZero();
    }

    @Test
    void oversizedFieldsAreRejected() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "a".repeat(81))
                        .param("email", "a".repeat(245) + "@example.com")
                        .param("password", "a".repeat(73)).param("confirmPassword", "a".repeat(73)))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", "displayName", "email", "password", "confirmPassword"));
        assertThat(countUsers()).isZero();
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndDoesNotReplaceOriginalUser() throws Exception {
        registrations.register(form("alex@example.com", PASSWORD));
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Someone else").param("email", " ALEX@EXAMPLE.COM ")
                        .param("password", "another good password").param("confirmPassword", "another good password"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", "email"))
                .andExpect(content().string(containsString("Diese E-Mail-Adresse ist bereits registriert.")));
        assertThat(countUsers()).isEqualTo(1);
        assertThat(passwords.matches(PASSWORD, jdbc.sql("SELECT password_hash FROM app_users").query(String.class).single())).isTrue();
    }

    @Test
    void missingOrInvalidCsrfTokenCannotRegister() throws Exception {
        mvc.perform(post("/register").param("displayName", "Alex").param("email", "alex@example.com")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/register").with(csrf().useInvalidToken())
                        .param("displayName", "Alex").param("email", "alex@example.com")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isForbidden());
        assertThat(countUsers()).isZero();
    }

    @Test
    void successPageRequiresACompletedRegistration() throws Exception {
        mvc.perform(get("/register/success"))
                .andExpect(redirectedUrl("/register"));
    }

    @Test
    void bcryptByteLimitHandlesUnicodeWithoutTruncation() throws Exception {
        String tooLong = "é".repeat(37);
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Alex").param("email", "alex@example.com")
                        .param("password", tooLong).param("confirmPassword", tooLong))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", "passwordWithinByteLimit"));
        assertThat(countUsers()).isZero();

        String atLimit = "é".repeat(36);
        registrations.register(form("alex@example.com", atLimit));
        assertThat(passwords.matches(atLimit, jdbc.sql("SELECT password_hash FROM app_users").query(String.class).single())).isTrue();
    }

    @Test
    void namesAreBoundAsSqlDataAndEscapedWhenRedisplayed() throws Exception {
        String name = "<script>alert('x')</script>";
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", name).param("email", "invalid")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(name))))
                .andExpect(content().string(containsString("&lt;script&gt;")));

        RegistrationForm form = form("alex@example.com", PASSWORD);
        form.setDisplayName("Alex'); DROP TABLE app_users; --");
        registrations.register(form);
        assertThat(jdbc.sql("SELECT display_name FROM app_users").query(String.class).single()).isEqualTo(form.getDisplayName());
    }

    @Test
    void concurrentRegistrationsForOneEmailCreateExactlyOneUser() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempt = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try {
                    registrations.register(form("alex@example.com", PASSWORD));
                    return true;
                } catch (EmailAlreadyRegisteredException exception) {
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(countUsers()).isEqualTo(1);
    }

    private long countUsers() {
        return jdbc.sql("SELECT COUNT(*) FROM app_users").query(Long.class).single();
    }

    private RegistrationForm form(String email, String password) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Alex Morgan");
        form.setEmail(email);
        form.setPassword(password);
        form.setConfirmPassword(password);
        return form;
    }
}
