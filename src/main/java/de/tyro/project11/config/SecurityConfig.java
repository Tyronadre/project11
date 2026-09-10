package de.tyro.project11.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/signin", "/register", "/register/success", "/css/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/welcome", "/calendar").authenticated()
                // DashboardService re-checks the persisted admin flag for every edit.
                // This also makes promotions/demotions effective without a new login.
                .requestMatchers(HttpMethod.POST, "/users/*/tally", "/users/*/admin").authenticated()
                .anyRequest().denyAll())
                .formLogin(form -> form
                        .loginPage("/signin")
                        .loginProcessingUrl("/signin")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/welcome", true)
                        .failureUrl("/signin?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/signin?logout")
                        .deleteCookies("JSESSIONID"));
        // CSRF protection stays enabled. Thymeleaf inserts the token into POST forms.
        return http.build();
    }
}
