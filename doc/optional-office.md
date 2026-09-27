# Strichverlauf und optionale Amtsverfahren

## Änderungen an Strichen

Auf `/users/{id}` steht unter dem Journal der Änderungsverlauf der Striche.
Er zeigt Zeitpunkt (Berlin), Bearbeiter, Ursache und den Gesamtstand vor und nach
der Änderung, mit 20 Einträgen pro Seite. Automatische Änderungen verlinken auf
das Event oder den zugehörigen Beurlaubungsantrag. Manuelle Änderungen durch
Admins, automatische Event-Striche, Rücknahmen und Urlaubsstriche werden zusammen
mit dem Zähler in derselben Transaktion gespeichert. Wiederholte Prüfungen und
unveränderte Zähler erzeugen keine zusätzlichen Einträge.

Die Eventkarten im Profil kennzeichnen automatisch vergebene oder zurückgenommene
Striche. Das funktioniert auch für bereits vorhandene `attendance_penalties`.
Abgesagte Events mit solchen Einträgen bleiben im Journal sichtbar. Eine solche
Zuordnung beschreibt den automatischen Vorgang; spätere manuelle Korrekturen
stehen separat im Verlauf. Frühere manuelle Änderungen können nicht rekonstruiert
werden. Das neue Journal beginnt mit den Änderungen nach diesem Update.

## AFeA – Antrag auf Feststellung der eigenen Anwesenheit

Auf der Detailseite eines beendeten, nicht abgesagten Events können Mitglieder
aus dessen Teilnahmezeit freiwillig einen AFeA abgeben. Zur Auswahl stehen drei
augenzwinkernde Beweismittel. Es gibt höchstens einen Hinweis je Event und Person;
dieser lässt sich jederzeit zurückziehen.

Bei der ersten, noch ungespeicherten Anwesenheitserfassung werden die betreffenden
Personen vorausgewählt. Der Ersteller oder Admin kann alle Häkchen frei ändern und
Personen ohne AFeA genauso als anwesend eintragen. Nach dem ersten Speichern sind
AFeAs lediglich zusätzliche Hinweise; sie überschreiben keine Anwesenheitsliste.
Abgabe und Rücknahme bestätigen weder Anwesenheit noch verändern sie Striche,
Kosten oder E-Mail-Aufträge. Die bestehende separate Bestätigung bleibt bestehen.

## BüB – Beschwerde über die Bearbeitungsdauer einer Beschwerde

Das Amt verlinkt unter `/amt/bub` ein eigenständiges Beschwerdewesen. Eine erste
Beschwerde benötigt keinen Vorgänger. Betreff (120 Zeichen) und Text (2000 Zeichen)
werden mit einem Aktenzeichen gespeichert. Eigene Beschwerden erscheinen in einer
Liste; ihre Detailseiten sind für angemeldete Gruppenmitglieder lesbar.

Nur die einreichende Person darf eine Beschwerde „als ausreichend beklagt“
abschließen und danach mit einer bis fünf Büroklammern bewerten. Über eine eigene
Beschwerde lässt sich eine weitere Beschwerde einreichen. Die Akte zeigt Eingang
und Abschluss. Alle Inhalte werden als Text ausgegeben.

Der Vorgang ist reine Unterhaltung: keine Striche, Anwesenheitsänderungen, Kosten,
Fristen, Entscheidungen in anderen Verfahren oder E-Mails. `ComplaintService`
greift ausschließlich auf seine eigenen Akten, die Benutzerzuordnung und die Uhr zu.

## Dienstmeldungen und Siegel

Im Amtsfuß wechseln fünf Dienstmeldungen mit passenden Stempeln stündlich, jeweils
beim nächsten Seitenaufruf. Beispielsweise ist das Faxgerät emotional nicht
erreichbar oder die Büroklammerninventur in der Nachzählung. Diese Anzeige schreibt
keine Daten und beeinflusst keine echte Bearbeitung oder Frist.

## Speicherung und Prüfung

Hibernate ergänzt über das bestehende Schema-Update die Tabellen `tally_history`,
`afea_declarations` und `ceremonial_complaints`. Geschlossene Abstimmungen erhalten
zusätzliche nullable Felder; bestehende Abstimmungen bleiben verwendbar.
Schreibaktionen benötigen Anmeldung und CSRF-Schutz; Eigentumsrechte werden im
Service geprüft. AFeA-Schreibaktionen sperren die Eventzeile, Beschwerden ihre Akte.

`OptionalOfficeIntegrationTests` prüft insbesondere die Unabhängigkeit der
Spaßverfahren, die freiwillige Vorauswahl, Berechtigungen und Journalführung.
`PollIntegrationTests` prüft auch konkurrierende Abschlüsse mit und ohne Event.
