# Frontend development cheatsheet

This project serves complete pages with **Spring MVC + Thymeleaf**, styled by a local CSS file.
The registration form works without JavaScript. There is no frontend package manager or bundler.

## How the browser gets a page

```text
GET /register
  -> controller adds registrationForm to Model
  -> Thymeleaf evaluates templates/register.html
  -> browser receives ordinary HTML
  -> browser requests /css/app.css

POST /register (form fields + hidden CSRF token + session cookie)
  -> validation
  -> errors: render form again
  -> success: save, redirect, render success page
```

Thymeleaf runs on the server. The browser never runs `th:text` or `th:field`.
Open `http://localhost:8080/register`, not the template file from disk.

## Files and URLs

| File | How it is used |
| --- | --- |
| `src/main/resources/templates/register.html` | Returned by controller as `"register"` |
| `src/main/resources/templates/registration-success.html` | Success view |
| `src/main/resources/templates/signin.html` | Email/password sign-in form |
| `src/main/resources/templates/welcome.html` | Protected page, ready for your content |
| `src/main/resources/templates/error.html` | Friendly error view |
| `src/main/resources/static/css/app.css` | Served at `/css/app.css` |
| `src/main/resources/static/js/example.js` | If added, served at `/js/example.js` |

`static` and `templates` are not included in the public URL.
New static paths such as `/js/**` must be permitted for GET in `SecurityConfig`.
Use `th:href="@{/css/app.css}"` so links also work if you later configure an application context path.

To see a change reliably: stop `bootRun`, restart it, then reload the browser.
The `dev` profile disables template caching, but resources still need to be copied to the runtime classpath by Gradle/your IDE.
For a packaged JAR, rebuild and restart after edits.

## Connecting the frontend to entities

