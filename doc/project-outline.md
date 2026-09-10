# Friend group activity tracker

Status: general outline for future implementation. This document records the intended product and a suggested implementation order. Exact form contents and unresolved rules will be specified later.

## Purpose

The website helps our friend group organize activities, record participation and holidays, and track marks for missed obligations. At the end of each year, the person with the most marks loses and pays for a round for the group.

Keep the existing shared marks overview and extend it with a calendar, online forms, and a readable history for each user.

Implemented so far: a read-only shared calendar with month navigation, activity/holiday filters, and an agenda, backed by Hibernate entities. Activity creation and holiday submission are future workflows, not actions in the calendar. Every user will be able to add activities when that workflow is implemented. All calendar dates and times use `Europe/Berlin`.

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

The exact AaA submission deadline within or before that week remains to be specified. The participant record must also be defined: advance registration, confirmed attendance, or both.

### Leave applications (AaB)

- A user can submit an AaB at any time for a specified period.
- The period appears as a block in the shared calendar, associated with that user.
- During that period, the user does not have to participate in activities and does not need a separate AaA for each activity.
- The period is also the basis for the travel-report obligation.

“At any time” does not yet settle whether leave can be entered retroactively or changed after the period starts. Its effect on already assessed marks must be decided, particularly because event marks are intended to be final after the cutoff.

### Travel reports (EeR)

- A user submits an EeR linked to their entered holiday / leave period.
- If the required report is missing when its deadline has passed, the user receives one mark for every day of the reserved holiday, regardless of scheduled activities.
- For example, a five-day holiday without the required report results in five marks. This is a penalty based on holiday duration, not a new mark for every day the report remains late.

**Deadline clarification required:** the initial description says the EeR must be submitted “at least one week after” the holiday ends. Literally, this sets an earliest submission time, but no final deadline. If the intended rule is “within one week after the holiday ends,” the deadline would instead be one week after its end. Confirm this before implementing report penalties.

The exact forms, required fields, and whether submissions need approval are not yet defined. No approval process is assumed here.

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
2. **Activities and participation.** Add activity creation, activity details, and participant management. These are the foundation for activity-specific AaA submissions and missed-activity checks.
3. **Shared calendar and leave.** Show activities; add AaB forms and leave periods; display the resulting calendar blocks and establish which activities they cover.
4. **Submissions and user histories.** Add AaA linked to activities and EeR linked to leave periods. Include AaB in each user's history and let every user read every submission. Implement the detailed fields when they have been specified.
5. **Automatic marks.** Introduce traceable mark entries, migrate existing tallies, and implement the event and missing-report deadline checks. Enforce the late-AaA restriction and prevent duplicate penalties. Checks must also catch up after the application has been offline.
6. **Annual results and polish.** Connect the current overview to mark entries, add year selection and past results, and implement the agreed tie and year-boundary rules.

Dependencies: participation and leave rules must exist before event penalties; leave periods and EeR submissions must exist before report penalties; traceable marks and year-assignment rules must exist before reliable annual results.

## Decisions for later specification

- **Forms and workflow:** exact contents of AaA, AaB, and EeR; immediate effect versus approval; editing, withdrawal, and multiple submissions for the same activity or holiday.
- **Deadlines:** the AaA cutoff, the EeR deadline wording above, and whether “one week” means seven calendar days or 168 hours. The shared timezone is fixed to `Europe/Berlin`, including daylight-saving changes.
- **Participation and activity management:** every user may create activities in the future workflow; decide who may change them, who records participation, and what happens when an activity is canceled or rescheduled.
- **Leave boundaries:** inclusive start/end dates, partial days, overlapping periods, multi-day activities, and whether retroactive leave is permitted.
- **Penalty finality:** what happens to late EeR submissions, whether report penalties can ever be corrected, and how corrections to attendance or leave interact with locked event penalties.
- **Membership:** which users are expected at an activity, how joining or leaving the group affects obligations, and which past records remain visible.
- **Year-end:** calendar year versus another annual period, handling ties, and assigning marks when an activity, holiday, or deadline crosses the year boundary.

When automatic rules are implemented, verify deadline boundaries, leave exemptions, duplicate checks, catch-up after downtime, and the inability to erase final event penalties through later submissions or edits.
