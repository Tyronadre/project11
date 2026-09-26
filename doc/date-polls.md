# Terminabstimmungen

Über „Abstimmungen“ in Übersicht und Kalender können angemeldete Mitglieder
eine Abstimmung anlegen: Titel, optionaler Ort und Beschreibung, Dauer
(15 bis 1440 Minuten) und zwei bis sechs zukünftige Termine in Berliner Zeit.
Doppelte Termine sowie nicht existierende oder mehrdeutige Uhrzeiten beim
Sommerzeitwechsel werden abgewiesen.

Jedes Mitglied kann mehrere passende Termine auswählen. Eine leere, gespeicherte
Auswahl bedeutet „Keiner passt“; nicht abgegebene Stimmen werden getrennt gezählt.
Erneutes Speichern ersetzt die eigene Auswahl. Die Gruppe sieht die Anzahl
der Zusagen pro Termin, den Beteiligungsstand und die führenden Termine.

Ersteller und Admins können einen Vorschlag verbindlich auswählen. Die Auswahl
ist bewusst manuell, auch bei Gleichstand. Dabei wird die Abstimmung geschlossen
und über den bestehenden Event-Service ein Event mit Titel, Beschreibung, Ort
und Dauer erstellt. Der ursprüngliche Abstimmungsersteller bleibt Eventersteller.
Stimmen bedeuten Verfügbarkeit, nicht bestätigte Anwesenheit.

Alle Schreibaktionen erfordern CSRF und Anmeldung. Stimmen und Abschluss
sperren dieselbe Datenbankzeile. Wiederholte oder parallele Abschlüsse führen
zum bereits erstellten Event; nach Abschluss sind keine neuen Stimmen möglich.
Abgeschlossene Abstimmungen bleiben mit Ergebnissen und Event-Link sichtbar.

Der festgelegte Vorschlag wird im Ergebnis als „Als Event festgelegt“ markiert.
„Keiner passt“ und ausstehende Antworten werden als eigene Zahlen angezeigt.
Vergangene Vorschläge bleiben im Ergebnis sichtbar, können aber nicht mehr als
Event ausgewählt werden. Sind alle Vorschläge vergangen, sind keine weiteren
Stimmen möglich; die Übersicht kennzeichnet die Abstimmung entsprechend.
Zeiträume über einen Sommer-/Winterzeitwechsel werden bei der Erstellung
abgewiesen, damit die angegebene Dauer der tatsächlichen Eventdauer entspricht.
