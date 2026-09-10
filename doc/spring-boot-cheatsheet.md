# Spring Boot cheatsheet

For this project: Spring Boot 4.1.1, Java 25, Gradle, Spring MVC, Thymeleaf, Spring Data JPA, Hibernate, H2.
Run commands from the directory containing `build.gradle`.

## Everyday commands

| Task | Windows PowerShell | Linux / macOS |
| --- | --- | --- |
| Start | `.\gradlew.bat bootRun` | `./gradlew bootRun` |
| Dev profile | `.\gradlew.bat bootRun --args='--spring.profiles.active=dev'` | `./gradlew bootRun --args='--spring.profiles.active=dev'` |
| Test | `.\gradlew.bat test` | `./gradlew test` |
| One test class | `.\gradlew.bat test --tests '*RegistrationIntegrationTests'` | `./gradlew test --tests '*RegistrationIntegrationTests'` |
| Build executable JAR | `.\gradlew.bat bootJar` | `./gradlew bootJar` |
| Full verification | `.\gradlew.bat clean test bootJar` | `./gradlew clean test bootJar` |
| Offline verification | `.\gradlew.bat --offline clean test bootJar` | `./gradlew --offline clean test bootJar` |
| Dependency tree | `.\gradlew.bat dependencies --configuration runtimeClasspath` | `./gradlew dependencies --configuration runtimeClasspath` |
| More error detail | `.\gradlew.bat test --stacktrace` | `./gradlew test --stacktrace` |

```text
java -version
java -jar build/libs/project11.jar
java -jar build/libs/project11.jar --server.port=9090
```

`bootJar` packages the app but does not run tests. `test` verifies behavior. `bootRun` stays running until stopped.
Read `build/reports/tests/test/index.html` after test failures.
Do not upgrade versions immediately before going offline.

## Project map

```text
build.gradle                                      libraries, Java version, build tasks
src/main/java/de/tyro/project11/
  Project11Application.java                       entry point and component scan
  config/SecurityConfig.java                      public routes, CSRF, password encoder
  auth/
    DatabaseUserDetailsService.java                loads credentials from JPA for Spring Security
    SignInController.java                         home, sign-in, and welcome views
  registration/
    RegistrationForm.java                         submitted values and constraints
    AppUser.java                                  JPA entity mapped to app_users
    RegistrationController.java                   GET/POST routes, model, redirects
    RegistrationService.java                      validation boundary and hashing
    UserRepository.java                           JpaRepository and derived query methods
    EmailAlreadyRegisteredException.java          expected business error
src/main/resources/
  application.properties                          default configuration
  application-dev.properties                      local template cache setting
  templates/                                      HTML evaluated by Thymeleaf
  static/css/app.css                              CSS served as /css/app.css
src/test/java/de/tyro/project11/
  RegistrationIntegrationTests.java               HTTP, validation, security, database tests
data/project11.mv.db                              runtime data, ignored by Git
```

**Controller:** HTTP input and output. **Service:** application rules and transactions. **Repository:** persistence operations. **Entity:** a mapped database object. **Form/DTO:** input or output data.
Keeping these roles separate makes it easier to change the frontend or database later.
Spring Data JPA generates the repository implementation. Hibernate implements JPA and turns entity operations into SQL.

## Spring concepts and annotations

A **bean** is an object managed by Spring. Spring creates it, supplies its dependencies, and manages its lifecycle.
Constructor injection makes the dependencies visible and lets you use normal constructors in unit tests.

