# Add user-owned data and pages

This guide shows one complete way to add records that belong to the signed-in user and pages that display those records. The worked example is a `Note`, but the same structure works for projects, bookmarks, workouts, orders, or similar data.

The central rule is:

> Get the owner from Spring Security on the server. Never accept an owner ID or email from a form, query parameter, JSON body, or URL.

Signing in only proves who made the request. It does not make a request such as `/notes/42` safe by itself. Every query for private data must also restrict the result to the current owner.

## Request flow

```text
authenticated session
  -> Principal.getName()                         current normalized email
  -> UserRepository.findByEmail(...)             current AppUser
  -> NoteRepository query including owner.id     only that user's rows
  -> service maps entities to view DTOs
  -> controller adds DTOs to the model
  -> Thymeleaf renders escaped values
```

For a create request, the service attaches the resolved `AppUser` to the new `Note`. There is deliberately no `ownerId` field in `NoteForm`.

## Files in the example

Add these below the existing application package so Spring discovers them:

```text
src/main/java/de/tyro/project11/notes/
  Note.java
  NoteForm.java
  NoteView.java
  NoteRepository.java
  NoteNotFoundException.java
  NoteService.java
  NoteController.java
src/main/resources/templates/notes/
  list.html
  new.html
  detail.html
src/test/java/de/tyro/project11/
  NoteOwnershipIntegrationTests.java
```

Also update `SecurityConfig` and a navigation template such as `welcome.html`.

## 1. Let Hibernate create the database table

This project uses `spring.jpa.hibernate.ddl-auto=update`. Add the entity below and restart; Hibernate generates the `notes` table, its owner foreign key, and its index from the annotations.

`owner_id NOT NULL` means every note has an owner. The foreign key prevents references to nonexistent users. The index supports the per-user list query.

No `schema.sql` is needed. Back up persistent data before changing existing mappings. Automatic updates do not infer renames or data transformations and may not update existing constraints; use explicit migrations for those cases.

## 2. Map the relationship in an entity

Create `notes/Note.java`:

```java
package de.tyro.project11.notes;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "notes", indexes = @Index(name = "idx_notes_owner_created", columnList = "owner_id, created_at DESC"))
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notes_owner"))
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Note() {
        // Required by JPA.
    }

    public Note(AppUser owner, String title, String body) {
        this.owner = owner;
        this.title = title;
        this.body = body;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

The note points to its owner with `@ManyToOne`; `AppUser` does not need a notes collection. Starting with this one-direction relationship keeps loading and deletion behavior simple. Do not use `CascadeType.REMOVE` from a note to its owner.

## 3. Separate submitted data from displayed data

Create `notes/NoteForm.java` for allowed browser input:

```java
package de.tyro.project11.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteForm {
    @NotBlank(message = "Enter a title.")
    @Size(max = 120, message = "Use at most 120 characters.")
    private String title = "";

    @NotBlank(message = "Enter some text.")
    @Size(max = 4000, message = "Use at most 4000 characters.")
    private String body = "";

    public String getTitle() { return title; }
    public String getBody() { return body; }

    public void setTitle(String title) {
        this.title = title == null ? "" : title.strip();
    }

    public void setBody(String body) {
        this.body = body == null ? "" : body.strip();
    }
}
```

Create `notes/NoteView.java` for template output:

```java
package de.tyro.project11.notes;

import java.time.OffsetDateTime;

public record NoteView(Long id, String title, String body, OffsetDateTime createdAt) {}
```

Neither type exposes an owner field. In particular, do not bind a submitted form directly to `Note`, because doing so can expose IDs and relationships to mass assignment.

## 4. Make ownership part of every repository query

Create `notes/NoteRepository.java`:

```java
package de.tyro.project11.notes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Note> findByIdAndOwnerId(Long id, Long ownerId);
}
```

Spring Data reads `ownerId` as the nested Java property `owner.id`. The second method is intentionally not just `findById`: a caller who changes `/notes/7` to `/notes/8` must not receive note 8 unless it has the same owner.

Use the same pattern for updates and deletes: first load with `findByIdAndOwnerId`, then change or delete the returned entity. A separate `existsById` check is not authorization and can create a time-of-check/time-of-use gap.

## 5. Resolve the signed-in user and map DTOs in a service

Create `notes/NoteNotFoundException.java`:

```java
package de.tyro.project11.notes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoteNotFoundException extends RuntimeException {}
```

Returning the same 404 for a missing note and another user's note avoids revealing which IDs exist.

Create `notes/NoteService.java`:

```java
package de.tyro.project11.notes;

