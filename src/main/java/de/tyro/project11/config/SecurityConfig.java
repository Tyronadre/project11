package de.tyro.project11.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import de.tyro.project11.auth.CredentialVersionFilter;

@Configuration
public class SecurityConfig {

    private final CredentialVersionFilter credentialVersions;

    public SecurityConfig(de.tyro.project11.registration.UserRepository users) {
        // Only register inside the security chain, after the session context is loaded.
        this.credentialVersions = new CredentialVersionFilter(users);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/signin", "/register", "/register/success",
                        "/forgot-password", "/forgot-password/sent", "/reset-password", "/css/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/register", "/forgot-password", "/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/account").authenticated()
                .requestMatchers(HttpMethod.GET, "/polls", "/polls/new", "/polls/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/polls", "/polls/*/vote", "/polls/*/finish", "/polls/*/close").authenticated()
                .requestMatchers(HttpMethod.POST, "/welcome/decisions/*/*/read").authenticated()
                .requestMatchers(HttpMethod.POST, "/account/name", "/account/email", "/account/password").authenticated()
                .requestMatchers(HttpMethod.GET, "/welcome", "/calendar", "/js/calendar.js", "/events/new", "/events/*/edit", "/events/*", "/events/*/attendance").authenticated()
                .requestMatchers(HttpMethod.GET, "/admin/aaa", "/admin/aaa/*", "/admin/travel", "/admin/travel/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/admin/aaa/*", "/admin/travel/*", "/events/*/attendance/confirm").authenticated()
                .requestMatchers(HttpMethod.POST, "/events", "/events/*/edit", "/events/*/cancel", "/events/*/rsvp", "/events/*/afea", "/events/*/afea/withdraw", "/events/*/attendance").authenticated()
                .requestMatchers(HttpMethod.GET, "/costs", "/costs/new", "/events/*/costs", "/events/*/costs/new", "/events/*/costs/*/edit").authenticated()
                .requestMatchers(HttpMethod.POST, "/events/*/costs", "/events/*/costs/*", "/events/*/costs/*/reopen",
                        "/events/*/costs/*/shares/*/paid", "/events/*/costs/*/shares/*/unpaid").authenticated()
                .requestMatchers(HttpMethod.GET, "/amt/bub", "/amt/bub/neu", "/amt/bub/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/amt/bub", "/amt/bub/*/close", "/amt/bub/*/rate").authenticated()
                .requestMatchers(HttpMethod.GET, "/amt", "/amt/", "/amt/akten", "/amt/mitglieder/*", "/amt/antraege/*",
                        "/amt/antraege/*/bescheid", "/amt/reisen/*/bescheid", "/js/amt-print.js",
                        "/amt/aaa", "/amt/aaa/pruefung", "/amt/aaa/freigabe", "/js/amt.js", "/js/amt-steps.js",
                        "/amt/aab", "/amt/aab/pruefung", "/amt/aab/freigabe",
                        "/amt/eer", "/amt/eer/pruefung", "/amt/eer/freigabe", "/amt/reisen/*", "/amt/fotos/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/amt/aaa/pruefen", "/amt/aaa/bestaetigen", "/amt/aaa/einreichen",
                        "/amt/aab/pruefen", "/amt/aab/bestaetigen", "/amt/aab/einreichen",
                        "/amt/eer/pruefen", "/amt/eer/bestaetigen", "/amt/eer/einreichen", "/amt/eer/fotos/*/entfernen").authenticated()
                .requestMatchers(HttpMethod.GET, "/users/me", "/users/*", "/users/*/edit",
                        "/users/*/blog/new", "/users/*/blog/*/edit", "/js/profile-editor.js", "/js/profile.js", "/js/vendor/quill-2.0.3.js").authenticated()
                .requestMatchers(HttpMethod.POST, "/users/*/profile", "/users/*/payments", "/users/*/blog",
                        "/users/*/blog/*", "/users/*/blog/*/delete").authenticated()
                // DashboardService re-checks the persisted admin flag for every edit.
                // This also makes promotions/demotions effective without a new login.
                .requestMatchers(HttpMethod.POST, "/users/*/tally", "/users/*/admin").authenticated()
                .anyRequest().denyAll())
                .formLogin(form -> form
                        .loginPage("/signin")
                        .loginProcessingUrl("/signin")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/welcome", false)
                        .failureUrl("/signin?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/signin?logout")
                        .deleteCookies("JSESSIONID"))
                .addFilterAfter(credentialVersions, UsernamePasswordAuthenticationFilter.class);
        // CSRF protection stays enabled. Thymeleaf inserts the token into POST forms.
        return http.build();
    }
}
