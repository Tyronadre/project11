# „Für dich offen“ auf der Startseite

Der persönliche Bereich auf `/welcome` bündelt offene Kosten, fehlende AaAs,
Reiseberichte mit Fristen und ungelesene Antragsentscheidungen. Er ersetzt den
bisherigen einzelnen AaA-Hinweisblock; die Vormerkungen auf den Strichkarten bleiben.
Alle Einträge beziehen sich auf das angemeldete Konto. Datum und Dringlichkeit
verwenden immer `Europe/Berlin` und die bestehenden Fristregeln.

## Inhalte

- **Kosten:** Noch nicht als bezahlt erfasste eigene Anteile an fremden Anfragen
  sowie ausstehende Erstattungen für eigene Anfragen. Der Eigenanteil zählt nicht
  als Zahlung an sich selbst. Teilzahlungen werden berücksichtigt. Eigene Anfragen
  ohne bestätigte Aufteilung erscheinen als „Aufteilung offen“, ohne geschätzte
  Schulden anzuzeigen. Jeder Eintrag verlinkt die konkrete Kostenanfrage.
- **AaAs:** Die vorhandenen persönlichen Anwesenheitshinweise mit ihrer echten
  Frist. Der Link öffnet den AaA mit vorausgewähltem Event. Nach Fristablauf führt
  er zum Event; eine verspätete Einreichung wird nicht angeboten. Die bestehenden
  Regeln für Bestätigung, Ausnahmen, abgesagte Events und bereits verbuchte Striche
  bleiben maßgeblich.
- **Reiseberichte:** Eigene Urlaubsperioden ohne eingereichten Bericht, sofern der
  zugehörige AaB nicht abgelehnt wurde. Auch ein noch ungeprüfter AaB hält die
  Berichtspflicht sichtbar. Bei laufenden und zukünftigen Urlauben wird angezeigt,
  ab wann der Bericht eingereicht werden kann. Danach öffnet der Link einen EeR
  mit vorausgewähltem Urlaub. Überfällige Berichte bleiben sichtbar; eine späte
  Einreichung hebt bereits vergebene Striche nicht auf.
- **Entscheidungen:** Angenommene und abgelehnte AaAs, AaBs und Reiseberichte des
  angemeldeten Mitglieds, mit Begründung und Datum, neueste zuerst. Auch bisherige
  Entscheidungen erscheinen zunächst ungelesen. „Als gelesen markieren“ blendet
  nur den persönlichen Hinweis aus; die Antragsakte bleibt unverändert erreichbar.

AaA- und Berichtsfristen stehen zusammen, früheste zuerst. Vergangene Fristen sind
rot markiert, Fristen innerhalb der nächsten drei Berliner Kalendertage gelb.
Kosten haben keine künstlich ergänzten Zahlungsfristen.
Die Summen trennen „Von dir zu zahlen“ und „An dich zu erstatten“; sie werden nicht
miteinander verrechnet. Erledigte Aufgaben verschwinden beim nächsten Seitenaufruf.

## Umsetzung

`OpenItemsService` erzeugt reine Ansichtsdatensätze aus den bestehenden Kosten-,
Anwesenheits- und Reiseabläufen. Es gibt keine zweite Speicherung offener Aufgaben.
Für Gelesen-Markierungen erzeugt Hibernate die Tabelle `decision_receipts`, eindeutig
pro Benutzer, Antragsart und Antrags-ID. Der POST-Endpunkt prüft die Zugehörigkeit
zum angemeldeten Konto und eine tatsächlich vorhandene Entscheidung; CSRF-Schutz
bleibt aktiv. Wiederholte Klicks sind idempotent.

Die Darstellung liegt in `templates/fragments/open-items.html` und
`static/css/open-items.css`, arbeitet ohne JavaScript und passt sich an schmale
Bildschirme an. Lange Entscheidungstexte werden in der Vorschau gekürzt; die
vollständige Begründung steht in der verlinkten Akte.

Es werden keine zusätzlichen E-Mails versendet, keine Striche verändert und keine
Anträge automatisch eingereicht oder entschieden.
