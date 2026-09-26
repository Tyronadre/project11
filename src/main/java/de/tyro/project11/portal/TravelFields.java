package de.tyro.project11.portal;

import java.util.List;

public final class TravelFields {
    private TravelFields() {}
    public record Field(String key, String label, String type, int min, int max, List<String> options) {}
    private static Field text(String key, String label, int max) { return new Field(key, label, "text", 1, max, List.of()); }
    private static Field area(String key, String label, int min, int max) { return new Field(key, label, "textarea", min, max, List.of()); }
    private static Field date(String key, String label) { return new Field(key, label, "date", 10, 10, List.of()); }
    private static Field select(String key, String label, String... options) { return new Field(key, label, "select", 1, 500, List.of(options)); }

    private static Field check(String key, String label, String value) { return new Field(key, label, "check", 1, 500, List.of(value)); }

    public static final List<Field> LEAVE = List.of(
            text("applicantName", "Vollständiger Name", 120),
            date("startsOn", "Beginn der Beurlaubung (erster Urlaubstag)"),
            date("endsOn", "Ende der Beurlaubung (letzter Urlaubstag)"),
            text("destination", "Reiseziel", 120),
            text("stopovers", "Zwischenziele (gegebenenfalls: keine)", 1000),
            text("companions", "Mitreisende (gegebenenfalls: keine)", 500),
            text("accommodation", "Unterkunft", 500),
            area("purpose", "Zweck der Reise", 20, 2000),
            new Field("meals", "Voraussichtliche Anzahl täglich konsumierter Mahlzeiten", "number", 0, 20, List.of()),
            select("internet", "Erwartete Internetverfügbarkeit", "Ständig", "Meistens", "Gelegentlich", "Keine", "Dem Schicksal überlassen"),
            text("contact", "Kontaktmöglichkeit während der Beurlaubung", 500),
            select("groupThoughts", "Wie wahrscheinlich ist es, dass Sie während Ihrer Abwesenheit an die Gruppe denken?", "Sehr wahrscheinlich", "Wahrscheinlich", "Gelegentlich", "Unwahrscheinlich", "Sie kennen meine Position"),
            area("relaxation", "Persönliche Begründung der Erholungsbedürftigkeit: Warum steht Ihnen eine vorübergehende räumliche und soziale Distanzierung vom Freundeskreis zu?", 20, 500),
            select("relationship", "Welche Aussage beschreibt Ihr aktuelles Verhältnis zur Gruppe am treffendsten?", "Ich brauche Abstand", "Ich brauche Urlaub", "Ich brauche beides", "Ich brauche eigentlich nur Schlaf", "Ich möchte diese Frage nicht beantworten", "Ich bereue meinen Antrag bereits"));

    public static final List<Field> REPORT = List.of(
            text("applicantName", "Vollständiger Name", 120),
            area("itinerary", "Tatsächlicher Reiseverlauf", 50, 6000),
            area("deviations", "Abweichungen vom ursprünglichen Reiseplan (gegebenenfalls: keine)", 1, 2000),
            area("insight", "Wichtigste Erkenntnis der Reise", 20, 2000),
            area("culture", "Kulturelle Bereicherung", 20, 2000),
            area("food", "Kulinarische Erfahrungen", 20, 2000),
            area("regret", "Gab es einen Moment, in dem Sie Ihre Entscheidung zur Abwesenheit bereut haben?", 1, 2000),
            new Field("rating", "Bewertung der Reise (1–10)", "number", 1, 10, List.of()),
            select("repeatTrip", "Würden Sie dieselbe Reise erneut antreten, wenn dadurch erneut eine Gruppenaktivität verpasst würde?", "Ja", "Nein", "Nur nach erneuter eingehender Gewissensprüfung"),
            check("truth", "Ich versichere, dass die vorstehenden Angaben nach bestem Wissen und Gewissen der Wahrheit entsprechen.", "Hiermit versichert"),
            check("consequences", "Ich habe verstanden, dass Falschangaben zu einer Neubewertung meiner sozialen Zuverlässigkeit führen können.", "Hiermit zur Kenntnis genommen"));

    public static List<Field> forKind(TravelKind kind) { return kind == TravelKind.LEAVE ? LEAVE : REPORT; }
}