| Annotation | Meaning / use |
| --- | --- |
| `@SpringBootApplication` | Main configuration, auto-configuration, component scan |
| `@Controller` | Web controller whose String results are template names or redirects |
| `@RestController` | Web controller whose results become response bodies, usually JSON |
| `@Service` | Application logic registered as a bean |
| `@Repository` | Storage access registered as a bean; participates in exception translation |
| `@Component` | General-purpose bean discovered by scanning |
| `@Configuration` | Class that declares configuration and beans |
| `@Bean` | Register the method's return value as a bean |
| `@GetMapping`, `@PostMapping` | Match an HTTP method and path |
| `@RequestParam` | Read a query/form parameter |
| `@PathVariable` | Read a value inside a URL path |
| `@RequestBody` | Deserialize a request body, e.g. JSON |
| `@ModelAttribute` | Bind form fields and expose the form in the model |
| `@Valid` | Validate an object and its constraints |
| `@Validated` | Enable method validation on a Spring-managed service |
| `@Transactional` | Run a proxied method within a database transaction |
| `@Entity`, `@Table` | Map a Java class to a database table |
| `@Id`, `@GeneratedValue` | Identify a row and generate its primary key |
| `@Column` | Map a field to a column and describe its constraints |
| `@Profile("dev")` | Activate a bean only for a selected profile |

Validation imports start with `jakarta.validation`, not the older `javax.validation` package.
Put new components below `de.tyro.project11`; classes outside the scanned package are not discovered automatically.
Avoid calling `new RegistrationService(...)` in application code: use the injected bean so method validation works.

## Controllers and HTTP

This app's endpoints:

| Method/path | Result |
| --- | --- |
| `GET /` | Redirect to `/signin` for guests, `/welcome` for signed-in users |
| `GET /signin` | Sign-in form (signed-in users go to `/welcome`) |
| `POST /signin` | Spring Security checks email/password and creates a session |
| `GET /welcome` | Minimal welcome page; authentication required |
| `POST /logout` | Invalidate session and redirect to `/signin?logout`; CSRF required |
| `GET /register` | Empty registration form |
| `POST /register` | Form errors, or save then redirect |
| `GET /register/success` | One-time success view after registration; otherwise redirect |
| `GET /css/app.css` | Local stylesheet |

Example additional page (create its template too):

```java
package de.tyro.project11.example;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GreetingController {
    @GetMapping("/greeting")
    public String greeting(@RequestParam(defaultValue = "friend") String name, Model model) {
        model.addAttribute("name", name);
        return "greeting"; // resources/templates/greeting.html
    }
}
```

Template body: `<h1 th:text="${'Hello, ' + name}">Hello</h1>`.
Also permit `GET /greeting` in `SecurityConfig`'s GET matcher; the current policy denies new routes until added.
Open `/greeting?name=Alex`.

An additional JSON endpoint might return a record:

```java
package de.tyro.project11.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleApiController {
    public record Greeting(String message) {}

    @GetMapping("/api/greeting")
    public Greeting greeting() {
        return new Greeting("Hello from Spring Boot");
    }
}
```

Permit `GET /api/greeting` explicitly before using it. Never return password hashes or an entire internal user object from an API.
`@Controller` returning `"hello"` means a view named `hello`; `@RestController` returning `"hello"` means response text.

| HTTP status | Typical meaning |
| --- | --- |
| 200 | Successful response; this project's form validation errors also render as 200 HTML |
| 201 | A REST endpoint created a resource |
| 302 / 303 | Redirect; follow with a GET for form success |
| 400 | Bad input in many APIs |
| 401 | Authentication required |
| 403 | Access denied or CSRF token missing/invalid |
| 404 | Route/resource missing (security may deny the request first) |
| 409 | Conflict, such as duplicate data in a REST API |
| 500 | Unexpected server error; inspect logs |

## Forms and validation

The controller pattern is:

```java
@PostMapping("/example")
public String save(@Valid @ModelAttribute("form") ExampleForm form,
                   BindingResult errors, RedirectAttributes redirect) {
    if (errors.hasErrors()) {
        return "example-form";
    }
    service.save(form);
    redirect.addFlashAttribute("saved", true);
    return "redirect:/example/success";
}
```

Keep `BindingResult` immediately after the validated form parameter. This lets the controller render field errors.
The above is a pattern: supply your own form, service, template, and security rules.

- `@NotBlank`: non-null String with something other than whitespace.
- `@Size(min = 12, max = 72)`: length limits; combine with `@NotBlank` if required.
- `@Email`: syntax validation; it does not verify ownership or deliverability.
- `@AssertTrue`: a boolean property must be true; this form uses it for password matching and the BCrypt byte limit.
- `errors.rejectValue("email", "duplicate", "Message")`: add an expected application error to a field.
- `@InitBinder` with `setAllowedFields(...)`: restrict which submitted fields Spring can bind.

