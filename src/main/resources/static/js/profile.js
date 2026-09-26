(() => {
    const form = document.querySelector('[data-payment-form]');
    if (!form) return;
    const button = form.querySelector('button');
    const output = document.querySelector('#payment-values');
    const error = document.querySelector('[data-payment-error]');
    function hide() {
        output.replaceChildren();
        button.textContent = 'Zahlungsdaten anzeigen';
        button.setAttribute('aria-expanded', 'false');
    }
    form.addEventListener('submit', async event => {
        event.preventDefault(); error.hidden = true;
        if (button.getAttribute('aria-expanded') === 'true') { hide(); return; }
        button.disabled = true;
        button.textContent = 'Wird geladen …';
        try {
            const data = new URLSearchParams(new FormData(form));
            data.set('fragment', 'true');
            const response = await fetch(form.action, {method: 'POST', body: data, cache: 'no-store'});
            if (!response.ok || response.redirected) throw new Error('request failed');
            // The server fragment uses escaped text for all profile values.
            output.innerHTML = await response.text();
            button.textContent = 'Zahlungsdaten verbergen';
            button.setAttribute('aria-expanded', 'true');
        } catch (_) {
            hide(); error.textContent = 'Die Zahlungsdaten konnten nicht geladen werden. Bitte lade die Seite neu und versuche es erneut.';
            error.hidden = false;
        } finally { button.disabled = false; }
    });
    window.addEventListener('pagehide', hide);
    window.addEventListener('pageshow', hide);
})();
