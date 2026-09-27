package de.tyro.project11.portal;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.List;
@Component("officeAtmosphere")
public class OfficeAtmosphere {
    private final Clock clock;
    public OfficeAtmosphere(Clock clock) { this.clock = clock; }
    public record Mood(String message, String seal) {}
    private static final List<Mood> MOODS = List.of(
        new Mood("Faxgerät emotional nicht erreichbar.", "GEPRÜFT · AUF ANWESENHEIT VON PAPIER"),
        new Mood("Referat B bespricht die Vermeidung weiterer Besprechungen.", "VORLÄUFIG ENDGÜLTIG GESTEMPELT"),
        new Mood("Die Büroklammerninventur befindet sich in der Nachzählung.", "ORDNUNGSGEMÄSS ZUR KENNTNIS GENOMMEN"),
        new Mood("Das Dienstsiegel ist im genehmigten Stempelerholungsurlaub.", "SIEGELVERTRETUNG IM DIENST"),
        new Mood("Die Zuständigkeit für die Zuständigkeitsfrage wird noch geklärt.", "ZUSTÄNDIGKEIT UNTER VORBEHALT")
    );
    public Mood current() { return MOODS.get((int) Math.floorMod(clock.instant().getEpochSecond() / 3600, MOODS.size())); }
}