The form normalizes names and email in setters. Passwords are never trimmed, lowercased, logged, or redisplayed.
The service is also validated so another controller cannot accidentally bypass constraints when calling the Spring bean.
HTML `required` and `minlength` improve the browser experience, but server validation remains necessary.

## Entities, repositories, and transactions

### Entity basics

`AppUser` is the working example. It maps to `app_users`, with a generated ID, name, normalized email, password hash, and creation time.
JPA annotations come from `jakarta.persistence`. Put `@Entity` classes below the main application package.

```java
@Entity
@Table(name = "notes")
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    protected Note() {} // JPA needs a no-argument constructor.

    public Note(String title) { this.title = title; }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void rename(String title) { this.title = title; }
}
```

This is an extension example; Hibernate creates the `notes` table from the entity on startup with `ddl-auto=update`.
Use regular non-final classes for entities. Records work well as DTOs, not as ordinary JPA entities.
With annotations on fields, JPA uses field access; public setters for every field are unnecessary.
Prefer methods such as `rename(...)` that represent intentional changes.
Keep generated ID setters, password hash changes, and relationship mutation out of general form binding.

### Repository basics

```java
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Imports: `org.springframework.data.jpa.repository.JpaRepository` and `java.util.Optional`.
Spring supplies the implementation, so no implementation class or explicit `@Repository` is needed on this interface.
Derived queries use **Java property names** (`email`, `displayName`), not SQL column names (`display_name`).

| Repository call | Result / reminder |
| --- | --- |
| `save(entity)` | Persist/merge; use the returned entity, especially for detached objects |
| `saveAndFlush(entity)` | Save and send pending SQL now; does not commit the service transaction |
| `findById(id)` | `Optional<AppUser>`; handle missing rows |
| `findByEmail(email)` | Derived query; normalize email before calling |
| `existsByEmail(email)` | Existence check; cannot replace the database UNIQUE constraint |
| `findAll(PageRequest.of(0, 20, Sort.by("id")))` | A page of users; page numbers start at zero |
| `count()` | Row count |
| `deleteById(id)` | Delete; ensure the caller is authorized and relationships permit it |

For a custom JPQL query, add a method to the repository:

```java
@Query("select u from AppUser u where u.createdAt >= :since order by u.id")
List<AppUser> findCreatedSince(@Param("since") OffsetDateTime since);
```

Imports: `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, plus `List` and `OffsetDateTime`.
JPQL uses entity names and properties. A native query (`nativeQuery = true`) uses table/column names and database-specific SQL.
Bind values as parameters in both cases. Do not concatenate user input into query strings.

### Transaction boundaries and dirty checking

The registration service uses `@Transactional`. Its `saveAndFlush` raises unique-email errors before the method finishes, allowing the named email constraint to become a friendly field error.
Other database errors are rethrown. Do not treat every integrity error as a duplicate email.
The unique constraint remains necessary for simultaneous requests.

For a future NoteService, after adding the Note entity/repository/table:

```java
@Transactional
public void renameNote(Long id, String title) {
    Note note = notes.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Note not found"));
    note.rename(title);
    // Hibernate detects the changed managed entity and writes it at flush/commit.
}
```

Import `org.springframework.transaction.annotation.Transactional`.
Call transaction methods through an injected Spring bean; calling your own method with `this.method()` does not go through its proxy.
A runtime exception rolls back by default; checked exceptions may need `rollbackFor`.
Do not swallow an exception if all writes must roll back. A failed flush can make the transaction unusable; let it end and roll back.
Use `@Transactional(readOnly = true)` for service methods that only read.

JPA terms: **transient** = new/unpersisted; **managed** = tracked in the current persistence context; **detached** = no longer tracked; **removed** = scheduled for deletion.
Changing a detached object does not automatically update the database.

### Entities and a Thymeleaf frontend

Keep `RegistrationForm` separate from `AppUser`. The browser submits a raw password; the stored entity holds only a hash.
Never bind a form directly to an entity with writable IDs, roles, ownership, or hash fields.
For read views, map entities to a small DTO in a service, then pass that DTO into the model:

