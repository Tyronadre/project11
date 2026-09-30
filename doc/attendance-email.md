# Attendance, application decisions, and emails

## Workflow

1. After an event ends, an admin or its creator records actual attendees at
   `/events/{id}/attendance`. Saving creates a draft and sends no emails.
   The roster is fixed on first save to members who had joined by the event end.
2. An admin separately confirms the saved list. The page shows exact recipient
   names/addresses and why others are excluded: attended, holiday, existing AaA,
   already emailed, or expired deadline. Confirmation rechecks both the saved
   version and recipient IDs; a stale review receives a conflict and must reload.
3. Only confirmed lists release reminder emails and automatic event marks.
   Editing any list invalidates its confirmation and cancels unsent reminders.
   Previously sent reminders are retained and never deliberately resent.
4. The cutoff is midnight Berlin on the event start date plus seven calendar days:
   **event on 1 January → cutoff on 8 January, 00:00 Europe/Berlin**. Filing is
   strictly before the cutoff. All-day/overnight events and daylight-saving
   changes use the same calendar-date rule.
5. A worker starts after 15 seconds, then checks every minute. At or after the
   cutoff, each confirmed unexcused absence adds one actual tally mark. It also
   catches up after downtime. Pending timely AaAs hold the mark; accepted AaAs
   excuse it; rejected AaAs incur it from the cutoff. Late AaAs are void.
6. Admins review all answers at `/admin/aaa` and accept or reject an AaA. Rejection
   needs a reason. The final decision queues one email, and rejection after the
   cutoff reconciles the mark immediately. The old portal continues to display
   absurd delay explanations; it does not control the actual decision.

Holidays excuse an event only if submitted before its cutoff and if they cover
its entire Berlin calendar-day span. Adjacent/overlapping holidays can jointly
cover the span; a midnight exclusive event end does not add an extra day.
Covered members receive neither reminders nor event marks, even if an AaA was
rejected. A rejected AaB provides no exemption; a pending AaB holds event marks until a decision. Retroactive leave submitted after the event cutoff normally cannot clear a mark. If accepted leave is later invalidated and charged by vacation day, those daily marks replace any marks for covered events to prevent double penalties. See [the travel workflow](application-portal.md#automatic-invalidation-and-marks).

The `attendance_penalties` table records whether each event/member mark is applied.
Event locks, ordered user locks, and a unique pair prevent duplicates and lost
increments when multiple events settle concurrently. Confirmed corrections to
actual attendance can reverse an earlier automatic mark once. Merely saving a
correction leaves actual marks unchanged until admin confirmation. Existing manual
counts are preserved; manual tally controls remain available to admins.

Upgraded attendance sheets start **unconfirmed** and therefore do not suddenly send
emails or apply historical penalties. Review and confirm them explicitly. Existing
AaAs without a stored decision are treated as pending. Schema additions are created
by Hibernate; no data reset is needed. AaBs and reports now have separate admin decisions and automatic daily
penalties. Existing undecided travel records require explicit review before any
daily penalties apply.

## Enable delivery

For a local Postfix server on your Raspberry Pi that delivers directly without
an external relay, use the [Docker deployment guide](raspberry-pi-docker.md).
Its Compose configuration sets the application SMTP connection automatically.

Email is disabled by default. Set these environment variables on the machine
running the application, using your SMTP provider’s credentials, then restart:

| Variable | Example / default | Purpose |
| --- | --- | --- |
| `ATTENDANCE_MAIL_ENABLED` | `false`; set to `true` | Enable reminders, AaA/AaB/report decisions, and event changes/cancellations |
| `PUBLIC_URL` | `https://friends.example.com` | Actual public app URL used in links; no trailing slash needed |
| `MAIL_FROM` | `Project 11 <notifications@example.com>` | Sender address accepted by your provider |
| `SMTP_HOST` | `smtp.example.com` | Provider’s SMTP host |
| `SMTP_PORT` | `587` | SMTP port |
| `SMTP_USERNAME` | empty | SMTP login |
| `SMTP_PASSWORD` | empty | Provider password or app password |
| `SMTP_AUTH` | `true` | Whether SMTP authentication is required |
| `SMTP_STARTTLS` | `true` | Require STARTTLS before sending |
| `SMTP_SSL` | `false` | Use implicit TLS instead of STARTTLS |

For providers using implicit TLS on port 465, set `SMTP_PORT=465`,
`SMTP_SSL=true`, and `SMTP_STARTTLS=false`. For a local development mail catcher,
use its host/port, `SMTP_AUTH=false`, and `SMTP_STARTTLS=false`.
Keep credentials in the deployment environment, not in committed files.
Spring Boot does not automatically load a `.env` file; configure your process,
IDE run configuration, or systemd environment explicitly.

With delivery enabled, missing host, invalid sender, or invalid public URL prevents
startup. SMTP connectivity is checked when sending, so a provider outage does not
prevent the app from starting or attendance from being saved.

Explicit confirmations and AaA/AaB/report decisions queue messages even while SMTP is disabled.
Enabling delivery resumes those queues; no messages are created by scanning old
attendance. Expired or resolved reminders are cancelled before sending. Decision
notifications remain deliverable after the filing cutoff. Sign-in preserves the
requested destination from the event/application links in each plain-text email.

## Delivery and corrections

- Attendance confirmation and queue changes commit in one database transaction;
  AaA/AaB/report decisions and their notifications likewise commit together.
- A background worker starts after 15 seconds, processing up to 20 decision emails
  per application family (AaA and travel) and 20 reminders every 30 seconds. SMTP timeouts are five seconds.
- Before each reminder, the worker rechecks confirmation, attendance, AaAs,
  holidays, and the cutoff. No reminders are sent once filing has closed.
- Reminder rows are unique per event/member; decision rows are unique per
  application in their respective AaA or travel table. Successful messages remain recorded across saves and restarts.
- Failed sends retry after 1, 2, 4, 8, and 16 minutes. After six failed attempts,
  the row becomes `FAILED`. Fix the SMTP problem, then save and explicitly
  reconfirm attendance to retry relevant failed reminders. Failed decision rows
  require an operator to reset `status` to `PENDING` and `attempts` to zero after
  fixing the provider; resubmitting an already final decision does not queue more mail.
- Queue states are `PENDING`, `SENT`, `CANCELLED`, and `FAILED`. Logs identify IDs,
  attempts, and states without printing addresses or provider errors. `SENT`
  means SMTP accepted the message; the provider handles inbox delivery/bounces.
- Event and queue row locks serialize concurrent workers. A crash after SMTP
  acceptance but before committing `SENT`, or an ambiguous SMTP timeout, can still
  duplicate a retry. SMTP cannot atomically commit with the application database.

Tests use an isolated H2 database, a controllable Berlin clock, and a mocked SMTP
sender. They cover authorization, explicit confirmation, changed recipient lists,
mail deduplication/retries, deadline boundaries, holiday coverage, real decisions,
late filing, concurrent settlement, catch-up, and confirmed corrections.

## Event changes and cancellations

[Event management](event-management.md) uses the same SMTP settings and dispatcher. Updates and cancellations queue a separate message for every current group member. Cancellation stops reminders, prevents new AaAs and reverses automatic event marks.
