package de.tyro.project11.profile;

import java.util.List;

public final class ProfileQuestions {
    private ProfileQuestions() {}
    public record Question(String key, String section, String label) {}
    public static final List<Question> ALL = List.of(
        new Question("siblings", "Über dich", "Wie viele Geschwister hast du?"),
        new Question("relationship", "Über dich", "Wie ist dein Beziehungsstatus?"),
        new Question("eyes", "Über dich", "Welche Augenfarbe hast du?"),
        new Question("hand", "Über dich", "Wie lang ist deine Hand in Zentimetern?"),
        new Question("allergies", "Über dich", "Welche Allergien hast du?"),
        new Question("bones", "Über dich", "Wie viele Knochen hast du dir bisher gebrochen?"),
        new Question("rating", "Über dich", "Wie bewertest du dich selbst auf einer Skala von 1 bis 10?"),
        new Question("pet", "Dein Alltag", "Was magst du lieber: Katzen, Hunde oder Schildkröten?"),
        new Question("plant", "Dein Alltag", "Sollte man dir eine Zimmerpflanze anvertrauen?"),
        new Question("phone", "Dein Alltag", "Was bevorzugst du: Apple oder Android?"),
        new Question("water", "Dein Alltag", "Trinkst du lieber Sprudelwasser oder stilles Wasser?"),
        new Question("movie", "Deine Lieblingsdinge", "Was ist dein Lieblingsfilm?"),
        new Question("spotify", "Deine Lieblingsdinge", "Wie viele Songs sind in deinen Spotify-Favoriten gespeichert?"),
        new Question("bar", "Deine Lieblingsdinge", "Welche Bar in Darmstadt findest du am besten?"),
        new Question("lanterns", "Deine Lieblingsdinge", "Wie viele Laternchen trinkst du am liebsten an einem Abend?"),
        new Question("season", "Unterwegs und in Zukunft", "Welche Jahreszeit magst du am liebsten?"),
        new Question("holiday", "Unterwegs und in Zukunft", "Welcher Ort ist für dich das beste Urlaubsziel?"),
        new Question("ski", "Unterwegs und in Zukunft", "Was war deine bisher höchste Geschwindigkeit beim Skifahren in km/h?"),
        new Question("jets", "Unterwegs und in Zukunft", "Wie viele Privatjets möchtest du einmal besitzen?"),
        new Question("capital", "Unterwegs und in Zukunft", "Beträgt dein Gesamtkapital aktuell mehr als 100.000 €?")
    );
}
