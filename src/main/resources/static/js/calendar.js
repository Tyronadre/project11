(() => {
    const tooltip = document.getElementById('calendar-holiday-tooltip');
    if (!tooltip) return;
    let active = null;
    let closeTimer;

    const hide = () => {
        clearTimeout(closeTimer);
        active?.removeAttribute('aria-describedby');
        active = null;
        tooltip.hidden = true;
    };
    const position = () => {
        if (!active) return;
        const entry = active.getBoundingClientRect();
        // Fixed positioning keeps the card outside the calendar's scrolling/clipped area.
        const margin = 12;
        const left = Math.max(margin, Math.min(entry.left, window.innerWidth - tooltip.offsetWidth - margin));
        const below = entry.bottom + 6;
        const top = below + tooltip.offsetHeight <= window.innerHeight - margin
            ? below : Math.max(margin, entry.top - tooltip.offsetHeight - 6);
        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${top}px`;
    };
    const show = entry => {
        hide();
        active = entry;
        tooltip.textContent = entry.dataset.holidayDetails;
        tooltip.style.setProperty('--holiday-color', entry.style.getPropertyValue('--holiday-color'));
        tooltip.hidden = false;
        entry.setAttribute('aria-describedby', tooltip.id);
        position();
    };
    const closeSoon = () => {
        clearTimeout(closeTimer);
        closeTimer = setTimeout(() => {
            if (active !== document.activeElement && !active?.matches(':hover') && !tooltip.matches(':hover')) hide();
        }, 150);
    };

    document.querySelectorAll('[data-holiday-details]').forEach(entry => {
        // Keep the native title as the fallback when JavaScript is disabled.
        entry.removeAttribute('title');
        entry.addEventListener('pointerenter', event => {
            if (event.pointerType !== 'touch') show(entry);
        });
        entry.addEventListener('pointerleave', closeSoon);
        entry.addEventListener('focus', () => show(entry));
        entry.addEventListener('blur', closeSoon);
        entry.addEventListener('click', hide);
    });
    tooltip.addEventListener('pointerenter', () => clearTimeout(closeTimer));
    tooltip.addEventListener('pointerleave', closeSoon);
    document.addEventListener('keydown', event => { if (event.key === 'Escape') hide(); });
    // A scroll can move an entry completely out of view; dismiss instead of leaving an orphan card.
    document.addEventListener('scroll', event => { if (event.target !== tooltip) hide(); }, true);
    window.addEventListener('resize', hide);
})();
