# Freiwilliges Zusatzverwaltungswesen

Der Einstieg liegt unter **Amt → Freiwillige Spaßverfahren** (`/amt/extra`).
Alle acht Angebote sind freiwillig und verändern weder Striche noch Anwesenheit,
Kosten, Fristen, Kontonamen oder Entscheidungen in anderen Anträgen. Sie versenden
keine E-Mails. Die Antragsberechtigung ist niemals Zugangsvoraussetzung für ein
anderes Formular.

## Die acht Angebote

1. **Antrag auf Erteilung einer Antragsberechtigung:** Die Einreichung wird sofort
   rückwirkend genehmigt – ausschließlich für diesen selbstbezüglichen Antrag.
2. **Digitales Wartezimmer:** Pro Mitglied gibt es höchstens eine offene Wartemarke.
   Drei erfundene Personen rücken im Abstand von 15 Sekunden weiter. Nach 45 Sekunden
   verweist Schalter 3 an Schalter 7; dort wird die Nichtzuständigkeit festgestellt.
   „Wartezimmer sofort verlassen“ ist jederzeit möglich. Die Anzeige lässt sich
   manuell aktualisieren; automatische Seitenaktualisierung ist optional und stoppbar.
3. **Spitznamenanerkennung:** Eine Bezeichnung und ihre übertriebene Begründung
   ergeben eine druckbare Ernennungsurkunde. Der tatsächliche Kontoname bleibt bestehen.
4. **Immaterielles Fundbüro:** Gemeinsames schwarzes Brett für Verluste und Funde.
   Über „Dazu habe ich etwas gefunden“ können Mitglieder eine Fundanzeige mit einer
   offenen Verlustanzeige verknüpfen. Nur die einreichende Person kann die eigene
   Anzeige als erledigt abheften. Verknüpfte Anzeigen bleiben davon unberührt.
5. **Rückwirkende Vorfreude:** Ein beendetes, nicht abgesagtes Treffen aus der eigenen
   Mitgliedszeit und ein Ausmaß der Vorfreude werden als Urkunde festgehalten.
   Daraus wird keine tatsächliche Anwesenheit abgeleitet.
6. **Zuständigkeitsprüfung:** Das Anliegen wandert per Klick über Referat C zum
   Unterausschuss Freizeit und zurück. Nach drei Weiterleitungen liegt die
   Bescheinigung der abschließenden Nichtzuständigkeit vor.
7. **Bescheinigung über das Nichtvorliegen eines Bescheinigungsbedarfs:** Bestätigt
   unmittelbar und druckbar, dass keine Bescheinigung benötigt wird.
8. **Persönliche Amtsstatistik:** Zählt ausschließlich eigene Zusatzakten und eigene
   Fundbüroanzeigen in diesem Bereich. Drei virtuelle Büroklammern je Zusatzakte,
   zwei je Anzeige. Ausgewiesen werden auch Abschlüsse und Wartemarken. Weder Rangliste
   noch Zuverlässigkeitsbewertung; keine Auswirkungen auf andere Funktionen.

## Akten und Urkunden

Zusatzakten besitzen dauerhafte Aktenzeichen und erscheinen in einer persönlichen
Liste mit 20 Einträgen je Seite. Fundbüro und zugehörige Antworten sind ebenfalls
paginiert. Text, Name, Eventtitel und Vorfreude werden bei der Einreichung gespeichert;
spätere Änderungen am Profil oder Treffen schreiben eine ausgestellte Urkunde nicht um.
Urkunden lassen sich über den Browser drucken oder als PDF speichern. Die Druckansicht
blendet Navigation und Aktionsknöpfe aus und verwendet A4-Druckränder.

Akten und Urkunden sind für angemeldete Gruppenmitglieder lesbar; ausschließlich
Eigentümer dürfen offene Vorgänge weiterschalten. Wartemarken und Statistik sind
persönlich. Alle Schreibaktionen benötigen CSRF-Schutz. Text wird escaped ausgegeben.
Einreichungen der fünf Formularverfahren werden durch einen Token gegen doppelte
Klicks geschützt. Die Zuständigkeitsprüfung verarbeitet jede Station höchstens einmal.

## Technik und Prüfung

Die Implementierung liegt in `portal/leisure`. Hibernate ergänzt zwei unabhängige
Tabellen: `leisure_cases` und `immaterial_lost_property`. Bestehende Antrags-,
Teilnahme- und Strichtabellen werden nicht verändert. `LeisureService` verwendet
Benutzer zur Identifikation und liest Events für die Vorfreudeauswahl; seine einzigen
Schreibzugriffe betreffen die beiden eigenen Tabellen.

`LeisureIntegrationTests` prüft alle Verfahren, Berechtigungen, HTML-Escaping,
druckbare Urkunden, Zeitschritte, parallele Einreichungen und unveränderte Zähler
der bestehenden Fachverfahren. Die Tests erzeugen HTML-Beispiele unter
`build/leisure-preview` zur visuellen Prüfung mit den lokalen Stylesheets.
