# AGATA application portal

The **Applications** navigation link on the marks and calendar pages opens `/amt`. AGATA stands for *Amt für Gruppenaktivitäten und temporäre Abwesenheiten*. It follows the concept in [bad-protal.md](bad-protal.md), retaining that file's existing spelling.

The modern pages use `app.css`; portal pages exclusively use `amt.css` and `amt.js`. The portal is intentionally styled like an old German administrative website: square beveled buttons, cramped tables, blue title bars, yellow notices, excessive reference numbers, and rubber stamps. No external fonts, scripts, or services are required.

## Implemented AaA workflow

1. Open `/amt/aaa` to start a session-local draft. The redirect assigns a stable draft address and questionnaire.
2. Select an existing activity and fill in all specified AaA fields, including the redundant activity date, travel and food plans, priority explanation, and at least 50 characters of detailed justification.
3. Answer three randomly selected supplementary questions, one knowledge question, one absurd scenario, and one loyalty question. The knowledge answer must be correct. The supplementary fields accept fictional answers.
4. Confirm the three confirmations, then the additional confirmation of those confirmations. Accept the Allgemeine Gruppenbedingungen in section E.5 with their separate required checkbox.
5. Submit for preliminary review. Validation errors preserve the entered answers and questionnaire. A selected minority of drafts require 17 extra justification characters (67 total instead of 50).
6. Inspect the complete application and confirm preparation. Then confirm again on a separate final page to actually file it.
7. Read the immutable application under its `AaA-YEAR-NUMBER` reference in the shared archive or the author's member page.

The random pool contains 20 supplementary questions. Each draft draws three without replacement. A rare additional question appears in 2% of drafts; the 17-character requirement applies to 15%. Question text and answers are saved together so old applications remain readable when the pool changes.

The JavaScript shows one shared thank-you notice below section C after all three supplementary answers are filled and reorders those fields once after focus leaves that group. It never changes field names or answers. The intentionally inaccurate progress estimate has no effect on validation. Without JavaScript, all sections remain visible together and the filing flow still works; reduced-motion preferences suppress decorative movement.

## Real rules versus satire

- The signed-in account owns the application; form data cannot choose another owner.
- The selected activity must exist, and its repeated date must match its start date in Berlin.
- The cutoff is **00:00 Berlin on the event's start date plus seven calendar days**: an event on 1 January has its cutoff on 8 January at midnight. This also applies to all-day and overnight events and daylight-saving changes. Filing must occur strictly before that instant, checked at preparation and again at final filing. Merely starting or confirming a draft does not preserve the deadline.
- At most one AaA can be filed per user/activity. Repeated final requests return the existing filing instead of duplicating it. A database uniqueness constraint also protects this relationship.
- Every signed-in member may read all submitted applications and member archives. No edit or delete routes exist for filed applications.
- Admins review complete applications at `/admin/aaa`, then accept or reject them. Rejection requires a reason. Each application has one final decision, with an email notification queued in the same transaction; repeated requests cannot produce duplicate decisions or notifications. Timely pending applications hold automatic marks; accepted applications excuse the absence, while rejected applications incur a mark from the cutoff if absence is confirmed and no holiday covers the event. Legacy applications filed at or after the cutoff are void and cannot be accepted.
- The old portal cycles through eight absurd delay explanations and error messages, changing every three minutes. It never fabricates an approval. Real decisions are communicated by email; the comic status has no effect on filing time or marks.
- The fictional reliability index starts at 87 and loses four points per filed AaA or AaB, with a floor of zero. It never changes the marks dashboard.

## Records and routes

`AbsenceApplication` links the actual user and activity to the Berlin submission timestamp. It stores an activity-title snapshot and an ordered collection of question/answer snapshots. Hibernate generates both the application and answer tables.

`AbsenceDraft` holds a stable questionnaire, form values, and confirmation stage in the HTTP session. Up to eight recent drafts are retained per session. Unsubmitted drafts are not persisted, and unsent browser edits are not autosaved; leaving the session or restarting the app can discard them.