```java
public record UserView(Long id, String displayName, String email) {}

// Inside a service with an injected UserRepository:
@Transactional(readOnly = true)
public UserView getUserView(Long id) {
    AppUser user = users.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return new UserView(user.getId(), user.getDisplayName(), user.getEmail());
}
```

A controller can call `model.addAttribute("user", service.getUserView(id))`; Thymeleaf reads `${user.displayName}`.
Sign-in is implemented, but add ownership/role authorization before exposing account detail pages or APIs. Being signed in must not allow someone to request another user's data by changing an ID. This starter deliberately has no public user list.
`AppUser.passwordHash` has `@JsonIgnore` as an extra guard, but explicit DTOs are still the preferred output boundary.

### Relationships and lazy loading

For a future Note owned by a user:

```java
// Fields inside Note; Hibernate generates the owner_id column and foreign key.
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "owner_id", nullable = false)
private AppUser owner;
```

`@OneToMany` represents a collection on the other side. If you add one to AppUser, `mappedBy = "owner"` names the Java field on Note; Note owns the foreign key.
Start with a one-direction relationship unless you need the collection.
Avoid `CascadeType.ALL` and `orphanRemoval=true` until their deletion effects are intentional; do not cascade removal from a note to its shared owner.
Use `@Enumerated(EnumType.STRING)` for enum values instead of fragile ordinal numbers. `@Version` enables optimistic locking when concurrent edits matter; add the corresponding column.

`spring.jpa.open-in-view=false` means templates do not load lazy relations after the service transaction ends.
Fetch needed relations and map them to DTOs within a service transaction. Use a JPQL `join fetch` or a repository `@EntityGraph(attributePaths = "owner")` for a suitable query.
Do not change every relationship to EAGER to hide `LazyInitializationException`.
Loading each row's relation separately can cause N+1 queries; inspect query counts when adding list pages. Collection fetch joins and pagination need special care.

### Schema ownership and optional plain SQL

`spring.jpa.hibernate.ddl-auto=update` lets Hibernate create missing tables and update the schema from entity mappings on startup.
`spring.sql.init.mode=never` disables SQL initialization scripts; there is no separate `schema.sql` to maintain.
For a new entity, define its columns, relationships, constraints, and indexes with annotations, then restart.
`AppUser` uses `@ColumnDefault` for database defaults and `@Table(check = @CheckConstraint(...))` for nonnegative tallies.
The administrator initializer promotes the oldest existing user only if no administrator exists; registration makes the first new account an administrator.
Automatic updates do not infer column renames or data transformations, and may not update existing constraints. Back up persistent data before schema changes and use explicit migrations for those cases.
Avoid `create` or `create-drop` with data you want to retain. A migration tool is the natural next step as schemas grow.

You can still inject `JdbcClient` for a specific SQL query; the tests use it to inspect real rows independently of JPA:

```java
long count = jdbc.sql("SELECT COUNT(*) FROM app_users").query(Long.class).single();
```