The database model is `AppUser`, a JPA entity. `UserRepository` loads/saves it, and a service owns the transaction.
The registration page binds to `RegistrationForm`, not directly to the entity: visitors must never set their own database ID or password hash.
For a future detail/list page, load entities in a service, map the needed fields into a small view DTO, and add that DTO to the controller's Model.
Thymeleaf accesses its properties normally, for example `<span th:text="${user.displayName}">Name</span>`.
This keeps the template simple and avoids accessing lazy relationships after the transaction has ended.
The [JPA section](spring-boot-cheatsheet.md#entities-repositories-and-transactions) includes working patterns for entity mappings, DTOs, repositories, and relationships.
Account detail pages still need ownership/role authorization before exposing anyone's data; sign-in alone is not enough to permit access to another user's account.

## HTML essentials

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Page title · Project 11</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
    <header><!-- Brand and navigation --></header>
    <main>
        <h1>One descriptive page heading</h1>
        <p>Useful content.</p>
    </main>
    <footer><!-- Secondary information --></footer>
</body>
</html>
```

| Element / attribute | Use |
| --- | --- |
| `<a href="...">` | Navigate to another page |
| `<button type="submit">` | Submit a form |
| `<button type="button">` | Perform a JavaScript action; avoid accidental submission |
| `<label for="email">` | Visible label connected to input `id="email"` |
| Input `name` | Key sent to the backend |
| Input `id` | Unique page identifier used by labels, CSS, JS |
| Input `value` | Current field value |
| `required`, `minlength`, `maxlength` | Browser input checks |
| `autocomplete="email"` | Help browsers/password managers fill appropriate values |
| `autocomplete="new-password"` | Mark password-creation fields |
| `<fieldset>` and `<legend>` | Group related fields such as radio buttons |

Use a password input, never a text input, for ordinary password entry. Do not submit passwords with GET: URLs can enter browser history and logs.
Disabled inputs are not submitted. Readonly inputs are submitted. Unchecked checkboxes are normally absent from ordinary form data.
Placeholders show examples; they do not replace labels.

## Thymeleaf expressions

| Syntax | Meaning | Example |
| --- | --- | --- |
| `${...}` | Model variable | `${name}` |
| `*{...}` | Property on `th:object` | `*{email}` |
| `@{...}` | Application URL | `@{/register}` |
| `#{...}` | Message from a messages bundle | `#{registration.title}` |
| `~{...}` | Template fragment | `~{fragments/header :: header}` |
| Literal substitution | Text interpolation | See the greeting example below |

```html
<p th:text="${name}">Fallback text for a static preview</p>
<p th:text="|Hello, ${name}|">Hello</p>
<a th:href="@{/greeting(name=${name})}">Greeting</a>
<p th:if="${saved}">Saved successfully.</p>
<p th:unless="${saved}">Not saved yet.</p>
<li th:each="item : ${items}" th:text="${item.title}">Example item</li>
<div th:classappend="${active} ? 'is-active' : ''">Content</div>
```

`th:text` escapes user-supplied HTML. Avoid `th:utext` with user content because it renders markup directly.
`th:if` removes an element when false; it does not merely hide it with CSS.
The controller must supply the model values used by the template.

### Reusable fragments

Create `templates/fragments/header.html`:

```html
<header xmlns:th="http://www.thymeleaf.org" th:fragment="header">
    <a th:href="@{/}">Project 11</a>
</header>
```

Use it from another template:

```html
<div th:replace="~{fragments/header :: header}"></div>
```

`th:replace` replaces the host element. `th:insert` puts the fragment inside it.

## Forms and validation

The model attribute name, `th:object`, and controller binding must agree:

```java
model.addAttribute("registrationForm", new RegistrationForm());
```

```html
<form th:action="@{/register}" method="post" th:object="${registrationForm}">
    <label for="email">Email address</label>
    <input id="email" type="email" th:field="*{email}" required
           autocomplete="email" aria-describedby="email-error"
           th:attr="aria-invalid=${#fields.hasErrors('email')}">
    <p id="email-error" th:errors="*{email}"></p>
    <!-- Include the other required fields; see register.html for the complete form. -->
    <button type="submit">Create account</button>
</form>
```

`th:field` produces binding attributes such as name and value. It also restores non-sensitive rejected input.
`th:errors` prints validation messages for that property. `#fields.hasAnyErrors()` can show a summary.
Spring Security's Thymeleaf integration inserts the hidden CSRF field into this POST form.
Do not add a second token manually when `th:action` already provides one.

For passwords, this project intentionally uses plain names without value binding:

```html
<input id="password" name="password" type="password"
       autocomplete="new-password" required minlength="12" maxlength="72">
```

The name still binds to `RegistrationForm.password`. The submitted password is never placed back into returned HTML.
After errors, users must enter both password fields again. Keep errors next to the relevant field, preserve name/email, and explain the next action.
The additional boolean validation properties `passwordsMatching` and `passwordWithinByteLimit` have their own `th:errors` elements.

On success, use redirect-after-POST. A normal browser form follows the redirect automatically.
For invalid input, this project returns status 200 with the form and field errors; it is not a JSON API.

## Sign-in and sign-out

The sign-in page uses a normal POST form:

```html
<form th:action="@{/signin}" method="post">
    <label for="email">Email address</label>
    <input id="email" name="email" type="email" autocomplete="username" required>
    <label for="password">Password</label>
    <input id="password" name="password" type="password" autocomplete="current-password" required>
    <button type="submit">Sign in</button>
</form>
```

`email` matches `.usernameParameter("email")` in `SecurityConfig`; the password parameter is `password`.
Spring Security handles the POST. The controller only renders the GET page.
Successful sign-in redirects to `/welcome`. Failed credentials redirect to `/signin?error`; the page shows a generic message with `th:if="${param.error != null}"`.
Do not display exception details or put submitted passwords back into the HTML.

Sign-out is also a form, so Thymeleaf includes the CSRF token:

```html
<form th:action="@{/logout}" method="post">
    <button type="submit">Sign out</button>
</form>
```

The server invalidates the session and redirects to `/signin?logout`.
The welcome template intentionally contains only a heading and the shared header/sign-out action. Put future page content inside its `<main>`.
Hiding a button is not authorization; enforce authentication and ownership rules on the server too.

## CSS you will use often

```css
:root {
    --ink: #203c32;
    --space: 16px;
    font-family: "Segoe UI", system-ui, sans-serif;
    color: var(--ink);
}
* { box-sizing: border-box; }
body { margin: 0; }
.card { max-width: 480px; padding: 24px; border: 1px solid #ddd; border-radius: 16px; }
.row { display: flex; align-items: center; justify-content: space-between; gap: var(--space); }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }
input { width: 100%; font: inherit; }
button:focus-visible, a:focus-visible { outline: 3px solid #648169; outline-offset: 3px; }
@media (max-width: 760px) {
    .grid { grid-template-columns: minmax(0, 1fr); }
}
```

| Concept | Reminder |
| --- | --- |
| `margin` | Space outside an element |
| `padding` | Space inside an element |
| `gap` | Space between flex/grid items |
| `box-sizing: border-box` | Width includes border and padding |
| Flexbox | Arrange items in one dimension: row or column |
| Grid | Arrange rows/columns; useful for whole page layouts |
| `max-width` + `margin: auto` | Limit content width and center it |
| `minmax(0, 1fr)` | Allow a grid column to shrink without content forcing overflow |
| `rem` | Size relative to root font; good for scalable spacing/type |
| `%` | Relative to the containing sizing context |
| `min-height: 100svh` | At least the small viewport height, including mobile browser UI behavior |
| `display: none` | Remove from layout and accessibility tree |
| `position: absolute` | Position relative to nearest positioned ancestor |

Prefer classes over deeply nested selectors. Later rules win when specificity is equal.
An ID selector is more specific than a class. Inline styles usually beat stylesheet declarations.
Use the browser Styles/Computed panels to see which rule wins instead of repeatedly adding `!important`.

This stylesheet uses a 760px single-column breakpoint. Test both sides and narrow screens around 320–390px.
Check long names, long errors, keyboard focus, and zoomed text; avoid fixed heights for content cards.
System fonts and local CSS keep the page usable offline.

## Accessibility quick reference

- Provide labels, heading hierarchy, and a single main content region.
- Every action must work with a keyboard; do not remove focus outlines.
- Errors should be text, not just a red border. Associate them using `aria-describedby` and `aria-invalid`.
- Use `role="alert"` for an important error summary; avoid announcing unrelated content.
- Give decorative artwork `aria-hidden="true"` and informative images meaningful `alt` text.
- Let browsers/password managers use autocomplete and paste.
- Use links for navigation and buttons for actions.

## Optional JavaScript and JSON

You do not need JS for the existing form. If adding a small enhancement, place the script under `static/js/`, permit its GET path in security, and load it with `defer`:

```html
<script th:src="@{/js/example.js}" defer></script>
```

Useful DOM operations:

```javascript
const button = document.querySelector('#my-button');
button.addEventListener('click', () => {
    document.querySelector('#message').textContent = 'Hello';
});
```

`textContent` writes text. Do not use `innerHTML` for untrusted values.

### Read JSON with fetch

First implement and permit `/api/greeting` from the Spring Boot cheatsheet; it is an example endpoint, not included in the running app.
Use a same-origin URL so the browser calls the server that delivered the page, including when opened from another computer on the Pi's LAN.

```html
<button id="load-greeting" type="button" th:attr="data-url=@{/api/greeting}">Load greeting</button>
<p id="greeting" role="status"></p>
```

```javascript
const button = document.querySelector('#load-greeting');
const output = document.querySelector('#greeting');

button.addEventListener('click', async () => {
    button.disabled = true;
    output.textContent = 'Loading…';
    try {
        const response = await fetch(button.dataset.url, {
            headers: { Accept: 'application/json' },
            signal: AbortSignal.timeout(10000)
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        output.textContent = data.message;
    } catch (error) {
        output.textContent = 'Could not load the greeting. Please try again.';
        console.error(error); // Never log passwords or tokens.
    } finally {
        button.disabled = false;
    }
});
```

`fetch` normally rejects on network errors, not on HTTP 400/500; check `response.ok`.
Read the body as JSON only when the endpoint returns JSON. See [MDN's fetch guide](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch).

### Write JSON with CSRF

For a **future** POST JSON endpoint, expose the token through Thymeleaf in the page head:

```html
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
```

Inside an async function, after defining the new endpoint and its security rule:

```javascript
const token = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;
const response = await fetch('/api/example', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        [header]: token
    },
    body: JSON.stringify({ title: 'My example' })
});
if (!response.ok) throw new Error(`HTTP ${response.status}`);
// A 204 No Content response has no JSON body to parse.
```

This is not the protocol for `POST /register`: that endpoint accepts an ordinary URL-encoded form and returns HTML/redirects.
If enhancing the existing form, keep its normal submission as the fallback. Fetch follows redirects in the background; it does not navigate the page for you.
With `FormData`, do not manually set Content-Type; the browser adds the multipart boundary. For this simple all-text form, `URLSearchParams(new FormData(form))` encodes it as standard form data and includes the hidden CSRF field.

Do not hardcode `http://localhost:8080` in frontend JS for the Pi: localhost would refer to the visitor's computer.
Same-origin pages and endpoints need no CORS setup. A separate frontend host or port introduces CORS and credential configuration, which this project avoids.
Do not use `mode: 'no-cors'` to fix CORS; it makes responses opaque rather than solving access rules.

## Browser debugging

1. Open developer tools (usually F12).
2. **Network:** check request URL/method/status, content type, form fields, redirect, and CSS response.
3. **Elements:** check generated HTML, not only template source.
4. **Console:** look for JS or asset errors.
5. **Application/Storage:** check the session cookie if CSRF fails; never share tokens or passwords in screenshots.
6. Use responsive mode and Tab/Shift+Tab to check the narrow layout and focus order.

| Symptom | Likely cause |
| --- | --- |
| Raw `th:*` in a local file does nothing | Template opened from disk instead of through Spring |
| CSS missing | Wrong URL, resource not copied, or security matcher missing |
| Passwords empty after validation | Intentional; re-enter both |
| JSON parse error beginning with `<` | Endpoint returned HTML (form/login/error), not JSON |
| Form does nothing | Browser validation blocked submission; inspect invalid fields |
| POST 403 after restart | Old CSRF/session; open a fresh form |
| Layout overflows | Fixed widths, long text, grid minimum sizing; inspect at a narrow viewport |

Optional reference: [Thymeleaf and Spring forms](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html).
