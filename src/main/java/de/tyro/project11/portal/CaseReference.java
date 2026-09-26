package de.tyro.project11.portal;

import de.tyro.project11.attendance.AttendanceRules;
import de.tyro.project11.calendar.CalendarTime;
import java.time.OffsetDateTime;
import java.util.Locale;

/** References use immutable filing time and database identity; existing references stay unchanged. */
public final class CaseReference {
    private CaseReference() {}
    public static String of(String kind, OffsetDateTime submitted, long id) {
        return String.format(Locale.ROOT, "%s-%d-%06d", kind,
                submitted.atZoneSameInstant(CalendarTime.BERLIN).getYear(), id);
    }
    public static String of(AbsenceApplication a) { return of("AaA", a.getSubmittedAt(), a.getId()); }
    public static String of(TravelApplication a) { return of(a.getKind().code, a.getSubmittedAt(), a.getId()); }
    public static String decision(String decision) {
        return switch (decision) {
            case "ACCEPTED" -> "Angenommen";
            case "REJECTED" -> "Abgelehnt";
            default -> "Eingegangen · Entscheidung ausstehend";
        };
    }
    public static String status(AbsenceApplication a) {
        if (a.getActivity().isCancelled()) return "Event abgesagt · AaA nicht mehr erforderlich";
        if (!AttendanceRules.timely(a)) return "Nichtig · verspätet eingereicht";
        return decision(a.getDecision().name());
    }
    public static String status(TravelApplication a) {
        String status = decision(a.getDecision().name());
        if (a.getKind() == TravelKind.LEAVE && a.getHoliday().isInvalidated()) return status + " · Beurlaubung verfallen";
        if (a.getKind() == TravelKind.REPORT && !TravelRules.timely(a)) return status + " · verspäteter Eingang";
        return status;
    }
}
