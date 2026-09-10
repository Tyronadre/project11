# Project 11

A small Spring Boot learning project with registration, sign-in, and a protected welcome page.
Spring Boot serves the frontend itself: **Thymeleaf + HTML/CSS**, with **Spring Data JPA / Hibernate + H2** for storage.
There is no Node.js build, separate frontend server, CDN, or external database to install.

## Run locally

Use **JDK 25** (the version already selected in this project) and the included Gradle wrapper.

Windows PowerShell, from the project directory:

```powershell
.\gradlew.bat bootRun
```

Linux / macOS:

```bash
chmod +x gradlew
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080). Stop with `Ctrl+C`.
The first build needs internet to download Gradle and dependencies. After preparation, development and the application work offline.

## What is implemented

- Name, email, password, and password confirmation.
- Server validation, useful field errors, and a responsive page that works without JavaScript.
- Trimmed names; trimmed, lowercase email addresses; a database uniqueness constraint.
- BCrypt password hashing, CSRF-protected forms, and escaped HTML output.
- A success page reached after saving; refreshing cannot resubmit the original POST.
- A persistent database at `data/project11.mv.db`, relative to the working directory.
- A mapped `AppUser` entity, `JpaRepository`, transactional service, and separate form object.
- Email/password sign-in backed by the registered users, with a Spring Security session.
- A shared, read-only calendar at `/calendar`, with month navigation, activity/holiday filters, and an agenda. Activity times and today's date always use `Europe/Berlin`; holidays include both selected dates.
- A protected, otherwise empty `/welcome` page and CSRF-protected sign-out.

Start at `/signin`, or create an account at `/register`. Registration success links to sign-in; successful sign-in opens `/welcome`.
Email verification, password reset, and user management are future work.
Activity creation and holiday submission will be added later through separate workflows. The calendar already reads persisted `Activity` and `Holiday` records; it shows an empty state until records exist and does not add sample data to your database.
For public deployment, add HTTPS and registration abuse controls; see the [Pi guide](doc/raspberry-pi.md).

## Test and package

```powershell
.\gradlew.bat test bootJar copyDatabaseTools
```

Linux equivalent: `./gradlew test bootJar copyDatabaseTools`.

- Application: `build/libs/project11.jar`
- Test report: `build/reports/tests/test/index.html`
- Offline SQL shell: `build/tools/h2-<version>.jar`

Run the packaged app with `java -jar build/libs/project11.jar`.
Tests use an isolated in-memory database and do not write to `data/`.

## Offline documentation

Start with [the offline guide index](doc/README.md).

| Guide | Contents |
| --- | --- |
| [Spring Boot cheatsheet](doc/spring-boot-cheatsheet.md) | Entities, repositories, relationships, transactions, DTOs, controllers, validation, configuration, testing |
| [User-owned data and pages](doc/user-owned-data.md) | Secure ownership, per-user create/list/detail pages, related records, and tests |
| [Frontend cheatsheet](doc/frontend-cheatsheet.md) | HTML, CSS, Thymeleaf forms, validation errors, accessibility, optional JavaScript/fetch |
| [SQL cheatsheet](doc/sql-cheatsheet.md) | Open the database, CRUD, joins, transactions, constraints, indexes, backup, schema changes |
| [Raspberry Pi and offline setup](doc/raspberry-pi.md) | Cache dependencies, run a JAR, systemd service, backups, logs, updates |

## Configuration

Hibernate creates and updates the database schema from entity annotations (`spring.jpa.hibernate.ddl-auto=update`). There is no `schema.sql` to maintain. Add an entity or mapped field and restart to apply supported changes. Back up before changing persistent data; renames, data transformations, and changes to existing constraints may require explicit migrations.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:h2:file:./data/project11;DB_CLOSE_ON_EXIT=FALSE` | Database location |
| `DB_USERNAME` | `sa` | H2 database user |
| `DB_PASSWORD` | empty | Local learning default; set before first creation on a server |

The H2 web console and database network server are not enabled. Inspect SQL using the local shell while the app is stopped.
Changing database credentials in the environment does not change credentials of an existing database.

For template development:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

The dev profile disables Thymeleaf's template cache. Restart `bootRun` after resource changes unless your IDE is copying them to the runtime classpath.

## Request flow

```text
Browser -> Spring Security (CSRF) -> RegistrationController
        -> RegistrationForm validation -> RegistrationService (hash)
        -> UserRepository (Spring Data JPA) -> AppUser / Hibernate -> H2 file
        -> redirect -> Thymeleaf success page -> Browser
```

All application packages belong below `de.tyro.project11` so Spring can discover them.

Sign-in uses Spring Security's form filter: `POST /signin` -> `DatabaseUserDetailsService` -> `UserRepository` -> BCrypt verification -> session -> `/welcome`.
`SignInController` serves the pages; it does not compare passwords. `POST /logout` invalidates the session and returns to `/signin?logout`.
