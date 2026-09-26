package de.tyro.project11.account;

import de.tyro.project11.registration.RegistrationForm;
import de.tyro.project11.registration.RegistrationService;
import de.tyro.project11.registration.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:account-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountIntegrationTests {

    private static final String EMAIL = "alex@example.com";
    private static final String PASSWORD = "river stone morning 42";
    private static final String NEW_PASSWORD = "even longer forest path 73";
    private static final String RAW_TOKEN = "A".repeat(43);

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registrations;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired Clock clock;
    @Autowired PasswordResetService resets;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    PasswordResetMailSender mailer;

    @BeforeEach
    void createAccount() {
        tokens.deleteAll();
        users.deleteAll();
        registrations.register(registration(EMAIL, PASSWORD));
    }

    @Test
    void forgotPasswordIsPublicGenericAndCsrfProtected() throws Exception {
        mvc.perform(get("/signin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Passwort vergessen?")));
        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reset-Link anfordern")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mvc.perform(post("/forgot-password").param("email", EMAIL))
                .andExpect(status().isForbidden());
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "invalid"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("forgotPasswordForm", "email"));

        mvc.perform(post("/forgot-password").with(csrf()).param("email", " UNKNOWN@example.com "))
                .andExpect(redirectedUrl("/forgot-password/sent"));
        assertThat(tokens.count()).isZero();

        mvc.perform(post("/forgot-password").with(csrf()).param("email", " ALEX@EXAMPLE.COM "))
                .andExpect(redirectedUrl("/forgot-password/sent"));
        assertThat(tokens.count()).isOne();
        assertThat(tokens.findAll().getFirst().getExpiresAt())
                .isAfter(tokens.findAll().getFirst().getCreatedAt());

        // Repeated requests inside the short throttle window do not create mail floods.
        mvc.perform(post("/forgot-password").with(csrf()).param("email", EMAIL))
                .andExpect(redirectedUrl("/forgot-password/sent"));
        assertThat(tokens.count()).isOne();
    }

    @Test
    void oneTimeResetChangesPasswordAndCannotBeReused() throws Exception {
        saveToken(RAW_TOKEN, OffsetDateTime.now(clock).plusMinutes(30));

        mvc.perform(get("/reset-password").param("token", RAW_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(containsString("Neues Passwort setzen")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mvc.perform(post("/reset-password").with(csrf())
                        .param("token", RAW_TOKEN)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/signin?passwordReset"));

        var account = users.findByEmail(EMAIL).orElseThrow();
        assertThat(passwords.matches(PASSWORD, account.getPasswordHash())).isFalse();
        assertThat(passwords.matches(NEW_PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(tokens.findAll().getFirst().getUsedAt()).isNotNull();

        mvc.perform(get("/reset-password").param("token", RAW_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Link nicht mehr gültig")))
                .andExpect(content().string(not(containsString("name=\"token\""))));
    }

    @Test
    void expiredMalformedAndInvalidPasswordResetsAreRejected() throws Exception {
        saveToken(RAW_TOKEN, OffsetDateTime.now(clock).minusSeconds(1));
        mvc.perform(get("/reset-password").param("token", RAW_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Link nicht mehr gültig")));
        mvc.perform(get("/reset-password").param("token", "not-a-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Link nicht mehr gültig")));

        tokens.deleteAll();
        saveToken(RAW_TOKEN, OffsetDateTime.now(clock).plusMinutes(30));
        mvc.perform(post("/reset-password").with(csrf())
                        .param("token", RAW_TOKEN)
                        .param("newPassword", "short")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("resetPasswordForm", "newPassword", "passwordsMatching"))
                .andExpect(content().string(not(containsString("value=\"short\""))))
                .andExpect(content().string(not(containsString("value=\"different\""))));
        assertThat(passwords.matches(PASSWORD, users.findByEmail(EMAIL).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void ownerCanChangeEmailOnlyWithCurrentPasswordAndUniqueAddress() throws Exception {
        registrations.register(registration("taken@example.com", "another safe password 11"));
        mvc.perform(get("/account").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Kontoeinstellungen")));

        mvc.perform(post("/account/email").with(user(EMAIL)).with(csrf())
                        .param("email", "new@example.com").param("currentPassword", "wrong password here"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("emailChangeForm", "currentPassword"));
        mvc.perform(post("/account/email").with(user(EMAIL)).with(csrf())
                        .param("email", "taken@example.com").param("currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("emailChangeForm", "email"));

        mvc.perform(post("/account/email").with(user(EMAIL)).with(csrf())
                        .param("email", " NEW@example.com ").param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/signin?emailChanged"));
        assertThat(users.findByEmail(EMAIL)).isEmpty();
        assertThat(users.findByEmail("new@example.com")).isPresent();
    }

    @Test
    void ownerCanChangePasswordAndAllResetTokensAreInvalidated() throws Exception {
        saveToken(RAW_TOKEN, OffsetDateTime.now(clock).plusMinutes(30));
        mvc.perform(post("/account/password").with(user(EMAIL)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/signin?passwordChanged"));

        var account = users.findByEmail(EMAIL).orElseThrow();
        assertThat(passwords.matches(PASSWORD, account.getPasswordHash())).isFalse();
        assertThat(passwords.matches(NEW_PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(tokens.findAll().getFirst().getUsedAt()).isNotNull();

        mvc.perform(post("/account/password").with(user(EMAIL))
                        .param("currentPassword", NEW_PASSWORD)
                        .param("newPassword", "one more secure password")
                        .param("confirmPassword", "one more secure password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordResetRevokesAnAlreadyAuthenticatedSession() throws Exception {
        HttpSession session = mvc.perform(post("/signin").with(csrf())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/welcome").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk());

        saveToken(RAW_TOKEN, OffsetDateTime.now(clock).plusMinutes(30));
        mvc.perform(post("/reset-password").with(csrf())
                        .param("token", RAW_TOKEN)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/signin?passwordReset"));

        mvc.perform(get("/welcome").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(redirectedUrl("/signin?sessionExpired"));
    }

    @Test
    void generatedMailTokenIsUsableAndOnlyItsHashIsStored() {
        resets.requestReset(EMAIL);
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailer).send(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq("Alex Morgan"), capture.capture());
        String token = capture.getValue();
        assertThat(token).hasSize(43);
        assertThat(tokens.findByTokenHash(token)).isEmpty();
        assertThat(tokens.findByTokenHash(PasswordResetService.hashToken(token))).isPresent();
        assertThat(resets.resetPassword(token, NEW_PASSWORD)).isTrue();
        assertThat(resets.resetPassword(token, PASSWORD)).isFalse();
    }

    @Test
    void concurrentResetRequestsConsumeTokenExactlyOnce() throws Exception {
        saveToken(RAW_TOKEN, OffsetDateTime.now(clock).plusMinutes(30));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                return resets.resetPassword(RAW_TOKEN, NEW_PASSWORD);
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)
                    ^ second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(users.findByEmail(EMAIL).orElseThrow().getCredentialVersion()).isEqualTo(1);
    }

    private void saveToken(String rawToken, OffsetDateTime expiresAt) {
        var account = users.findByEmail(EMAIL).orElseThrow();
        tokens.saveAndFlush(new PasswordResetToken(
                account,
                PasswordResetService.hashToken(rawToken),
                OffsetDateTime.now(clock).minusMinutes(1),
                expiresAt
        ));
    }

    private RegistrationForm registration(String email, String password) {
        var form = new RegistrationForm();
        form.setDisplayName("Alex Morgan");
        form.setEmail(email);
        form.setPassword(password);
        form.setConfirmPassword(password);
        return form;
    }
}
