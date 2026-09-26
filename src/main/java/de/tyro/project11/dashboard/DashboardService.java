package de.tyro.project11.dashboard;

import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class DashboardService {

    public static final int MAX_TALLY = 999;

    private final UserRepository users;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public DashboardService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DashboardView load(String email) {
        AppUser currentUser = findUserByEmail(email);
        var tallies = users.findAllByOrderByDisplayNameAsc().stream()
                .map(TallyView::from)
                .toList();
        int total = tallies.stream().mapToInt(TallyView::tallyCount).sum();
        return new DashboardView(
                currentUser.getId(),
                currentUser.getDisplayName(),
                currentUser.isAdmin(),
                tallies.stream().filter(TallyView::admin).count(),
                total,
                tallies
        );
    }

    @Transactional
    public void changeTally(String editorEmail, long userId, TallyChange change, Integer exactValue) {
        requireAdmin(editorEmail);
        AppUser user = findLockedUser(userId);
        int current = user.getTallyCount();
        int next = switch (change) {
            case DECREMENT -> Math.max(0, current - 1);
            case INCREMENT -> current >= MAX_TALLY ? current : current + 1;
            case ADD_FIVE -> current >= MAX_TALLY ? current : Math.min(MAX_TALLY, current + 5);
            case RESET -> 0;
            case SET -> validateExactValue(exactValue);
        };
        user.setTallyCount(next);
    }

    @Transactional
    public void setAdmin(String editorEmail, long userId, boolean admin) {
        requireAdmin(editorEmail);
        AppUser user = findLockedUser(userId);
        if (!admin && user.isAdmin() && users.countByAdminTrue() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one administrator is required.");
        }
        user.setAdmin(admin);
    }

    private int validateExactValue(Integer value) {
        if (value == null || value < 0 || value > MAX_TALLY) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The tally must be between 0 and " + MAX_TALLY + "."
            );
        }
        return value;
    }

    private AppUser requireAdmin(String email) {
        AppUser editor = findUserByEmail(email);
        if (!editor.isAdmin()) {
            throw new AccessDeniedException("Only administrators can edit tallies.");
        }
        return editor;
    }

    private AppUser findUserByEmail(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("The signed-in account no longer exists."));
    }

    private AppUser findLockedUser(long userId) {
        var user = users.findLockedById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Editing one's own tally can have loaded this account during authorization,
        // before a concurrent automatic assessment committed its updated total.
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
}
