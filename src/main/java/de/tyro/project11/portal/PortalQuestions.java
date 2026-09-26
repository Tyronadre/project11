package de.tyro.project11.portal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class PortalQuestions {
    private PortalQuestions() {}

    public record Knowledge(String question, List<String> options, String correctAnswer) {}
    public record Questionnaire(List<String> personal, Knowledge knowledge, String absurd,
                                String loyalty, boolean audit, boolean extraSeventeen) {}

    private static final List<String> PERSONAL = List.of(
            "Augenfarbe (bei neutraler Beleuchtung)", "Bevorzugte Hand", "Lieblingsessen",
            "Lieblingsfarbe nach aktueller Aktenlage", "Schuhgröße", "Anzahl der Geschwister",
            "Sternzeichen", "Dominante Schlafposition", "Bevorzugte Nudelform",
            "Aktueller Akkustand des Smartphones", "Anzahl der offenen Browser-Tabs",
            "Anzahl ungelesener Nachrichten", "Geschätzte tägliche Schrittzahl",
            "Bevorzugte Kartoffelzubereitung", "Anzahl der Zimmerpflanzen",
            "Zuletzt gehörtes Lied", "Lieblingsmonat", "Anzahl heute konsumierter Heißgetränke",
            "Bevorzugte Seite des Sofas", "Persönliche Einstellung zu Rosinen");
    private static final List<Knowledge> KNOWLEDGE = List.of(
            new Knowledge("Wie viele Knochen besitzt ein erwachsener Mensch normalerweise?", List.of("204", "206", "208", "212"), "206"),
            new Knowledge("Wie viele Minuten hat eine Woche mit sieben Tagen?", List.of("9.840", "10.080", "10.240", "10.800"), "10.080"),
            new Knowledge("Wie viele Zähne umfasst ein vollständiges Erwachsenengebiss einschließlich Weisheitszähnen?", List.of("28", "30", "32", "34"), "32"),
            new Knowledge("Wie viele Kanten hat ein Würfel?", List.of("6", "8", "12", "16"), "12"),
            new Knowledge("Wie groß ist der Erdumfang am Äquator ungefähr?", List.of("30.075 km", "40.075 km", "50.075 km", "60.075 km"), "40.075 km"));
    private static final List<String> ABSURD = List.of(
            "Was würden Sie tun, wenn Sie für einen Tag ein Wurm wären? Berücksichtigen Sie bestehende Gruppenverpflichtungen.",
            "Was würden Sie tun, wenn Sie ab morgen unsichtbar wären? Eine bloße Abwesenheitsbehauptung genügt nicht.",
            "Was würden Sie tun, wenn Sie für 24 Stunden nicht lügen könnten?",
            "Was würden Sie tun, wenn Sie morgen feststellen würden, dass Sie die letzten fünf Jahre nur geträumt haben?",
            "Was würden Sie tun, wenn Sie eine Taube wären? Bitte Flugroute und Zuständigkeit klären.",
            "Eine fremde Person übergibt Ihnen 100.000 Euro mit der Auflage: Geben Sie es sinnvoll aus. Wie verfahren Sie?",
            "Was würden Sie tun, wenn Sie für einen Tag 50 cm groß wären?",
            "Mit welchem Gegenstand in Ihrem Zimmer würden Sie sprechen, wenn dies möglich wäre, und worüber?",
            "Alle Menschen außer Ihnen sprechen 24 Stunden lang rückwärts. Wie organisieren Sie den nächsten Gruppenabend?",
            "Sie dürfen nur noch eine Mahlzeit essen. Welche wählen Sie und warum ist es nicht die Gruppenpizza?",
            "Sie dürfen einen Gegenstand auf eine einsame Insel mitnehmen und bleiben dort zehn Jahre. Welchen wählen Sie und warum?");
    private static final List<String> LOYALTY = List.of(
            "Wie häufig werden Sie während der Abwesenheit pflichtbewusst an die Gruppe denken?",
            "Wie verbindlich ist Ihre innere Verbundenheit mit dem Freundeskreis während der beantragten Abwesenheit?",
            "Mit welcher Intensität bedauern Sie die Nichtteilnahme bereits im Voraus?");
    public static final List<String> LOYALTY_OPTIONS = List.of(
            "Außerordentlich und ohne Aufforderung", "Im üblichen verwaltungsrechtlichen Umfang",
            "Gelegentlich, sofern die Umstände es zulassen", "Sie kennen meine Position");

    public static Questionnaire draw() {
        var random = ThreadLocalRandom.current();
        var personal = new ArrayList<>(PERSONAL);
        Collections.shuffle(personal);
        return new Questionnaire(List.copyOf(personal.subList(0, 3)), KNOWLEDGE.get(random.nextInt(KNOWLEDGE.size())),
                ABSURD.get(random.nextInt(ABSURD.size())), LOYALTY.get(random.nextInt(LOYALTY.size())),
                random.nextInt(100) < 2, random.nextInt(100) < 15);
    }
}
