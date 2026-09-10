package de.tyro.project11.dashboard;

import java.util.List;

public record DashboardView(
        long currentUserId,
        String currentUserName,
        boolean admin,
        long adminCount,
        int totalTallies,
        List<TallyView> users
) {
}
