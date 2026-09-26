# Events bearbeiten und absagen

Eventersteller und Admins finden auf `/events/{id}` die Aktionen **Event bearbeiten** und **Event absagen**. Die Berechtigung wird bei jedem Speichern serverseitig anhand des gespeicherten Adminstatus und Eventerstellers geprüft.

## Bearbeiten

Das gemeinsame Eventformular enthält Name, Beschreibung, Ort, Startdatum, optionale Start-/Endzeit und ein optionales Enddatum. Bestehende mehrtägige Events behalten ihr Enddatum. Ohne Endzeit endet das Event am Ende des gewählten Enddatums; ohne explizites Enddatum bedeutet eine frühere Endzeit den nächsten Tag. Alle Zeiten werden in `Europe/Berlin` interpretiert; nicht existierende Uhrzeiten beim Wechsel auf Sommerzeit werden abgelehnt.

Bei tatsächlichen Änderungen speichert die Anwendung für jedes aktuelle Gruppenmitglied (einschließlich Ersteller) eine E-Mail mit bisherigem und neuem Stand und einem Link zum Event. Änderungen und Nachrichten werden in derselben Transaktion gespeichert. Unverändertes Speichern erzeugt keine Nachricht. Ein veraltetes Formular wird mit HTTP 409 abgelehnt, damit konkurrierende Änderungen nicht überschrieben werden.

**Bereits dokumentierte Termine sind geschützt:** Sobald Anwesenheiten oder AaAs zum Event vorliegen, können Datum und Uhrzeit nicht mehr verändert werden. So bleiben Fristen, Urlaubsausnahmen, Striche und Kostenaufteilungen auf denselben Termin bezogen. Ort, Name und Beschreibung bleiben bearbeitbar. Für eine Neuplanung lässt sich das bisherige Event absagen und ein neues erstellen. Das Formular erklärt diese Einschränkung.

Kosten werden beim Bearbeiten des Events nicht neu angelegt oder geändert. Dafür bleibt die separate Kostenabrechnung zuständig.

## Absagen

Die aufklappbare Absage auf der Eventseite erklärt die Folgen und enthält eine optionale Begründung. Erst der ausdrückliche POST-Button führt die Absage aus. Es wird kein Event gelöscht; der Kalender und die Detailseite zeigen **Abgesagt**. Die Startseitenliste kommender Events blendet Absagen aus.

- Jedes aktuelle Gruppenmitglied erhält eine gespeicherte Absagebenachrichtigung.
- Neue Anwesenheitslisten und AaAs sind ausgeschlossen. Bestehende AaA-Akten bleiben erhalten, benötigen aber keine Entscheidung mehr.
- Ausstehende AaA-Erinnerungen werden storniert; bereits versendete E-Mails lassen sich nicht zurückholen. Noch wartende AaA-Entscheidungsmails werden beim Versand verworfen.
- Es entstehen keine neuen Event-Striche. Bereits angewendete automatische Striche für dieses Event werden genau einmal zurückgenommen. Andere und manuell vergebene Striche bleiben erhalten.
- Kosten und gespeicherte Zahlungseinträge bleiben bestehen, damit bereits angefallene Ausgaben oder Erstattungen geklärt werden können.
- Absagen sind abschließend; ein abgesagtes Event kann nicht bearbeitet oder wieder aktiviert werden. Wiederholtes Absenden erzeugt keine weiteren Nachrichten.

## E-Mail-Betrieb

Die Eventbenachrichtigungen verwenden die vorhandene [SMTP-Konfiguration](attendance-email.md#enable-delivery), insbesondere `ATTENDANCE_MAIL_ENABLED=true`, `SMTP_HOST`, `MAIL_FROM` und `PUBLIC_URL`.

Bei deaktiviertem Versand werden Änderungen und Absagen trotzdem gespeichert und Nachrichten vorgemerkt. Die Oberfläche zeigt ausdrücklich an, dass noch keine E-Mails versendet werden. Aktivieren des Mailversands verarbeitet auch diese wartenden Nachrichten.

`event_change_emails` speichert einen unveränderlichen Nachrichtentext pro Eventrevision und Empfänger. Der Worker verarbeitet alle 30 Sekunden bis zu 20 Eventnachrichten. Erfolgreiche Nachrichten bleiben als `SENT` gespeichert; temporäre Fehler werden mit wachsendem Abstand bis insgesamt sechs Versuchen wiederholt. Endgültige `FAILED`-Nachrichten können nach Behebung des SMTP-Problems operativ auf `PENDING` mit `attempts=0` zurückgesetzt werden. Revision und Änderungszeit stehen in der Mail, damit verzögert eintreffende Nachrichten zeitlich einzuordnen sind.

Wie bei den bisherigen SMTP-Nachrichten ist bei einem Prozessabbruch nach SMTP-Annahme, aber vor dem Datenbank-Commit, ein doppelter Zustellversuch möglich. Normale Wiederholungen und parallele Worker werden durch Datenbank-Locks abgefangen. Die Empfänger erhalten separate E-Mails; es gibt keine öffentliche Empfängerliste.

Neue nullable Spalten in `activities` (`revision`, `cancelled_at`, `cancellation_reason`) und die neue Mailtabelle werden durch Hibernate ergänzt. Bestehende Events gelten als aktiv mit Revision 0.

Automatisierte Prüfungen stehen in `EventManagementIntegrationTests`: Berechtigungen/CSRF, veraltete Formulare, Änderungsnachrichten, Absagen, Strichkorrekturen, ausgeschlossene AaAs, geschützte Termine, mehrtägige und über Mitternacht laufende Events, Berliner Zeitumstellung sowie Mailwiederholungen und konkurrierende Aufrufe. SMTP wird in Tests simuliert.

## Rückmeldungen vor dem Event

Auf der Eventseite kann jedes angemeldete Mitglied **Dabei**, **Vielleicht** oder **Nicht dabei** auswählen und seine eigene Antwort bis zum Eventbeginn ändern. Namen und Anzahlen sind für die Gruppe sichtbar; Mitglieder ohne aktuelle Antwort erscheinen unter **Noch offen**. Der Eventersteller wird nicht automatisch als dabei eingetragen.

Die Grenze ist der gespeicherte Eventbeginn in Berliner Zeit. Bei Events ohne angegebene Startzeit ist das 00:00 Uhr am Eventtag. Nach Beginn oder Absage bleiben die Rückmeldungen sichtbar, sind aber nicht mehr bearbeitbar. Ändert sich der gespeicherte Zeitraum, gelten frühere Rückmeldungen als überholt und werden bis zur erneuten Bestätigung unter „Noch offen“ angezeigt. Reine Orts- oder Textänderungen behalten bestehende Antworten bei.

Die Tabelle `event_rsvps` speichert maximal eine Antwort je Event und Nutzer, einschließlich Zeitraum und Versionsnummer. Event-Locks und Formularversionen verhindern doppelte Datensätze und unbeabsichtigtes Überschreiben. Der Nutzer wird immer aus der Anmeldung bestimmt; auch Admins können keine Antwort für jemand anderen abgeben.

Rückmeldungen erzeugen keine E-Mails, Anwesenheitslisten, Striche oder Kostenanteile. „Nicht dabei“ ersetzt keinen erforderlichen AaA. Die tatsächliche Anwesenheit wird weiterhin nach dem Treffen separat erfasst und bestätigt; sie darf von den vorherigen Zusagen abweichen. `RsvpIntegrationTests` prüft diese Trennung, Berechtigungen, Fristen, Terminänderungen und konkurrierende Rückmeldungen.
