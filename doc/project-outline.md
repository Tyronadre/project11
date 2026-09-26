# Friend group activity tracker

Status: general outline and implementation progress. This document records the intended product and a suggested implementation order. AaA, AaB, and EeR contents are implemented from the bad-portal concept; event attendance, all application decisions, event penalties, and missing-report penalties are implemented; year-end rules remain later work.

## Purpose

The website helps our friend group organize activities, record participation and holidays, and track marks for missed obligations. At the end of each year, the person with the most marks loses and pays for a round for the group.

Keep the existing shared marks overview and extend it with a calendar, online forms, and a readable history for each user.

Implemented so far: a shared calendar with month navigation, activity/holiday filters, and an agenda, backed by Hibernate entities. Every signed-in user can create an activity from the calendar using a simple form with name, optional description, date, and optional Berlin start and end times. Holiday submission is handled by the AaB portal. All calendar dates and times use `Europe/Berlin`.

The separate AGATA application portal at `/amt` now implements AaA, AaB, EeR (including photos), and member application archives. Its deliberately dated presentation, nuisance questions, and repeated confirmations follow [the bad-portal concept](bad-protal.md). See [the portal guide](application-portal.md) for the exact current behavior.

## Main areas

```text
Friend group website
├── Marks overview
│   ├── Current marks for every user
│   ├── Reasons and sources of marks
│   └── Annual tally and past results
├── Shared calendar
│   ├── Activities and their participants
│   └── Users' holiday / leave periods
├── User pages (blog-style histories)
│   ├── Past applications and travel reports
│   └── Individual submissions that everyone can open and read
└── Online forms
    ├── Antrag auf Abwesenheit (AaA) — absence from one activity
    ├── Antrag auf Beurlaubung (AaB) — leave for a period
    └── Einreichung eines Reiseberichtes (EeR) — report for a holiday
```

“Everyone” means every user of the website; public access without an account is not specified. These user pages collect submissions, rather than introducing a separate general-purpose blogging feature.

## Intended rules

### Activities and absence applications (AaA)

- An activity appears in the shared calendar and has a record of its participants.
- A user who cannot or does not want to participate submits an AaA for that specific activity.
- Once one week has passed after the activity, a user receives one mark if they were not registered as a participant, did not submit an applicable AaA, and were not covered by a leave period.
- After that cutoff, handing in a late AaA or removing the resulting mark is not allowed.

AaAs must arrive before midnight Berlin on the event start date plus seven calendar days (1 January → 8 January, 00:00). The deadline is rechecked at final submission. Filed answers are immutable and unique per user/activity. Admins and creators save attendance, then an admin separately confirms the list and exact reminder recipients. Confirmed unexcused absences receive one automatic mark from the cutoff. Pending timely AaAs hold the mark until an admin accepts or rejects them; both decisions notify the applicant by email. Advance registration remains future work.

### Leave applications (AaB)

- A user can submit an AaB at any time for a specified period.
- The period appears as a block in the shared calendar, associated with that user.
- During that period, the user does not have to participate in activities and does not need a separate AaA for each activity.
- The period is also the basis for the travel-report obligation.

The current AaB accepts past as well as future periods, includes both boundary dates, creates the calendar block immediately, and cannot be changed after filing. An admin must accept the AaB and separately confirm its timely EeR. Overlapping periods remain separate records. Event exemptions require complete coverage by holidays submitted before the event cutoff; adjacent and overlapping periods can jointly cover an event. Late leave normally cannot erase a mark, but invalidated accepted leave is charged by vacation day and replaces penalties for covered events to avoid double punishment. Rejected leave gives no event exemption; pending decisions hold event marks.

### Travel reports (EeR)

- A user submits an EeR linked to their entered holiday / leave period.
- If the required report is missing when its deadline has passed, the user receives one mark for every day of the reserved holiday, regardless of scheduled activities.
- For example, a five-day holiday without the required report results in five marks. This is a penalty based on holiday duration, not a new mark for every day the report remains late.

