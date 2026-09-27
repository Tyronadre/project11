# Eventkosten

Die moderne Oberfläche bietet unter **Kosten** (`/costs`) eine Übersicht, gruppiert nach Event. Pro Event werden Gesamtkosten, eigener Anteil, offene Zahlungen an andere und offene Erstattungen an den eingeloggten Nutzer angezeigt. Es gibt keine Verrechnung zwischen verschiedenen Events oder Erstellern. Die persönlichen Summen berücksichtigen nur bereits aufgeteilte Anfragen; ein Hinweis kennzeichnet noch fehlende Aufteilungen.

## Eintragen und aufteilen

- Jeder angemeldete Nutzer kann Ausgaben für ein Event eintragen, auch wenn er selbst nicht teilgenommen hat. Der eingeloggte Nutzer wird als Ersteller/Zahlungsempfänger gespeichert.
- **Kosten eintragen** in der Übersicht und auf der Eventseite öffnet dieselbe Unterseite: `/events/{id}/costs/new`. Die gemeinsame Eventabrechnung liegt unter `/events/{id}/costs`.
- Bei der Eventerstellung kann der Ersteller optional einen Gesamtbetrag angeben. Event und zugehörige Anfrage „Eventkosten“ werden gemeinsam in einer Transaktion gespeichert. Ein leerer Betrag erzeugt keine Anfrage.
- Beträge sind Eurobeträge von 0,01 bis 9.999.999,99 Euro, ohne Tausendertrennzeichen und mit höchstens zwei Nachkommastellen. Komma und Punkt sind erlaubt. Die Speicherung erfolgt als ganze Centbeträge, ohne Gleitkomma-Rundung.
- Standardmäßig erfolgt die Aufteilung auf alle anwesenden Personen in der **bestätigten** Teilnehmerliste. Ohne bestätigte Liste oder bei null anwesenden Personen wartet diese automatische Aufteilung. Alternativ kann der Ersteller „Nur ausgewählte Personen“ wählen und mindestens einen Nutzer markieren. Diese Auswahl steht auch bei der Eventerstellung zur Verfügung und kann bei offenen Anfragen bearbeitet werden. Alle ausgewählten Nutzer tragen ihren Anteil unabhängig von ihrer Teilnahme; andere Personen erhalten keinen Anteil. Diese Aufteilung und die Zahlungserfassung funktionieren sofort, auch ohne bestätigte Teilnehmerliste. Änderungen der Anwesenheit verändern die manuelle Auswahl nicht und blockieren deren Zahlungserfassung nicht.
- Jeder erhält denselben Anteil bis auf unvermeidbare Restcents. Diese gehen in stabiler Reihenfolge an die kleinsten Nutzer-IDs; die Summe aller Anteile entspricht immer exakt dem Gesamtbetrag.
- Nimmt der Ersteller teil, zählt sein eigener Anteil zu den Eventkosten, ist aber keine Erstattungsforderung an sich selbst. Ist er abwesend, erstatten die Teilnehmenden den gesamten Betrag.

## Bearbeiten und bezahlen

Nur der Ersteller einer Kostenanfrage darf sie bearbeiten oder ihren Status ändern; auch andere Admins erhalten keine Ausnahme.

Automatische Anfragen ohne erfasste Zahlungen werden bei Änderungen an der bestätigten Teilnehmerliste neu aufgeteilt. Während eine geänderte Liste auf Bestätigung wartet, pausiert deren Aufteilung. Für diese automatische Variante kann die erste Zahlung nur bei bestätigter, nicht leerer Teilnehmerliste erfasst werden. Eine manuelle Personenauswahl bleibt dagegen unabhängig von Anwesenheit und Bestätigungsstand vollständig berücksichtigt und zahlbar. Neue Teilnehmende werden nicht automatisch in die Auswahl aufgenommen. Weitere Zahlungen verwenden jeweils die bereits gespeicherte Aufteilung.

Der Ersteller hakt jede erforderliche Zahlung direkt bei der jeweiligen Person ab. Dadurch ist für alle Mitglieder sichtbar, welche Anteile offen oder bezahlt sind und wann eine Zahlung markiert wurde. Der eigene Anteil des Erstellers ist keine Zahlung an sich selbst; auch 0-Cent-Anteile benötigen keinen Zahlungsstatus. Sobald alle erstattungspflichtigen Anteile bezahlt sind, erhält die Anfrage zusätzlich den Gesamtstatus **Bezahlt**. Bis dahin zeigt sie nach der ersten Zahlung **Teilweise bezahlt**.

Beim ersten abgehakten Anteil werden Teilnehmer und Centbeträge als unveränderliche Aufteilung gespeichert. Spätere Anwesenheitskorrekturen ändern bereits verfolgte Zahlungen daher nicht. Die offenen persönlichen Summen und die offenen Erstattungen eines Events enthalten nur noch tatsächlich unbezahlte, erstattungspflichtige Anteile.

Der Ersteller kann eine einzelne Zahlung wieder als offen markieren. Werden dadurch keine Zahlungen mehr verfolgt, gilt wieder die gespeicherte Personenauswahl beziehungsweise die aktuelle bestätigte Teilnehmerliste. Zusätzlich kann er alle Zahlungsstände einer Anfrage gemeinsam zurücksetzen. Zum Bearbeiten von Betrag, Verwendungszweck oder Personenauswahl müssen zuerst alle Zahlungsstände zurückgesetzt werden.

Diese Seiten lösen keine Zahlungen oder E-Mails aus. Profile sind aus der Aufteilung verlinkt; Zahlungsdaten bleiben auf der Profilseite und werden dort wie bisher erst auf Wunsch angezeigt.

## Technische Absicherung

`CostService` prüft Ownership, Geldformat und Versionen serverseitig. Ein Event-Lock serialisiert Änderungen mit der Anwesenheitsverwaltung. Die Version der Kostenanfrage schützt Betrag und Personenauswahl vor veralteten Änderungen; bei automatischer Aufteilung wird vor der ersten Zahlung zusätzlich die Version der Teilnehmerliste geprüft. Ein eindeutiger Formularschlüssel pro Ersteller verhindert doppelte Kostenanfragen, auch bei parallelem Absenden. Alle Änderungen sind CSRF-geschützte POSTs.

Tabellen: `event_costs`, `event_cost_shares` und `event_cost_selected_users`. `event_cost_shares.paid_at` speichert den Status pro Person; `event_costs.paid_at` bleibt der Abschlusszeitpunkt der gesamten Anfrage und hält bereits abgeschlossene Bestandsdaten kompatibel. Das nullable Feld `selected_only` bewahrt bei bestehenden Anfragen die Aufteilung auf alle Teilnehmenden. Hibernate ergänzt neue Spalten beim Start über die bestehende Schemaaktualisierung; vorhandene Events ohne Kosten funktionieren weiter.

`CostIntegrationTests` prüft unter anderem die Cent-Aufteilung, einzelne und teilweise Zahlungen, offene persönliche Summen, das Zurücksetzen von Zahlungsständen, auswählbare Teilgruppen, Teilnehmerkorrekturen, Ownership, CSRF, ungültige Beträge, konkurrierendes Absenden, alte Formularversionen, Eventerstellung mit Kosten sowie die gemeinsamen Links und serverseitige Darstellung. Visuelle Frontend-Checks erfolgen separat.
