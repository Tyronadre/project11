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