**Confirmed deadline:** the EeR is due by the end of the seventh day after the last holiday date in `Europe/Berlin`, including daylight-saving changes. It can be filed from the day after the holiday ends. Late reports are accepted with an immutable actual receipt and a visible late label; they do not become timely through subsequent recognition of the leave. After this cutoff, accepted leave with a missing, late, or rejected report is invalidated and charged once per vacation day. A timely pending report holds the marks until the admin decision. Late filing or confirmation cannot restore validity.

All three forms follow the bad-portal concept. AaA's comic status explanations are decorative; actual admin decisions and submission time determine the mark. AaBs and EeRs receive separate final admin decisions and email notifications. Their old portal statuses also remain comic delay messages. Filed applications and report photos are immutable and readable by all signed-in members.

### Annual tally

- Show each user's marks for the relevant year.
- At year-end, compare the totals; the user with the most marks pays for a round for everyone.
- Preserve past years so previous results can be viewed.

Tie handling, the precise year boundary, and the year assigned to penalties assessed after New Year remain open.

## Relationships and suggested records

These are conceptual records; the exact Java entities and form fields can be chosen later. Hibernate will generate the schema from the eventual entity mappings.

| Record | Relationships and purpose |
| --- | --- |
| User | Has participation records, submissions, leave periods, and marks. Their history page presents their submissions. |
| Activity | Has a date/time and many participation records and AaA submissions. Appears in the calendar. |
| Participation | Connects one user to one activity. Supplies the participation information used by the mark rule. |
| Submission | Common concept for AaA, AaB, and EeR: author, type, submission time, and readable contents. Each belongs to one user and is visible to all users. |
| AaA | A submission linked to exactly one activity and its submitting user. |
| AaB / leave period | A leave submission describes one user's date range. That range creates a calendar block and determines participation exemptions. It need not be stored twice in separate records. |
| EeR | A report submission linked to a specific leave period and its submitting user. |
| Mark entry | Records the affected user, reason, quantity, assessment time, tally year, and source activity or leave period. Supports a traceable total. |

The calendar combines activities and leave periods. The user history combines that user's submissions. Neither needs a separate copy of those records.

Prefer calculating totals from mark entries over keeping only an editable counter. An event penalty contributes one mark; a missing-report penalty contributes the number of holiday days. Keep the source of each penalty so repeated checks cannot award the same penalty twice.

The current manually editable tally needs a transition plan: preserve existing totals as opening entries, and decide what manual adjustments remain allowed. General tally editing must not bypass the finality of automatic event penalties.

## Simple implementation order

1. **Confirm the rules and basic records.** Settle the deadlines, participation meaning, leave boundaries, and permissions. Define the initial relationships, while leaving detailed form contents for later specification.
2. **Activities and participation.** Activity creation, calendar agenda details, and attendance recording by admins and creators, with separate admin confirmation, are implemented. Advance registration remains optional future work. These are the foundation for activity-specific AaA submissions and missed-activity checks.
3. **Shared calendar and leave.** Calendar, AaB forms, and calendar blocks are implemented. Establish exactly which activities a leave period covers.
4. **Submissions and user histories.** AaA linked to activities, AaB, and EeR linked to leave periods are implemented with group-wide readable histories and detailed fields. Extend only as later rules require.
5. **Automatic marks.** Event marks now use durable per-event/member entries, preserve existing tallies, reject late AaAs, prevent duplicates, and catch up after downtime. Missing-report penalties now use unique user/date entries, including overlap deduplication and replacement of covered event marks. A unified annual ledger remains future work.
6. **Annual results and polish.** Connect the current overview to mark entries, add year selection and past results, and implement the agreed tie and year-boundary rules.

Dependencies: participation and leave rules must exist before event penalties; leave periods and EeR submissions must exist before report penalties; traceable marks and year-assignment rules must exist before reliable annual results.

## Decisions for later specification

- **Forms and workflow:** AaAs, AaBs and reports now have one final admin acceptance/rejection; submitted answers remain immutable. One AaA per user/activity and one EeR per holiday are allowed.
- **Deadlines:** event assessments run each minute from the Berlin cutoff and catch up after downtime. Vacation-day assessments likewise run each minute with catch-up after downtime.
- **Participation and activity management:** every signed-in user can now create activities; decide who may change them, who records participation, and what happens when an activity is canceled or rescheduled.
- **Leave boundaries:** both boundary dates count; holidays filed before the event cutoff must jointly cover its whole Berlin day span. Partial-day leave remains undefined.
- **Penalty finality:** late EeR filings and later confirmations do not restore an invalidated holiday. Any future appeal/correction procedure requires separate rules; current decisions are final.
- **Membership:** which users are expected at an activity, how joining or leaving the group affects obligations, and which past records remain visible.
- **Year-end:** calendar year versus another annual period, handling ties, and assigning marks when an activity, holiday, or deadline crosses the year boundary.

