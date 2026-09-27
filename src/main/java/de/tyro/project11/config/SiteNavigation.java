package de.tyro.project11.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Shared navigation state; templates never need to know their active section. */
@Component("siteNavigation")
public class SiteNavigation {
    public record Navigation(boolean signedIn, String active) {}

    public Navigation current() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String active = "";
        if (path.equals("/") || path.equals("/welcome")) active = "welcome";
        else if (within(path, "/costs") || path.matches("/events/[^/]+/costs(?:/.*)?")) active = "costs";
        else if (within(path, "/calendar") || within(path, "/events") || path.equals("/attendance-problem")) active = "calendar";
        else if (within(path, "/polls")) active = "polls";
        else if (within(path, "/amt") || within(path, "/admin")) active = "applications";
        else if (within(path, "/users")) active = "profile";
        else if (within(path, "/account")) active = "account";
        else if (path.equals("/register")) active = "register";
        else if (path.equals("/signin")) active = "signin";
        return new Navigation(signedIn, active);
    }

    private boolean within(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }
}
