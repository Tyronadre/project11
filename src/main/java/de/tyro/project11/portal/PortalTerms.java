package de.tyro.project11.portal;

import java.util.List;
import java.util.stream.Collectors;

/** Shared fictional terms; the exact accepted text is retained in each filed application. */
public final class PortalTerms {
    private PortalTerms() {}

    public record Clause(String heading, String text) {}
    public record Document(String title, String version, List<Clause> clauses) {}

    public static final Document CURRENT = new Document("Allgemeine Gruppenbedingungen (AGB)", "AGB-01", List.of(
            new Clause("§ 1 Geltungsbereich der Geltung",
                    "Diese Bedingungen gelten für das Ausfüllen, Weiterlesen und bedeutungsschwere Seufzen während des Antrags. Ein Überfliegen ist nur zulässig, sofern die Flughöhe innerhalb des Browserfensters bleibt."),
            new Clause("§ 2 Amtliche Zeitrechnung",
                    "Die Begriffe gleich, demnächst und bin schon unterwegs bezeichnen keine messbaren Zeitspannen. Ein bereits angezogener Schuh gilt noch nicht als Abfahrt. Der zweite Schuh ist gesondert zu würdigen."),
            new Clause("§ 3 Ordnungsgemäße Abwesenheit",
                    "Während einer Abwesenheit ist die antragstellende Person angehalten, sich irgendwo anders aufzuhalten. Gleichzeitige Anwesenheit am Ort der Abwesenheit ist der eigenen Verwirrung unverzüglich mitzuteilen."),
            new Clause("§ 4 Tassen- und Untersetzerwesen",
                    "Kaffeetassen sind vorzugsweise mit der Öffnung nach oben zu führen. Ein kreisförmiger Kaffeefleck kann als Dienstsiegel angesehen werden, sofern er überzeugend genug aussieht. Tee genießt Bestandsschutz."),
            new Clause("§ 5 Vertretung durch Zimmerpflanzen",
                    "Bei ungeklärter Zuständigkeit übernimmt der nächstgelegene Zimmerfarn die kommissarische Sachbearbeitung. Sein Schweigen ist botanischer Natur. Büroklammern dürfen beraten, aber keine Urlaubsvertretung übernehmen."),
            new Clause("§ 6 Gleichbehandlung der Nudelformen",
                    "Sämtliche Nudelformen sind vor der Soße gleich. Farfalle dürfen ihre Fliege auch außerhalb festlicher Anlässe tragen. Die Nennung von Lieblingsnudeln begründet keinen Anspruch auf deren Bereitstellung beim nächsten Gruppenabend."),
            new Clause("§ 7 Papierloser Papierverkehr",
                    "Virtuelle Ausdrucke dürfen nicht geknickt werden. Deckblätter sind gedanklich oben auf den Vorgang zu legen. Leere Rückseiten gelten als vollständig ausgefüllt, solange niemand versucht, sie vorzulesen."),
            new Clause("§ 8 Wetterbedingte Gefühlslagen",
                    "Sonnenschein beschleunigt die Bearbeitung nicht. Regen stellt ebenfalls keinen Beschleunigungsgrund dar. Bei wechselnder Bewölkung bleibt die Zuständigkeit wechselnd ungeklärt."),
            new Clause("§ 9 Lautstärke des Schweigens",
                    "Stillschweigende Zustimmung ist in Zimmerlautstärke zu erteilen. Lautes Schweigen kann durch leiseres Nicken ersetzt werden. Ein innerliches Augenrollen ist zulässig, sofern dabei keine Gedanken aus dem Kopf fallen."),
            new Clause("§ 10 Rosinen und sonstige Überraschungen",
                    "Die persönliche Haltung zu Rosinen darf von der Aktenlage abweichen. In einem Keks aufgefundene Rosinen sind zunächst als solche zu erkennen. Ein Umbenennen in traurige Weintrauben ändert ihre Zuständigkeit nicht."),
            new Clause("§ 11 Bewegliche Bedienelemente",
                    "Ein ausweichender Weiter-Knopf befindet sich im Außendienst. Nach Verbrauch seines Reisebudgets hat er an einem erreichbaren Ort zu verbleiben. Rückläufige Fortschrittsanzeigen gelten als besonders gründliche Schätzung."),
            new Clause("§ 12 Abschließende Vorläufigkeit",
                    "Mit der Bestätigung dieser Bedingungen bestätigen Sie deren Bestätigung. Der letzte Hinweis ist nicht notwendigerweise der letzte Hinweis. Diese Gruppenbedingungen sind Teil des Verwaltungsscherzes und verändern weder Ihre Striche noch die Einreichungsfristen.")
    ));

    public static ApplicationAnswer acceptedSnapshot() {
        String text = "Akzeptiert.\n\n" + CURRENT.clauses().stream()
                .map(clause -> clause.heading() + "\n" + clause.text()).collect(Collectors.joining("\n\n"));
        return new ApplicationAnswer("E. Bestätigungswesen", CURRENT.title() + " · Fassung " + CURRENT.version(), text);
    }
}