import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@Service
@Validated
public class NoteService {
    private final NoteRepository notes;
    private final UserRepository users;

    public NoteService(NoteRepository notes, UserRepository users) {
        this.notes = notes;
        this.users = users;
    }

    @Transactional
    public Long create(String signedInEmail, @Valid NoteForm form) {
        AppUser owner = requireUser(signedInEmail);
        return notes.save(new Note(owner, form.getTitle(), form.getBody())).getId();
    }

    @Transactional(readOnly = true)
    public List<NoteView> listFor(String signedInEmail) {
        AppUser owner = requireUser(signedInEmail);
        return notes.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteView getFor(String signedInEmail, Long noteId) {
        AppUser owner = requireUser(signedInEmail);
        Note note = notes.findByIdAndOwnerId(noteId, owner.getId())
                .orElseThrow(NoteNotFoundException::new);
        return toView(note);
    }

    private AppUser requireUser(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        return users.findByEmail(normalized)
                .orElseThrow(() -> new IllegalStateException("Signed-in account no longer exists."));
    }

    private NoteView toView(Note note) {
        return new NoteView(note.getId(), note.getTitle(), note.getBody(), note.getCreatedAt());
    }
}
```

In this project, `Principal.getName()` is the normalized email because `DatabaseUserDetailsService` uses the stored email as the Spring Security username. The database relationship still uses the stable user ID. If email changes become a feature, keep resolving the current account in one place so the authentication identifier can be changed safely later.

Mapping to `NoteView` inside the transaction also fits `spring.jpa.open-in-view=false`: templates receive all the data they need and do not trigger lazy database access.

## 6. Add authenticated list, create, and detail routes

Create `notes/NoteController.java`:

```java
package de.tyro.project11.notes;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/notes")
public class NoteController {
    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @InitBinder("noteForm")
    void configureFormBinding(WebDataBinder binder) {
        binder.setAllowedFields("title", "body");
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("notes", notes.listFor(principal.getName()));
        return "notes/list";
    }

    @GetMapping("/new")
    public String newNote(Model model) {
        model.addAttribute("noteForm", new NoteForm());
        return "notes/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("noteForm") NoteForm form,
                         BindingResult errors,
                         Principal principal,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "notes/new";
        }
        Long id = notes.create(principal.getName(), form);
        redirect.addFlashAttribute("saved", true);
        return "redirect:/notes/" + id;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("note", notes.getFor(principal.getName(), id));
        return "notes/detail";
    }
}
```

`Principal` comes from the authenticated session; it is not controlled by a hidden input. Keep `BindingResult` immediately after the validated form. Redirecting after a successful POST prevents refresh from creating a duplicate note.

Permit these routes in `SecurityConfig` before `.anyRequest().denyAll()`:

```java
.requestMatchers("/notes", "/notes/**").authenticated()
```

Do not mark them `permitAll()`. This matcher checks authentication for every HTTP method under the path. The owner-restricted service/repository queries are still required: URL authentication alone does not enforce row ownership.

## 7. Create the Thymeleaf pages

`templates/notes/list.html` needs an empty state and owner-scoped list:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>My notes · Project 11</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<main>
    <h1>My notes</h1>
    <p><a th:href="@{/notes/new}">Add a note</a></p>
    <p th:if="${#lists.isEmpty(notes)}">You have no notes yet.</p>
    <ul th:unless="${#lists.isEmpty(notes)}">
        <li th:each="note : ${notes}">
            <a th:href="@{/notes/{id}(id=${note.id})}" th:text="${note.title}">Example note</a>
        </li>
    </ul>
</main>
</body>
</html>
```

`templates/notes/new.html` binds only title and body. Because it uses `th:action` on a POST form, Thymeleaf adds Spring Security's CSRF token:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>New note · Project 11</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<main>
    <p><a th:href="@{/notes}">Back to notes</a></p>
    <h1>Add a note</h1>
    <form th:action="@{/notes}" th:object="${noteForm}" method="post">
        <div role="alert" th:if="${#fields.hasAnyErrors()}">Check the fields below.</div>

        <label for="title">Title</label>
        <input id="title" type="text" th:field="*{title}" required maxlength="120"
               aria-describedby="title-error"
               th:attr="aria-invalid=${#fields.hasErrors('title')}">
        <p id="title-error" th:errors="*{title}"></p>

        <label for="body">Text</label>
        <textarea id="body" th:field="*{body}" required maxlength="4000"
                  aria-describedby="body-error"
                  th:attr="aria-invalid=${#fields.hasErrors('body')}"></textarea>
        <p id="body-error" th:errors="*{body}"></p>

        <button type="submit">Save note</button>
    </form>