| Route | Purpose |
| --- | --- |
| `/amt` | Portal home, procedures, recent filings, member directory |
| `/amt/aaa` | New or existing AaA draft; optional `activity` preselects an eligible event |
| `/amt/aaa/pruefen` → `/amt/aaa/pruefung` | Validate and review |
| `/amt/aaa/bestaetigen` → `/amt/aaa/freigabe` | First confirmation and final confirmation page |
| `/amt/aaa/einreichen` | Final filing; redirects to the saved application |
| `/amt/akten` | Paginated group-wide archive |
| `/amt/mitglieder/{id}` | A member's chronological application history |
| `/amt/antraege/{id}` | Complete filed application and status journey |
| `/admin/aaa` | Admin-only list of applications and actual decisions |
| `/admin/aaa/{id}` | Read the full application; POST a final acceptance or rejection |
| `/admin/travel` | Admin list of AaBs and travel reports, with real decision/invalidity state |
| `/admin/travel/{id}` | Read answers and report photos; POST a final decision |

All submission actions use POST and Spring Security CSRF protection. Admins and event creators can save attendance after events. A separate admin confirmation releases the reviewed reminders and automatic marks; see [attendance workflow](attendance-email.md). Every signed-in member can create activities through the modern calendar and `/events/new`. An existing eligible activity is required to complete an AaA; with no activities, the entire form remains viewable but submission is disabled.

## AaB: Beurlaubung

`/amt/aab` opens a leave draft with inclusive first/last holiday dates, destination, stopovers, companions, accommodation, purpose, meals, Internet availability, contact details, thoughts of the group, a justification of the need for rest (maximum 500 characters), and the group-relationship question. Choosing “Ich bereue meinen Antrag bereits” produces the deliberately unhelpful warning with two affirmative options.

The form uses the same randomized personal/knowledge/loyalty questions and four confirmations as the AaA. Review and final confirmation pages follow the same route suffixes under `/amt/aab`. Filing atomically creates a `Holiday` calendar block and an immutable `TravelApplication` of kind `LEAVE`. Both selected dates count as holiday days. Past periods can also be entered. For event exemptions, holidays must have been filed before that event's AaA cutoff and jointly cover its entire Berlin day span; adjacent or overlapping periods can provide that coverage. A rejected AaB does not excuse events. Pending AaB review holds event marks until a decision. Late leave normally cannot erase a final event mark; the exception is replacement by daily vacation penalties, which must not be combined with penalties for covered events. Holiday records remain separate in the calendar.

Each submitted AaB starts pending and appears at `/admin/travel`. An admin accepts or rejects it; rejection needs a reason. Decisions are final, recorded with reviewer/time/reason, and each queues one email. A report is reviewed separately; it cannot be confirmed before its AaB is accepted. Submitted answers and photos remain immutable. Every member can see the calendar block and read the application.

The old portal deliberately cycles through absurd processing excuses and errors instead of displaying the actual approval state. Admin pages show the real decisions, and the applicant receives them by email. These comic explanations never affect the receipt or deadline.

## EeR: Reisebericht

`/amt/eer` lists the signed-in user's completed holidays that do not yet have a report, excluding rejected AaBs. Reports may already be filed while AaB review is pending, so admin delays do not prevent timely receipt; confirmation still requires acceptance of the AaB. Legacy holidays without a linked AaB can also receive a report. The form includes the actual itinerary, deviations, insight, cultural and culinary experiences, regret, a 1–10 rating, willingness to repeat the trip, and separate truth/consequences checkboxes. It also has the shared nuisance questions and repeated confirmations.

**Confirmed deadline:** the report must arrive by the end of the seventh day after the last holiday date in `Europe/Berlin`. For example, a holiday ending on 10 September has a deadline of 17 September, 23:59:59… Berlin time. Internally, the exclusive cutoff is midnight starting 18 September, preserving calendar-day semantics across daylight-saving changes. Filing is possible starting on the day after the holiday ends. Drafts and preliminary confirmations do not preserve a deadline.

