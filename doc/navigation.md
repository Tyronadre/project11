# Shared site navigation

All screen pages use `templates/fragments/navigation.html :: header`.
Add or rename menu links there once. Include it on a new page with:

```html
<header th:replace="~{fragments/navigation :: header}"></header>
```

`SiteNavigation` supplies login state and the active section directly to the fragment,
including exception-handler views. Its route mapping handles nested pages (event costs highlight Costs,
other event pages highlight Calendar, administrative reviews highlight
Applications). New sections require a route mapping there.

`static/css/navigation.css` owns the header design, responsive wrapping,
keyboard focus and active styles. Both app.css and amt.css import it.
Styles are scoped to `.app-header` so the application portal has the same
global menu without changing its local forms and sidebar.

Anonymous visitors see sign-in and registration links. Signed-in visitors see
all main sections, account settings and CSRF-protected logout. Menu visibility
does not replace endpoint authorization.

The portal header includes the global fragment before its own masthead. Local
navigation such as calendar month controls, pagination and the portal sidebar
remains with the corresponding feature. The printable administrative notice
intentionally has no site menu.
