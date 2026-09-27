# User profiles and personal blogs

Every registered member has a profile at `/users/{id}` immediately, including accounts from older databases. `/users/me` opens the signed-in person's profile. Member names on the dashboard and attendance lists link to their profiles. Pages are visible only to signed-in group members.

The profile timeline combines completed holidays, past group events, and personal blog entries, newest first. A holiday links to its existing Reisebericht when one has been filed. Holidays appear after their final day in Berlin time; events appear after their stored end time. Attendance uses the saved roster and attendee list: attended, absent, not in that roster, or not yet recorded. Missing attendance is never treated as absence. Unrecorded events that ended before the user joined are omitted. Upcoming events remain in the calendar.

Only the profile owner can change the profile or create, edit, and delete its blog entries, even when the viewer is an administrator. Profile color, birthday, PayPal email or paypal.me URL, IBAN, and all 20 ordered German fact-sheet questions are editable. The questions live in `ProfileQuestions`; stable keys preserve existing answers when wording or ordering changes. Answers are optional, with up to 300 characters each. Empty fields remove saved values. IBAN input is normalized and checked for structure and checksum.

Payment values are excluded from the normal profile view model and HTML. Clicking “Zahlungsdaten anzeigen” makes an authenticated, CSRF-protected POST to `/users/{id}/payments`; the response is marked `no-store`. JavaScript displays the returned fragment and allows hiding it again. Without JavaScript the same action opens a dedicated page with a return link. Payment values are stored in the database, like other profile data; the reveal control is a display choice, not a secret shared only with selected members.

Blog entries use a continuous rich-text editor with selectable passages. Users can apply serif, sans-serif, or monospace fonts, colors, bold, italic, underline, and strikethrough to individual words or selections across paragraphs. Paragraph styles include normal text, headings, and quotes. Standard keyboard shortcuts, undo/redo, copy/paste, blank paragraphs, and warnings before leaving unsaved work are supported. The formatted editing surface is also the preview. JavaScript is required for editing; if the editor cannot load, saving is disabled so existing content cannot be accidentally overwritten.

Quill 2.0.3 is bundled locally in `static/js/vendor` and `static/css/vendor`, with its license alongside the JavaScript. No CDN or Node build is required at runtime. The client converts the editor document into the existing ordered paragraph structure (up to 40 paragraphs, 5,000 characters each), with validated inline text runs. `BlogInline` accepts only text and known formatting attributes; raw HTML and media are not accepted. Published content is rendered through escaped Thymeleaf text, without running the editor. Existing posts without inline content retain their original formatting and open in the new editor.

Hibernate's existing schema-update process creates `user_profiles`, `profile_answers`, `blog_entries`, and `blog_blocks`. Profile rows are created on first save, so old accounts need no data backfill. The nullable `blog_blocks.inline_content` column stores selection formatting on first edit. New functionality does not rewrite the existing user, holiday, travel-report, or attendance data.

`ProfileIntegrationTests` covers access rules, CSRF, validation, payment reveal, timeline associations, blog persistence, escaped output, and malformed block collections. Run the normal Gradle `test` task. Browser verification can use a separate in-memory H2 database to avoid altering real group data.

## Direkt lesbares Journal

Das moderne Profil stellt vergangene Urlaube mit den eingereichten AaB-Angaben und dem zugehörigen Reisebericht direkt im chronologischen Journal dar. Berichtstexte und bereits eingereichte Fotos sind ohne Wechsel ins Amt lesbar. Noch nicht eingereichte Fotodrafts werden nicht angezeigt. Die vorhandenen Archivlinks bleiben ergänzend verfügbar.

Eingereichte AaAs werden vollständig beim zugehörigen Event angezeigt, auch wenn dieses noch bevorsteht oder inzwischen abgesagt wurde. Es erscheinen nur die Anträge des Profilinhabers. Die tatsächliche Teilnahme bleibt unabhängig vom Antrag sichtbar; aus einem AaA wird keine Abwesenheit abgeleitet. Events ohne Antrag behalten ihre kompakte Teilnahmeübersicht, während Reisegeschichten, Anträge und eigene Blogbeiträge größere Lesebereiche erhalten.

Alle angemeldeten Mitglieder können diese Inhalte lesen. Texte werden als Text ausgegeben und HTML-escaped; Zahlungsdaten werden weiterhin ausschließlich nach ausdrücklichem Abruf angezeigt.

Anträge und Reiseberichte sind im Profil standardmäßig eingeklappt. Titel und Eingangsdatum bleiben sichtbar; per Klick oder Tastatur lässt sich der vollständige Beitrag inklusive Fotos öffnen. Der gespeicherte AGB-Volltext wird im Profil durch „Akzeptiert.“ ersetzt. Die Originaldokumente im Amt bleiben unverändert. Eigene Blogbeiträge und die Teilnahmeübersicht werden weiterhin direkt angezeigt.
