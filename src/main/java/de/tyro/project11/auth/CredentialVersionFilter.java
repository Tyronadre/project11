package de.tyro.project11.auth;

import de.tyro.project11.registration.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CredentialVersionFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();

    public CredentialVersionFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            boolean current = users.findById(principal.userId())
                    .filter(user -> user.getCredentialVersion() == principal.credentialVersion())
                    .filter(user -> user.getEmail().equals(principal.getUsername()))
                    .isPresent();
            if (!current) {
                logout.logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/signin?sessionExpired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
