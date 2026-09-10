package de.tyro.project11.dashboard;

import de.tyro.project11.registration.AppUser;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public record TallyView(
        long id,
        String displayName,
        String email,
        boolean admin,
        int tallyCount,
        List<Integer> fullGroups,
        int remainder,
        int hiddenCount,
        String initials
) {
    private static final int MAX_VISIBLE_GROUPS = 12;

    static TallyView from(AppUser user) {
        int visibleFullGroups = Math.min(user.getTallyCount() / 5, MAX_VISIBLE_GROUPS);
        int visibleRemainder = visibleFullGroups < MAX_VISIBLE_GROUPS ? user.getTallyCount() % 5 : 0;
        int visibleCount = visibleFullGroups * 5 + visibleRemainder;
        return new TallyView(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.isAdmin(),
                user.getTallyCount(),
                IntStream.range(0, visibleFullGroups).boxed().toList(),
                visibleRemainder,
                user.getTallyCount() - visibleCount,
                initials(user.getDisplayName())
        );
    }

    private static String initials(String displayName) {
        String[] names = displayName.strip().split("\\s+");
        String first = firstCharacter(names[0]);
        String last = names.length > 1 ? firstCharacter(names[names.length - 1]) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String firstCharacter(String value) {
        return value.substring(0, value.offsetByCodePoints(0, 1));
    }
}