For the requested example: **holiday ends 1 January → report may arrive throughout 8 January → assessment starts 9 January at 00:00 Berlin**. The exact receipt, not when the draft was started or the admin reviewed it, determines timeliness.

Late reports can still be archived and reviewed, but neither filing nor later admin confirmation restores the holiday or removes the daily marks. The actual receipt and late label remain visible. One report per holiday and one final admin decision are allowed; a rejected report cannot be replaced with a second filing.

### Automatic invalidation and marks

- Only an explicitly **accepted AaB** creates the report penalty obligation. Pending or rejected leave is not charged by vacation day.
- At the cutoff, a missing, late, or rejected report invalidates accepted leave permanently. A timely pending report holds the marks until a decision. Acceptance excuses the leave; rejection after the cutoff invalidates it immediately. Rejection before the cutoff waits until the cutoff for assessment.
- Both boundary dates count. A three-day invalid holiday adds three marks once, not three more on each scheduler run. `travel_day_penalties` is unique per user/date, so overlapping invalid holidays never charge the same day twice.
- Invalidated accepted leave continues to exclude covered events from additional event marks. Any already applied event penalties for the covered period are removed in the same transaction as the daily marks are added. Events outside that coverage remain chargeable. Event coverage still means the full Berlin day span, allowing adjacent holidays to jointly cover it.
- A worker starts after 15 seconds and runs every minute, also catching up after downtime. Invalidation time and each charged date are persisted. User locks serialize travel filing, reviews, daily settlement and tally changes; event settlement locks users in a stable order before re-reading coverage, so concurrent checks do not lose or duplicate marks.
- The existing manual tally controls remain available. Existing totals are preserved and automatic changes are applied as increments/corrections.
- On upgrade, existing AaBs and reports without a decision start **pending**, never silently accepted. Legacy holiday-only records continue to excuse events as before but do not acquire retrospective report penalties. Explicitly accepting an old AaB after its cutoff can trigger assessment immediately; inspect its linked report before deciding.

Decision emails use the same opt-in SMTP settings and durable retry behavior as AaAs; see [email configuration](attendance-email.md). No real mail is sent by the integration tests.

### Photo attachments

- Three to six photos are required. Accepted formats: JPEG and PNG; maximum 4 MiB per image, 20 megapixels, and 12 MiB total after normalization. HTTP requests are limited to 26 MiB.
- Images are decoded and re-encoded before storage; uploaded metadata and trailing content are not served. Original filenames are displayed as escaped text only.
- Accepted uploads survive form validation errors and are attached to the session draft via a UUID. Only the owner can read or remove unfiled photos. Form text remains session-local; unsent changes are not autosaved.
- Unfiled photos older than 24 hours are cleaned up when a new travel procedure begins. A draft whose photos expired must supply the missing attachments again.
- On final filing, photos become immutable report attachments visible to all signed-in members. They are served by the protected `/amt/fotos/{id}` route with `Cache-Control: no-store`.
- Photos are persisted as database BLOBs; the normal H2 database backup therefore includes them.

`TravelApplication` links the author, holiday, type, actual receipt, filing UUID, and question/answer snapshots. Unique constraints prevent duplicate filing UUIDs and more than one application of each kind per holiday. Owner locking and idempotent final submission protect repeated requests. The report and leave detail pages at `/amt/reisen/{id}` link to each other. The group archive and member histories merge AaA, AaB, and EeR chronologically with shared pagination. Reports do not decrease the fictional reliability index.

Hibernate generates all new tables and relationships. No manual SQL schema is required.

## Section-by-section forms and random detours

All three forms display one section at a time when JavaScript is enabled. Each section has a **Weiter** button, with a back button from the second section onward. Continue validates the current section; the final preparation action validates all sections again and reveals any invalid field. Server-side validation remains authoritative and reopens the section containing the first reported field error. All controls remain in the same form, so moving between sections retains text and selected photo files. Navigation between sections is not an autosave; the existing session draft is updated when the form is sent to preliminary review.

