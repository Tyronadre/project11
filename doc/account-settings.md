# Kontoeinstellungen und Passwort vergessen

Die Kontoeinstellungen unter `/account` sind im eigenen Profil verlinkt.
E-Mail- und Passwortänderungen verlangen das aktuelle Passwort. Nach einer
Änderung ist eine erneute Anmeldung nötig; andere Sitzungen werden beim nächsten
Aufruf widerrufen. Die neue E-Mail-Adresse wird direkt übernommen.

Auf der Anmeldeseite führt „Passwort vergessen?“ zur Reset-Anfrage. Die Antwort
ist für bekannte und unbekannte Adressen gleich. Reset-Links sind 30 Minuten
gültig, funktionieren einmal und werden nur als SHA-256-Hash gespeichert.
Pro Konto wird höchstens alle zwei Minuten ein neuer Link erzeugt. Ein neuer
Link oder eine Änderung der Zugangsdaten macht bisherige Links ungültig.

## Mailversand aktivieren

Folgende Umgebungsvariablen beim Start setzen:

- `PASSWORD_RESET_MAIL_ENABLED=true`
- `MAIL_FROM`: gültige Absenderadresse
- `PUBLIC_URL`: öffentliche Basisadresse, im Betrieb mit HTTPS
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`: SMTP-Zugang

Die bestehenden Optionen `SMTP_AUTH`, `SMTP_STARTTLS` und `SMTP_SSL` gelten auch
für Reset-Mails. Ohne explizites `PASSWORD_RESET_MAIL_ENABLED` wird der Wert von
`ATTENDANCE_MAIL_ENABLED` übernommen. Ohne aktivierten Versand wird keine Mail
verschickt und kein Reset-Link im Browser oder Log ausgegeben.

SMTP-Fehler erscheinen ohne Adressen oder Tokens im Log; es gibt keinen
automatischen Wiederholungsversand. Nach Beheben der Konfiguration kann nach
zwei Minuten ein neuer Link angefordert werden. Für öffentliche Installationen
sollte der vorgeschaltete Proxy zusätzlich Anfragen pro IP begrenzen.

Hibernate ergänzt die Token-Tabelle und die Versionsspalte für den
Sitzungswiderruf beim Start über die vorhandene Schema-Aktualisierung.

## Namen ändern

Unter `/account` kann jedes Mitglied seinen eigenen Anzeigenamen ändern.
Der Name muss nach Entfernen äußerer Leerzeichen 1 bis 80 Zeichen lang sein.
Gespeichert wird nach `/account/name` mit Anmeldung und CSRF-Schutz; eine
mitgesendete fremde Benutzer-ID hat keine Wirkung. Die Sitzung bleibt bestehen.
Profil und Teilnehmerlisten verwenden anschließend den aktuellen Namen.
Bereits eingereichte Formularantworten und versandte E-Mails bleiben erhalten.
