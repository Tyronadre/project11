# Aktenzeichen, Bearbeitungsverlauf und Bescheide

## Aktenzeichen

AaA, AaB und EeR behalten das bisherige Format `AaA-2026-000001`.
Das Jahr stammt vom unveränderlichen Eingangszeitpunkt in Europe/Berlin;
die laufende Nummer ist die Datenbank-ID innerhalb des jeweiligen Bestands.
Sie wird nicht jährlich zurückgesetzt. Typpräfixe unterscheiden die Bestände.
Bestehende Links und Aktenzeichen bleiben erhalten, eine Migration ist nicht nötig.
Die Referenz steht in Registratur, Akte, Adminprüfung, Entscheidungs-E-Mail und Bescheid.

## Tatsächlicher Verlauf

Die Aktenansicht zeigt gespeicherte Eingänge und Entscheidungen mit Zeitpunkt,
entscheidender Person und Begründung. Verknüpfte AaBs/EeRs erscheinen mit Links;
spätere Eventabsagen und der Verfall einer Beurlaubung werden ebenfalls angezeigt.
Die Anzeige entsteht aus bereits gespeicherten Fakten, verändert keine Daten und
funktioniert auch für bestehende Akten. Nicht gespeicherte Zwischenprüfungen werden
nicht erfunden. Namen entsprechen den aktuellen Mitgliedskonten. Falls ältere Daten
keinen Entscheidungszeitpunkt oder Bearbeiter enthalten, steht „Nicht dokumentiert“.
Der frühere rotierende Spaßstatus wird durch den tatsächlichen Stand ersetzt.

## Druckbarer Bescheid

Nach Annahme oder Ablehnung erscheint „Bescheid öffnen / drucken“.
Die Ansichten `/amt/antraege/{id}/bescheid` und `/amt/reisen/{id}/bescheid`
enthalten Aktenzeichen, Person, Betreff, Eingang, Entscheidung, Begründung,
Bearbeiter, Verlauf und die aktuelle Wirkung. Ein späterer Verfall oder eine
Eventabsage wird neben der ursprünglichen Entscheidung sichtbar. Die Seite ist
somit eine aktuelle Ausfertigung, kein unveränderliches Dokumentarchiv.

Die Druckansicht verwendet A4 mit Druckrändern, blendet Bedienelemente aus und
unterstützt mehrseitige Begründungen. Der Knopf öffnet den Browserdruckdialog;
dort kann auch als PDF gespeichert werden. Ohne JavaScript funktioniert Strg+P
bzw. Cmd+P. Es ist kein serverseitiger PDF-Generator erforderlich.

Nicht entschiedene Vorgänge liefern beim direkten Bescheidaufruf HTTP 409,
unbekannte Akten HTTP 404. Alle Ansichten setzen Anmeldung voraus und sind wie
die bisherigen Akten für sämtliche Gruppenmitglieder lesbar. Neue Bescheide
werden durch Lesen oder Drucken weder entschieden noch per E-Mail versandt.

## Prüfung

`bash gradlew test bootJar` prüft den vollständigen Stand. Die Bescheidtests
prüfen Berechtigungen, Altdatenreferenzen/Jahreswechsel, offene und entschiedene
Akten, HTML-Escaping, verknüpfte Vorgänge, Absagen und den Verfall von Urlaub.
Eine fiktive lange Ausfertigung für die visuelle Prüfung wird unter
`build/notice-preview/notice.html` erzeugt.