Each successfully completed section has a 70% chance of an administrative detour. A shuffled pool of twenty-four incidents avoids repeating the same incident in different sections during a page visit:

- Three original stamps for an original, a copy, and the copy of the copy.
- A paperless printer requiring virtual paper and imaginary toner before shredding the result.
- Referral between departments until an office plant takes responsibility.
- A waiting ticket called in fractional numbers despite an empty queue.
- A revision that reduces the decorative progress estimate to 41%.
- A double-negative confirmation with the option to continue under protest.
- A roaming Weiter button that escapes into another cell of its bounded area, at most three times. Mouse hover triggers an escape; touch/pen taps trigger it where pointer events are supported. Keyboard activation goes straight through. A stationary “Dienstaufsicht” button can freeze it at any time. Reduced-motion mode keeps it stationary.
- A slider to calibrate silence to 100%, followed by confirmation that nobody has anything to add. No audio is played or recorded.
- A humanity check accepting that the applicant is, at least, not a paperclip.
- Virtual coffee whose stain becomes the official seal.
- Cover sheets requesting additional cover sheets before budget cuts end the recursion.
- A digital fax sent to the same computer, complete with imagined beeping.
- An upside-down notice that must be turned over with a button.
- A raisin preference written on a cookie that is immediately eaten. No actual browser cookies or preferences are changed.
- A mouse-pointer parking inspection, fulfilled by hovering over or activating the marked parking bay.
- A shrinking Weiter label, followed by a large magnifying-glass button after three clicks. The actual hit target stays large throughout.
- Rejected writing instruments, ending with an imaginary signature.
- A working set of elevator floor buttons: selecting the second floor first diverts to the basement, then reaches the intended floor on the second request.
- An objection to clicking too quickly, followed by a request to click slowly. The next attempt is accepted regardless of measured duration; no mandatory waiting or precise timing is required.
- Three checkboxes that must remain unchecked and a fourth that confirms that nonselection. Incorrect choices can simply be unticked.
- A grumpy wastepaper basket receiving a fictional blank sheet as a gift.
- A decorative progress bar inflated to 100% with three pump strokes, followed by closing the valve.
- An objection from the applicant's future self, which can be contradicted or deferred.
- A red ribbon requiring an imaginary scissors permit, a cut, and inward applause before the next section opens.

Text detours take at most three affirmative actions; the moving button permits at most three escapes, and the silence slider has one calibration plus two confirmations. The additional interactive detours have finite sequences: three shrink/pump actions plus a final confirmation, two elevator requests for floor 2 plus exit, or a short parking, checkbox, timing, gift, or ribbon procedure. Wrong checkbox choices or elevator destinations remain correctable. All detours support keyboard and touch and can be left to return to the current answers. A completed detour is not repeated when revisiting its section during the same page visit. They do not alter answers, attachments, deadlines, marks, or submission state. Real filing still requires the existing server-side review and two confirmations. Reduced-motion preferences suppress smooth navigation scrolling.

AaA questions **A.4 (duration)** and **A.8 (return time)** have been removed from new forms and their validation. Existing filed answer snapshots remain readable. Remaining question numbers retain their identifiers. **B.2 normally requires 50–6,000 characters** after trimming; the randomly selected 15% of AaA drafts require at least 67 characters. This requirement stays fixed for the lifetime of the draft and is reported by server-side validation.

## Allgemeine Gruppenbedingungen in section E.5

All three forms display the same fictional terms, version `AGB-01`, with twelve paragraphs about administrative timekeeping, coffee seals, office plants, pasta shapes, paperless paperwork, weather, silence, raisins, and wandering buttons. The full text is available in a scrollable, keyboard-focusable region. A separate unchecked-by-default checkbox is required to continue and is also validated on the server. Acceptance does not depend on scroll position or JavaScript.

`PortalTerms` supplies the same version and wording to the displayed form and the saved answer snapshot. The review and filed application show the explicit acceptance and the full accepted wording. Existing applications are not retroactively changed. The terms are part of the portal's satire and do not change marks or deadlines.