For writes, prefer one persistence approach within a transaction. Direct SQL can bypass JPA's managed entity state; flush/clear deliberately when mixing them.
See the [SQL cheatsheet](sql-cheatsheet.md) for SQL syntax and backups, and [Boot's JPA documentation](https://docs.spring.io/spring-boot/reference/data/sql.html) for configuration context.

## Configuration and profiles

```properties
# src/main/resources/application.properties
server.port=${PORT:8080}
spring.datasource.url=${DB_URL:jdbc:h2:file:./data/project11;DB_CLOSE_ON_EXIT=FALSE}
spring.datasource.username=${DB_USERNAME:sa}
spring.datasource.password=${DB_PASSWORD:}
```

`${VARIABLE:default}` uses an environment variable when present, otherwise its default.
For the settings used here: command-line arguments override environment variables, which override file defaults.
`application-dev.properties` adds/overrides settings when the `dev` profile is active.

```powershell
$env:PORT = '9090'
.\gradlew.bat bootRun
Remove-Item Env:PORT
```

```bash
PORT=9090 ./gradlew bootRun
```

Spring Boot does not automatically load a plain `.env` file. Use actual environment variables or external configuration.
Keep secrets out of committed files and shell command history. A relative database URL uses the process working directory.

## Security in this project

`SecurityConfig` permits registration, sign-in, and stylesheet GETs. `/welcome` requires authentication; unconfigured routes are denied.
CSRF remains enabled. Thymeleaf adds a hidden token to the `th:action` POST form and the session cookie accompanies submission.
A POST from curl without the token and matching session receives 403. Refresh the form after restarting the app because sessions are in memory.
See [Spring Security's CSRF documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) for the underlying integration.

`BCryptPasswordEncoder.encode(rawPassword)` creates a salted, one-way hash; `matches(rawPassword, storedHash)` verifies one.
Do not compare two calls to `encode`: random salts make them different. The 72-byte UTF-8 limit is validated before encoding.
Password length counts and byte length are not the same for non-ASCII characters.
See [Spring Security password storage](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html).

`DatabaseUserDetailsService` loads a registered account using `UserRepository.findByEmail`, with the same trimmed/lowercase email normalization as registration.
It returns Spring Security's `UserDetails` containing the stored hash and `ROLE_USER`. The JPA entity is not put into the session.
The custom service makes Spring Boot's default generated user unnecessary; auto-configuration backs off automatically.

`formLogin` in `SecurityConfig` sets the page and POST URL to `/signin`, the username parameter to `email`, and the success destination to `/welcome`.
Spring Security handles the POST and uses the configured BCrypt encoder to verify the password. Do not add a competing POST controller or manually set an authenticated flag in the session.
Failure redirects to `/signin?error`, which always shows the same generic error for unknown email and wrong password.
Successful authentication rotates the session ID; later requests use the session cookie. No JWT or browser local storage is needed.
Registration saves data and links to sign-in; it does not automatically sign in the new user.

`POST /logout` with a CSRF token invalidates the session, clears the security context and session cookie, then redirects to `/signin?logout`.
The welcome page includes a normal Thymeleaf POST form for it. Do not replace it with a state-changing GET link.
Sessions are in memory: restarting the app signs everyone out, while registered accounts remain in H2.
See [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html) and [logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html) for the framework flow.

## Testing and troubleshooting

The integration tests load the full application with MockMvc and a separate in-memory H2 database.
They verify saved hashes, validation, duplicate requests, UTF-8 limits, escaping, SQL binding, CSRF, and the registration success redirect.

Useful Boot 4 imports:

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
```

Use `.with(csrf())` on valid form POSTs. Also test a POST without it.
Boot 4 separates some test modules; this build includes `spring-boot-starter-webmvc-test`.

| Symptom | Check |
| --- | --- |
| Port already in use | Stop your previous app or choose `--server.port=9090` |
| No Java 25 toolchain | Install JDK 25 and check IDE Gradle JVM / `JAVA_HOME` |
| `UnsupportedClassVersionError` | Runtime is older than the Java version used to compile |
| Offline dependency resolution fails | Run the same build online first, retain the Gradle cache |
| Template not found | Template name and file under `resources/templates` must match |
| Old HTML/CSS still shown | Restart/rebuild resources, dev profile, browser hard refresh |
| Form POST gives 403 | CSRF input, session cookie, expired form, URL and method rules |
| New endpoint gives 403 | Add a deliberately scoped security matcher |
| Database already in use | Stop the other app/SQL shell using the embedded file |
| Empty database after moving app | Check working directory and `DB_URL` |
| Schema-validation error / column missing | Add/migrate the entity's table/column; CREATE IF NOT EXISTS does not change existing tables |
| LazyInitializationException | Load relations and map DTOs within a service transaction |
| Query derivation fails at startup | Repository method references a Java property that does not exist |
| Dependency injection fails | Component annotation, package location, constructor dependencies |

Additional official references: [Boot build starters](https://docs.spring.io/spring-boot/reference/using/build-systems.html), [JDBC configuration](https://docs.spring.io/spring-boot/reference/data/sql.html), [database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