</main>
</body>
</html>
```

`templates/notes/detail.html` displays the DTO:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title th:text="|${note.title} · Project 11|">Note · Project 11</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
<main>
    <p><a th:href="@{/notes}">Back to notes</a></p>
    <p role="status" th:if="${saved}">Note saved.</p>
    <article>
        <h1 th:text="${note.title}">Example title</h1>
        <p th:text="${note.body}">Example text</p>
    </article>
</main>
</body>
</html>
```

`th:text` escapes user content. Do not replace it with `th:utext`. If line breaks in note bodies should remain visible, use CSS such as `white-space: pre-wrap` rather than rendering the value as HTML.

Link the feature from the protected welcome page:

```html
<a th:href="@{/notes}">My notes</a>
```

The snippets are intentionally minimal; copy the existing `site-header`, sign-out form, layout classes, and accessibility patterns to make the pages match the application.

## Showing data related to an owned record

Suppose a checklist item belongs to a note. A user owns the item indirectly through the note:

```text
AppUser <- Note <- ChecklistItem
```

Do not load a submitted `noteId` using `findById(noteId)` and then attach the item. First prove that the parent note belongs to the current user:

```java
AppUser owner = requireUser(signedInEmail);
Note note = notes.findByIdAndOwnerId(noteId, owner.getId())
        .orElseThrow(NoteNotFoundException::new);
items.save(new ChecklistItem(note, form.getText()));
```

Scope child queries through the same ownership path:

```java
List<ChecklistItem> findAllByNoteIdAndNoteOwnerIdOrderById(Long noteId, Long ownerId);
Optional<ChecklistItem> findByIdAndNoteOwnerId(Long itemId, Long ownerId);
```

For a note detail page that shows its checklist items, load the owned note and owned child rows within one read-only service transaction, then return one page DTO:

```java
public record ChecklistItemView(Long id, String text, boolean done) {}
public record NoteDetails(NoteView note, List<ChecklistItemView> items) {}
```

The controller adds that `NoteDetails` to the model. The template iterates over `details.items`. This avoids lazy-loading in the template and keeps authorization in the service rather than in presentation logic.

## Test the ownership boundary

At minimum, integration tests should prove all of these behaviors:

- A guest is redirected to sign-in for every private page and cannot POST.
- A signed-in user can create a record, and its `owner_id` is that user's ID.
- A submitted `ownerId` parameter cannot choose or replace the owner.
- User A's list does not contain user B's data.
- User A gets 404 when requesting, updating, or deleting user B's ID.
- Invalid input writes nothing; valid POSTs require CSRF.
- User text is escaped when a form is redisplayed and when a detail page renders.

With MockMvc, authenticate as an email that already exists in the test database:

```java
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

mvc.perform(post("/notes")
        .with(user("alex@example.com").roles("USER"))
        .with(csrf())
        .param("title", "Private")
        .param("body", "Only Alex should see this")
        .param("ownerId", blairId.toString())) // must be ignored
    .andExpect(status().is3xxRedirection());

mvc.perform(get("/notes/" + alexNoteId)
        .with(user("blair@example.com").roles("USER")))
    .andExpect(status().isNotFound());
```

Create both `alex@example.com` and `blair@example.com` before these requests. Assert `owner_id` independently with `JdbcClient` or load the note through an owner-scoped repository query. When clearing test data, delete child tables such as `notes` before `app_users` because the foreign key is enforced.

## Ownership checklist

Before considering a user-owned feature complete, check that:

- The database has a non-null foreign key to `app_users` and useful ownership indexes.
- Forms/JSON DTOs contain editable values only—never owner, ID, role, or other protected fields.
- The current account is derived from the authenticated principal on the server.
- List, detail, update, and delete queries all include the current owner.
- Missing and not-owned records produce the same response.
- Entities are mapped to small page/API DTOs within the service transaction.
- POST/PUT/PATCH/DELETE actions retain CSRF protection for this session-based application.
- Routes are explicitly authenticated in `SecurityConfig` before its deny-all fallback.
- Tests use two users and try the same IDs from both sessions.

