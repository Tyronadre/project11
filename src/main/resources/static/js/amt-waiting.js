'use strict';

(() => {
    const button = document.getElementById('waiting-auto');
    const status = document.getElementById('waiting-update-status');
    if (!button || !status) return;
    const key = 'amt-waiting:' + window.location.pathname;
    let timer;
    let enabled = false;
    try { enabled = sessionStorage.getItem(key) === 'on'; } catch (_) { /* Manual updates still work. */ }
    function update() {
        clearTimeout(timer);
        button.textContent = enabled ? 'Automatische Aktualisierung anhalten' : 'Automatische Aktualisierung starten';
        button.setAttribute('aria-pressed', String(enabled));
        status.textContent = enabled ? 'Die Seite wird alle 15 Sekunden neu geladen.' : 'Automatische Aktualisierung ist aus.';
        try { sessionStorage.setItem(key, enabled ? 'on' : 'off'); } catch (_) { /* Optional preference. */ }
        if (enabled) timer = setTimeout(() => window.location.reload(), 15000);
    }
    button.hidden = false;
    button.addEventListener('click', () => { enabled = !enabled; update(); });
    window.addEventListener('pagehide', () => clearTimeout(timer));
    update();
})();
