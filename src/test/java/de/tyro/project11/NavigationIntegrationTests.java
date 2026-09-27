package de.tyro.project11;

import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:navigation-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class NavigationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test void sharedMenuAppearsAcrossSectionsWithExactlyOneActiveLink() throws Exception {
        var member = users.save(new AppUser("Menu member", "menu@example.com", "unused", true));
        String[][] routes = {
                {"/welcome", "/welcome"}, {"/calendar", "/calendar"}, {"/events/new", "/calendar"},
                {"/polls", "/polls"}, {"/polls/new", "/polls"}, {"/costs", "/costs"},
                {"/users/" + member.getId(), "/users/me"}, {"/account", "/account"},
                {"/amt", "/amt"}, {"/admin/aaa", "/amt"}
        };
        for (var route : routes) {
            String html = mvc.perform(get(route[0]).with(user(member.getEmail())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String header = header(html);
            if (!route[0].startsWith("/amt") && !route[0].startsWith("/admin"))
                assertThat(html).doesNotContain("Berlin", "MEZ / MESZ");
            assertThat(header).contains("aria-label=\"Hauptnavigation\"", "action=\"/logout\"", "name=\"_csrf\"");
            assertThat(header.split("aria-current=\"page\"", -1)).hasSize(2);
            assertThat(header).contains("href=\"" + route[1] + "\" aria-current=\"page\"");
            for (String link : new String[]{"/welcome", "/calendar", "/polls", "/amt", "/costs", "/users/me", "/account"})
                assertThat(header).contains("href=\"" + link + "\"");
        }
    }

    @Test void publicPagesHaveOnlyPublicAccountLinks() throws Exception {
        for (String route : new String[]{"/signin", "/register", "/forgot-password", "/reset-password"}) {
            String html = mvc.perform(get(route)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(header(html)).contains("href=\"/signin\"", "href=\"/register\"")
                    .doesNotContain("Hauptnavigation", "action=\"/logout\"");
        }
    }
    private String header(String html) {
        String start = "<header class=\"app-header\">";
        assertThat(html.split(java.util.regex.Pattern.quote(start), -1)).hasSize(2);
        int index = html.indexOf(start);
        return html.substring(index, html.indexOf("</header>", index));
    }
}
