# Offline development desk

These notes are written for this repository: Spring Boot **4.1.1**, Java **25**, Gradle **9.7.1**, Thymeleaf, Spring Data JPA / Hibernate, and H2.
The examples are local and self-contained; reference links are optional reading for when you have internet again.

## Find what you need

- **Understand the planned activity tracker:** [Project outline and implementation order](project-outline.md).
- **Start or package the app:** [Spring Boot commands](spring-boot-cheatsheet.md#everyday-commands).
- **Understand where code belongs:** [Project map](spring-boot-cheatsheet.md#project-map).
- **Use entities, JPA repositories, relationships, and DTOs:** [JPA essentials](spring-boot-cheatsheet.md#entities-repositories-and-transactions).
- **Add data owned by the signed-in user:** [User-owned data and pages](user-owned-data.md).
- **Add a page or endpoint:** [Controllers](spring-boot-cheatsheet.md#controllers-and-http).
- **Bind a form and display errors:** [Thymeleaf forms](frontend-cheatsheet.md#forms-and-validation).
- **Understand sign-in, sessions, and logout:** [Authentication forms](frontend-cheatsheet.md#sign-in-and-sign-out).
- **Style a page:** [CSS basics](frontend-cheatsheet.md#css-you-will-use-often).
- **Call a JSON endpoint:** [Optional JavaScript](frontend-cheatsheet.md#optional-javascript-and-json).
- **Open the database and practise SQL:** [SQL shell](sql-cheatsheet.md#open-a-local-sql-shell).
- **Find SQL syntax:** [CRUD](sql-cheatsheet.md#read-create-update-delete), [joins](sql-cheatsheet.md#relationships-and-joins), [transactions](sql-cheatsheet.md#transactions).
- **Run on the Raspberry Pi:** [Deployment guide](raspberry-pi.md).

## Before disconnecting

Run from the project directory while you still have internet:

```powershell
.\gradlew.bat clean test bootJar copyDatabaseTools
.\gradlew.bat --offline clean test bootJar copyDatabaseTools
```

On Linux, use `./gradlew` instead of `.\gradlew.bat`.
The second command proves the wrapper distribution, dependencies, test tooling, and JDK are available locally.
Keep your Gradle user cache (normally `~/.gradle`) and installed JDK. `clean` removes only this project's `build/` outputs.

Also import the project into your IDE while online and download source/Javadoc attachments if you want offline completion and library documentation.
New dependencies or changed versions may still need internet. A built JAR needs only a compatible Java runtime to run.

## A small development loop

1. Find the closest existing example in `src/main`.
2. Make one change.
3. Run the relevant test or `gradlew test`.
4. Start the app and try both valid and invalid input.
5. Inspect logs and the browser Network tab if behavior differs from expectations.
6. Add any newly learned project-specific detail to these notes.

Use a separate practice database for destructive SQL exercises. Do not commit database files or passwords.