When automatic rules are implemented, verify deadline boundaries, leave exemptions, duplicate checks, catch-up after downtime, and the inability to erase final event penalties through later submissions or edits.

## Simple event creation

`GET /events/new` shows the modern, single-page form; an optional `date` query parameter prefills a selected calendar day. `POST /events` validates and persists the activity, then redirects to its shared event detail page. All signed-in members may create events; the owner is taken from the authenticated account. Description and time are optional. Form errors preserve submitted values. There are no portal questions or confirmation screens in this workflow.

New events with neither time are all-day. Start and end times are independently optional. An end-only event displays “until” its specified time without inventing a start time. An end earlier than a supplied start means the next day; identical start/end times are rejected. An end-only midnight means the end of the selected day. Explicit end times determine when attendance can be recorded. Without an end time, the next Berlin midnight remains the internal boundary for queries and attendance; it is not displayed as a claimed duration. Existing interval events retain their original times.

Nonexistent times during the spring clock change are rejected with an inline error. Ambiguous autumn times use the first occurrence (the earlier offset chosen by `ZonedDateTime`). Days follow Berlin's actual day boundaries, including 23- and 25-hour days. `Activity.timing` distinguishes all-day, start-only, and legacy interval events; a null value in an existing database retains legacy interval behavior. The nullable `Activity.startTimeUnspecified` flag distinguishes end-only events while retaining legacy start-time behavior. Hibernate adds new columns automatically. No synthetic boundary is shown as an entered time.

### Main page

The `/welcome` overview shows up to six events whose end is still in the future, ordered by start time and ID. This includes ongoing events and all-day events for today, while excluding finished events. Cards show the Berlin date/time, organizer, description preview, and a direct link to its event detail page. Today, tomorrow, and ongoing labels use the injected clock. An empty state invites members to create their first plan. The marks grid and admin controls remain below this section; the former tally hero banner is removed. The navigation label for this page is now “Overview”.

## Attendance, decisions, and automatic marks

Admins and event creators record ended-event attendance. Every save is a draft;
an admin separately reviews the saved list and exact reminder recipients before
confirming. Holiday members, attendees, timely AaA applicants, and members already
emailed are excluded. Each edit invalidates confirmation and cancels unsent reminders.
The fixed roster excludes accounts created after the event ended.

Confirmed unexcused absences become actual marks at midnight Berlin seven days
after the event start date. A minute worker catches up after downtime; unique
per-event/member records prevent duplicates. Timely pending AaAs hold marks until
admins decide them at `/admin/aaa`. Both acceptance and rejection queue an email.
Late applications are void. Confirmed attendance corrections can reverse a mark;
late AaAs cannot. Vacation-day penalties replace covered event penalties, including for invalidated retroactive leave. Existing manual tally controls remain in place.

See [the attendance and SMTP guide](attendance-email.md) for holiday coverage,
upgrade behavior, delivery guarantees, and test coverage.

## Shared event details

Every signed-in member can open `/events/{id}` from an event in the calendar grid, its agenda title, or the upcoming-event cards. After creating an event, the same page opens with a success notice. It displays the title, organizer, full description, optional location for existing records, and the same Berlin date/time presentation as the calendar. A back link returns to the event's original calendar month.

An event is considered past at its stored end instant. Past-event pages display the saved attendance roster in “Attended” and “Did not attend” groups, with the latest recording time and editor name. These are attendance facts, not penalty or excuse statuses. If attendance has not been saved, the page explicitly says so; an unrecorded list is never presented as zero attendees. Admins and the event creator see the record/edit action; the service checks ownership or the persisted admin flag on every read/write. Only admins may confirm a saved list. Ongoing and future events do not display an attendance list. All member names are rendered as escaped text; private account data is not included.
