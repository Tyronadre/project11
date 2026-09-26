(() => {
    "use strict";

    const form = document.getElementById("absence-form") || document.getElementById("travel-form");
    if (!form) return;

    const activity = document.getElementById("activityId");
    const information = document.getElementById("activity-information");
    const updateActivity = () => {
        const option = activity.selectedOptions[0];
        information.textContent = option?.dataset.period
            ? `Termin: ${option.dataset.period}\nEinreichung vor: ${option.dataset.deadline}\nZu wiederholendes Datum: ${option.dataset.date}`
            : "Nach Auswahl werden Termin und Einreichungsfrist zur Kenntnisnahme mitgeteilt.";
    };
    if (activity) {
        activity.addEventListener("change", updateActivity);
        updateActivity();
    }

    const reason = document.getElementById("detailedReason");
    const counter = document.getElementById("reason-count");
    const countReason = () => { counter.textContent = String(reason.value.trim().length); };
    if (reason) {
        reason.addEventListener("input", countReason);
        countReason();
    }

    const holiday = document.getElementById("holidayId");
    if (holiday) {
        const updateHoliday = () => {
            const option = holiday.selectedOptions[0];
            document.getElementById("holiday-information").textContent = option?.dataset.deadline
                ? `Einreichungsfrist: ${option.dataset.deadline}${option.dataset.overdue === "true" ? " · Frist abgelaufen: Der Eingang wird als verspätet gespeichert." : ""}`
                : "Nach Auswahl wird die Einreichungsfrist mitgeteilt.";
        };
        holiday.addEventListener("change", updateHoliday);
        updateHoliday();
    }

    const relationship = document.getElementById("travel-relationship");
    if (relationship) {
        const warning = document.getElementById("regret-warning");
        const updateRegret = () => { warning.hidden = relationship.value !== "Ich bereue meinen Antrag bereits"; };
        relationship.addEventListener("change", updateRegret);
        warning.querySelectorAll("[data-dismiss-regret]").forEach(button => button.addEventListener("click", () => {
            warning.hidden = true;
            relationship.focus();
        }));
        updateRegret();
    }

    const upload = document.getElementById("travel-photos");
    if (upload) {
        upload.addEventListener("change", () => {
            const count = Number(upload.dataset.existing) + upload.files.length;
            const oversized = [...upload.files].some(file => file.size > 4 * 1024 * 1024);
            upload.setCustomValidity(count > 6 ? "Höchstens sechs Fotos insgesamt auswählen." : oversized ? "Jedes Foto darf höchstens 4 MiB groß sein." : "");
            document.getElementById("photo-information").textContent = `${upload.dataset.existing} Fotos gespeichert, ${upload.files.length} zum Hochladen ausgewählt. Insgesamt erforderlich: 3 bis 6.`;
        });
    }

    const primaryChecks = [...form.querySelectorAll("[data-primary-confirmation]")];
    const finalBox = document.getElementById("final-checkbox");
    const finalCheck = finalBox.querySelector('input[type="checkbox"]');
    const updateConfirmations = () => {
        const confirmed = primaryChecks.every(input => input.checked);
        finalBox.hidden = !confirmed;
        if (!confirmed) finalCheck.checked = false;
    };
    primaryChecks.forEach(input => input.addEventListener("change", updateConfirmations));
    updateConfirmations();

    // Questions and field names come from the server. Only their visual order changes.
    const randomFields = form.querySelector(".amt-random-fields");
    let reordered = false;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const personalInputs = [...randomFields.querySelectorAll("input")];
    const personalNote = document.getElementById("personal-useless-note");
    const updatePersonalNote = () => { personalNote.hidden = !personalInputs.every(input => input.value.trim()); };
    randomFields.addEventListener("input", updatePersonalNote);
    updatePersonalNote();
    randomFields.addEventListener("focusout", () => {
        // Wait for the next focus target so a focused field never moves away mid-entry.
        window.setTimeout(() => {
            if (reordered || reducedMotion.matches || randomFields.contains(document.activeElement)) return;
            if (![...randomFields.querySelectorAll("input")].some(input => input.value.trim())) return;
            const first = randomFields.firstElementChild;
            randomFields.append(first);
            first.classList.add("amt-field-relocated");
            reordered = true;
            document.getElementById("field-reorder-notice").textContent =
                "Hinweis: Die Feldreihenfolge wurde aus organisatorischen Gründen geändert. Ihre Angaben sind weiterhin vorhanden.";
        }, 0);
    });

    const fields = [...form.querySelectorAll("input[required], select[required], textarea[required]")];
    const updateProgress = () => {
        if (form.classList.contains("amt-stepped-form")) return;
        const completed = fields.filter(field => field.validity.valid).length;
        const ratio = fields.length ? completed / fields.length : 0;
        // The intentionally dubious estimate is decorative, not a submission gate.
        const estimate = Math.min(92, 73 + Math.floor(ratio * 19));
        document.getElementById("amt-progress-fill").style.width = `${estimate}%`;
        document.getElementById("amt-progress-label").textContent = `${estimate} % abgeschlossen*`;
        document.getElementById("amt-step-label").textContent = ratio >= 1 ? "Schritt 8 von 7" : `Schritt ${Math.max(1, Math.ceil(ratio * 7))} von 7`;
    };
    form.addEventListener("input", updateProgress);
    form.addEventListener("change", updateProgress);
    updateProgress();
})();
