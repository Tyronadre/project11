(() => {
    "use strict";

    const form = document.getElementById("absence-form") || document.getElementById("travel-form");
    if (!form) return;
    const sections = [...form.querySelectorAll(".amt-fieldset")];
    const submission = form.querySelector(".amt-submit-area");
    if (!sections.length || !submission) return;
    const panels = [...sections, submission];
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const labels = sections.map(section => section.querySelector("legend").textContent.trim());
    labels.push("Abschluss / Übergabe an die Vorprüfung");

    const element = (tag, className, text) => {
        const node = document.createElement(tag);
        node.className = className;
        if (text) node.textContent = text;
        return node;
    };
    const button = (text, action, primary = false) => {
        const node = element("button", `amt-button${primary ? " amt-primary" : ""}`, text);
        node.type = "button";
        node.addEventListener("click", action);
        return node;
    };

    // These detours only affect presentation. Answers, attachments and filing times never change.
    // Detours have a finite sequence and are completed only once per section; moving buttons stop after three escapes.
    const incidents = [
        { title: "Stempelstelle / dreifache Ausfertigung", stamps: true, steps: [
            ["Der Weiter-Antrag liegt nur im Original vor. Bitte das Original abstempeln.", "Original abstempeln"],
            ["STEMPEL ERTEILT. Leider benötigt auch die Kopie einen Originalstempel.", "Kopie original abstempeln"],
            ["Die Kopie ist vollständig. Es fehlt nur die Kopie der Kopie.", "Kopie der Kopie abstempeln & weiter"]
        ] },
        { title: "Drucker 07 / papierloses Verfahren", steps: [
            ["Zur papierlosen Weiterleitung muss dieser Abschnitt ausgedruckt werden. Druckerstatus: KEIN PAPIER.", "Virtuelles Papier nachlegen"],
            ["Papier erkannt. Neuer Druckerstatus: KEIN TONER. Schwarzweiß hilft ausdrücklich nicht.", "Imaginären Toner schütteln"],
            ["Ausdruck erfolgreich. Das Blatt wurde zur Wahrung der Papierlosigkeit sofort geschreddert.", "Vernichtungsnachweis akzeptieren & weiter"]
        ] },
        { title: "Referats-Pingpong / Zuständigkeit unklar", steps: [
            ["Referat B ist für die Weiterleitung zuständig, erklärt sich jedoch für unzuständig.", "An Referat D weiterleiten"],
            ["Referat D bestätigt die Unzuständigkeit von Referat B und schickt die Akte zurück.", "Zurück an Referat B"],
            ["Referat B hat inzwischen Feierabend. Der Zimmerfarn übernimmt die Freigabe.", "Freigabe des Zimmerfarns anerkennen & weiter"]
        ] },
        { title: "Wartemarkenverwaltung / niemand vor Ihnen", steps: [
            ["Bitte ziehen Sie eine Wartemarke. Anzahl wartender Personen: 0. Geschätzte Wartezeit: verwaltungsüblich.", "Wartemarke ziehen"],
            ["Ihre Nummer: 404. Aufgerufen wird: 403½. Der Schalter übt noch mit Brüchen.", "Aufruf in ganzen Zahlen beantragen"],
            ["Nummer 404 bitte! Sie waren die ganze Zeit an der Reihe. Vielen Dank für Ihre unnötige Geduld.", "Ordnungsgemäß vortreten & weiter"]
        ] },
        { title: "Fortschrittsrevision / Rückschritt genehmigt", steps: [
            ["Ihr Fortschritt wurde von 92 % auf 41 % korrigiert. Sie haben schneller ausgefüllt, als wir schätzen konnten.", "Rückschritt zur Kenntnis nehmen"],
            ["Nach erneuter Schätzung sind wieder 73 % erreicht. Sie haben dafür nichts getan. Das ist so vorgesehen.", "Schätzleistung würdigen & weiter"]
        ] },
        { title: "Weiterleitungsprüfung / doppelte Verneinung", steps: [
            ["Bestätigen Sie bitte, dass Sie nicht beabsichtigen, das Nichtweitergehen fortzusetzen.", "Nichtweitergehen nicht fortsetzen"],
            ["Wir haben Ihr Nein zum Nichtweitergehen als Ja zum Weitergehen ausgelegt. Ein Widerspruch würde ebenfalls als Weitergehen gewertet.", ["Ja, weiter", "Ja, aber unter Protest weiter"]]
        ] },
        { title: "Außendienst / der Weiter-Knopf ist unterwegs", kind: "runaway" },
        { title: "Referat Ruhe / Lautstärke des Schweigens", kind: "silence" },
        { title: "Menschlichkeitsprüfung / Büroklammer ausgeschlossen", steps: [
            ["Bitte bestätigen Sie, dass Sie keine Büroklammer sind. Büroklammern können diesen Hinweis ebenfalls lesen; wir arbeiten an dem Problem.", ["Ich bin ein Mensch", "Ich bin zumindest keine Büroklammer"]],
            ["Ihre Aussage wurde ungeprüft übernommen. Das ist die Grundlage unseres Sicherheitskonzepts.", "Menschlich weitergehen"]
        ] },
        { title: "Dienstkaffee / Betriebsbereitschaft unklar", steps: [
            ["Der zuständige Sachbearbeiter ist anwesend, aber noch nicht betriebsbereit. Koffeinstand: bedenklich sachlich.", "Virtuellen Kaffee reichen"],
            ["Der Kaffee wurde angenommen. Leider befindet sich die Kaffeetasse nun auf Ihrem Weiter-Antrag.", "Tasse höflich versetzen"],
            ["Der Kaffeefleck gilt gemäß Hausordnung als Dienstsiegel. Ihr Abschnitt ist damit ausreichend beglaubigt.", "Kaffeefleck anerkennen & weiter"]
        ] },
        { title: "Deckblattstelle / Antrag auf Antragsabdeckung", steps: [
            ["Ihrem Abschnitt fehlt ein Deckblatt. Ohne Deckblatt ist für uns nicht erkennbar, dass darunter ein Abschnitt liegt.", "Deckblatt erzeugen"],
            ["Das Deckblatt benötigt seinerseits ein Deckblatt. Die Verwaltung erwägt eine Endlosschleife.", "Oberdeckblatt erzeugen"],
            ["Die Endlosschleife wurde aus Kostengründen nach zwei Deckblättern eingestellt.", "Papierstapel zur Vorführung anmelden & weiter"]
        ] },
        { title: "Fernmeldestelle / digitales Faxverfahren", steps: [
            ["Ihr Abschnitt wird per Fax an denselben Computer geschickt. Bitte stellen Sie sich jetzt ein sehr unangenehmes Piepen vor.", "Piepen innerlich bestätigen"],
            ["Besetzt. Offenbar faxen Sie sich gerade selbst. Wir haben die Verbindung durch gutes Zureden überredet.", "Fax an mich selbst entgegennehmen"],
            ["Empfang bestätigt. Die Lesbarkeit wurde vorsorglich auf Faxniveau reduziert; Ihre tatsächlichen Angaben bleiben lesbar.", "Gedachte Empfangskopie abheften & weiter"]
        ] },
        { title: "Scanstelle / Formularlage verkehrt", inverted: true, steps: [
            ["Ihr Abschnitt wurde versehentlich kopfüber eingescannt. Bitte drehen Sie ausschließlich dieses Hinweisblatt um. Den Bildschirm können Sie stehen lassen.", "Hinweisblatt amtlich umdrehen"],
            ["Die Welt steht wieder richtig herum. Es ist erstaunlich, was ein weiterer Klick bewirken kann.", "Orientierung bestätigen & weiter"]
        ] },
        { title: "Keksverwaltung / notwendiges Gebäck", steps: [
            ["Diese Dienststelle verwendet Kekse. Echte Kekse. Bitte wählen Sie eine rechtsverbindliche Haltung zu Rosinen.", ["Nur notwendige Rosinen", "Alle Rosinen ablehnen"]],
            ["Ihre Präferenz wurde auf einem Keks notiert. Der Keks wurde gegessen. Eine Speicherung fand nicht statt.", "Verlust der Rosinenpräferenz verschmerzen & weiter"]
        ] },
        { title: "Maus-TÜV / ruhender Zeigerverkehr", kind: "parking" },
        { title: "Sparkommission / schrumpfender Weiter-Knopf", kind: "shrinking" },
        { title: "Unterschriftenstelle / Schreibgerät unzulässig", steps: [
            ["Wählen Sie ein Schreibgerät für Ihre virtuelle Unterschrift. Die Stiftzulassungsstelle ist auf jede Enttäuschung vorbereitet.", ["Kugelschreiber", "Bleistift", "Füller"]],
            ["Dieser Stift ist nicht zugelassen. Die beiden anderen übrigens auch nicht. Zulässig ist ausschließlich eine gedankliche Unterschrift.", "Gedanklich unterschreiben"],
            ["Ihre unsichtbare Unterschrift ist ausreichend unleserlich und wird daher als authentisch eingestuft.", "Gedanken trockenpusten & weiter"]
        ] },
        { title: "Dienstaufzug / nächster Abschnitt im zweiten Stock", kind: "elevator" },
        { title: "Entschleunigungsstelle / amtliches Klicktempo", kind: "slowClick" },
        { title: "Nichtauswahlprüfung / unberührte Kästchen", kind: "nonselection" },
        { title: "Wertstoffreferat / der beleidigte Papierkorb", kind: "trash" },
        { title: "Fortschrittstechnik / Luftdruck unzureichend", kind: "pump" },
        { title: "Zukunftsstelle / Widerspruch aus morgen", steps: [
            ["Ihr zukünftiges Ich hat gegen den zusätzlichen Verwaltungsaufwand Widerspruch eingelegt. Es behauptet, Sie hätten Besseres zu tun.", ["Widerspruch widersprechen", "Zukünftiges Ich vertrösten"]],
            ["Ihr zukünftiges Ich wurde auf später vertröstet. Es hat dies mit der Begründung akzeptiert, dass es ohnehin erst später zuständig ist.", "Gegenwärtig weitergehen"]
        ] },
        { title: "Protokollreferat / feierliche Abschnittseröffnung", kind: "ribbon" }
    ];
    // Shuffle a finite pool so different sections do not receive the same joke.
    const pool = [...incidents];
    for (let i = pool.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [pool[i], pool[j]] = [pool[j], pool[i]];
    }
    const detours = new Map();
    const completed = new Set();
    const navs = [];
    let current = 0;
    let activeDetour = null;

    const heading = element("h3", "amt-step-heading");
    heading.tabIndex = -1;
    const track = element("ol", "amt-step-list");
    track.setAttribute("aria-label", "Formularabschnitte");
    labels.forEach(label => track.append(element("li", "", label.split(".")[0])));
    form.prepend(heading, track);
    const items = [...track.children];

    const updateProgress = (incident = false) => {
        const estimate = incident ? 41 : (current === sections.length ? 92 : 73 + current * 3);
        document.getElementById("amt-progress-fill").style.width = `${estimate}%`;
        document.getElementById("amt-progress-label").textContent = `${estimate} % abgeschlossen*`;
        document.getElementById("amt-step-label").textContent = incident ? "Zwischenschritt 8 von 7" : `Schritt ${current + 1} von 7`;
    };
    const clearDetour = () => {
        if (!activeDetour) return;
        activeDetour.remove();
        activeDetour = null;
        navs[current].hidden = false;
    };
    const show = (index, focus = true) => {
        clearDetour();
        current = index;
        panels.forEach((panel, i) => { panel.hidden = i !== index; });
        items.forEach((item, i) => {
            if (i === index) item.setAttribute("aria-current", "step");
            else item.removeAttribute("aria-current");
        });
        heading.textContent = `Abschnitt ${index + 1} von ${panels.length}: ${labels[index]}`;
        updateProgress();
        if (focus) {
            heading.focus({ preventScroll: true });
            heading.scrollIntoView({ block: "start", behavior: reducedMotion.matches ? "instant" : "smooth" });
        }
    };

    const controls = section => [...section.querySelectorAll("input, select, textarea")]
        .filter(input => input.willValidate && !input.closest(".amt-step-detour"));
    const invalidField = section => {
        for (const input of controls(section)) {
            if (input.matches('textarea, input[type="text"], input:not([type])')) {
                const value = input.value.trim();
                input.setCustomValidity(input.required && !value ? "Bitte dieses Pflichtfeld ausfüllen."
                    : input.minLength > 0 && value.length < input.minLength ? `Bitte mindestens ${input.minLength} Zeichen eingeben.` : "");
            }
            if (input.id === "travel-photos") {
                const count = Number(input.dataset.existing) + input.files.length;
                input.setCustomValidity(count < 3 || count > 6 ? "Bitte insgesamt drei bis sechs Fotos hinzufügen."
                    : [...input.files].some(file => file.size > 4 * 1024 * 1024) ? "Jedes Foto darf höchstens 4 MiB groß sein." : "");
            }
            if (!input.validity.valid) return input;
        }
        return null;
    };
    const reportInvalid = (index, input) => {
        show(index, false);
        // The last confirmation normally opens only after the preceding three were checked.
        const extra = input.closest("#final-checkbox");
        if (extra) extra.hidden = false;
        input.focus();
        input.reportValidity();
    };

    const mountRunaway = (body, message, actions, finish, cancel) => {
        message.textContent = "Der Weiter-Knopf hat Außendienst beantragt und weicht Ihrer Maus aus. Nach drei Dienstwegen ist sein Reisebudget aufgebraucht. Bei Bedarf die Dienstaufsicht rufen.";
        const arena = element("div", "amt-runaway-arena");
        arena.setAttribute("aria-label", "Dienstgelände des Weiter-Knopfs");
        const status = element("p", "amt-runaway-status");
        status.setAttribute("aria-live", "polite");
        const positions = [[1, 1], [2, 1], [1, 2], [2, 2], [1, 3], [2, 3]];
        let position = 0;
        let escapes = 0;
        const update = () => {
            target.style.gridColumn = String(positions[position][0]);
            target.style.gridRow = String(positions[position][1]);
            status.textContent = escapes >= 3 ? "Reisebudget aufgebraucht. Der Knopf bleibt jetzt hier."
                : `Außendienst: ${escapes} von 3 Dienstwegen abgerechnet.`;
            if (escapes >= 3) target.textContent = "Weiter · eingefangen";
        };
        const escape = () => {
            if (escapes >= 3 || reducedMotion.matches) return false;
            // Choose a distant grid cell; sizing remains in CSS, including after zoom/rotation.
            const [column, row] = positions[position];
            const candidates = positions.map((cell, index) => ({ cell, index }))
                .filter(({ cell }) => Math.abs(cell[0] - column) + Math.abs(cell[1] - row) >= 2);
            position = candidates[Math.floor(Math.random() * candidates.length)].index;
            escapes++;
            update();
            return true;
        };
        const target = button("Weiter »", event => {
            // Touch/pen escape on activation rather than hover. Keyboard activation never dodges.
            if (event.detail > 0 && (event.pointerType === "touch" || event.pointerType === "pen") && escape()) return;
            finish();
        }, true);
        target.classList.add("amt-runaway-button");
        target.addEventListener("pointerenter", event => {
            if (event.pointerType === "mouse" && !target.matches(":focus-visible")) escape();
        });
        const hold = button("Dienstaufsicht rufen / Knopf festhalten", () => {
            escapes = 3;
            update();
            target.focus({ preventScroll: true });
        });
        arena.append(target);
        body.append(arena, status);
        actions.append(hold, button("Zurück zu meinen Angaben", cancel));
        update();
        if (reducedMotion.matches) status.textContent = "Außendienst entfällt bei reduzierter Bewegung. Der Knopf wartet auf Sie.";
        message.focus({ preventScroll: true });
    };

    const mountSilence = (body, message, actions, finish, cancel) => {
        message.textContent = "Vor der Weiterleitung muss das Schweigen kalibriert werden. Bitte stellen Sie es auf 100 %. Es wird selbstverständlich kein Ton abgespielt.";
        const label = element("label", "amt-silence-label", "Amtlich gewünschtes Schweigen");
        const slider = element("input", "amt-silence-slider");
        slider.type = "range";
        slider.min = "0";
        slider.max = "100";
        slider.step = "10";
        slider.value = "50";
        // This prop is deliberately unnamed: only the application answers belong in the POST.
        label.append(slider);
        const output = element("p", "amt-silence-output");
        output.setAttribute("aria-live", "polite");
        const update = () => { output.textContent = `${slider.value} % Schweigen · gemessene Lautstärke: weiterhin 0 dB (geschätzt)`; };
        slider.addEventListener("input", update);
        body.append(label, output);
        actions.append(button("Schweigen prüfen", () => {
            if (slider.value !== "100") {
                output.textContent = "Das Schweigen ist noch nicht vollständig. Bitte den Regler ganz nach rechts auf 100 % stellen.";
                slider.focus();
                return;
            }
            label.hidden = true;
            output.hidden = true;
            message.textContent = "100 % Schweigen festgestellt. Die zuständige Stelle hat nichts hinzuzufügen, verlangt aber eine Bestätigung dieses Umstands.";
            actions.replaceChildren(button("Nichts hinzufügen & weiter", finish, true), button("Zurück zu meinen Angaben", cancel));
            message.focus({ preventScroll: true });
        }, true), button("Zurück zu meinen Angaben", cancel));
        update();
        message.focus({ preventScroll: true });
    };

    const setDetourActions = (actions, cancel, ...choices) => {
        actions.replaceChildren(...choices, button("Zurück zu meinen Angaben", cancel));
    };

    const mountParking = (body, message, actions, finish, cancel) => {
        message.textContent = "Bitte parken Sie Ihren Mauszeiger innerhalb der Markierung. Rückwärts einparken wird wohlwollend zur Kenntnis genommen. Mit Touch oder Tastatur bitte den Parkplatz betätigen.";
        let parked = false;
        const park = () => {
            if (parked) return;
            parked = true;
            bay.classList.add("amt-parking-complete");
            bay.textContent = "P · ordnungsgemäß geparkt";
            message.textContent = "Maus-TÜV bestanden. Ihr Zeiger stand kurz still. Die Verwaltung erkennt sich darin wieder.";
            setDetourActions(actions, cancel, button("Parkbescheinigung abholen & weiter", finish, true));
        };
        const bay = button("P · Zeiger hier abstellen", park);
        bay.classList.add("amt-parking-bay");
        bay.addEventListener("pointerenter", event => { if (event.pointerType === "mouse") park(); });
        body.append(bay);
        setDetourActions(actions, cancel);
        message.focus({ preventScroll: true });
    };

    const mountShrinking = (body, message, actions, finish, cancel) => {
        message.textContent = "Die Sparkommission verkleinert bei jedem Klick den Weiter-Knopf. Bitte dreimal versuchen. Ihre Finger müssen an dieser Einsparung nicht teilnehmen.";
        let clicks = 0;
        const label = element("span", "amt-shrinking-label", "Weiter »");
        const target = button("", () => {
            clicks++;
            label.style.transform = `scale(${Math.pow(0.65, clicks)})`;
            message.textContent = `Verkleinerung ${clicks} von 3 genehmigt. Der Verwaltungsaufwand passt schon fast unter einen Fingernagel.`;
            if (clicks === 3) {
                target.disabled = true;
                message.textContent = "Der Verwaltungsaufwand wurde erfolgreich reduziert. Sie benötigen jetzt eine größere Lupe, um den kleineren Aufwand zu bearbeiten.";
                const magnify = button("🔍 Mit amtlicher Lupe weiter", finish, true);
                magnify.classList.add("amt-magnifier-button");
                setDetourActions(actions, cancel, magnify);
                magnify.focus({ preventScroll: true });
            }
        });
        // Only the visible label shrinks; the actual keyboard/touch target stays large.
        target.classList.add("amt-shrinking-target");
        target.setAttribute("aria-label", "Weiter-Knopf verkleinern, insgesamt dreimal");
        target.append(label);
        body.append(target);
        setDetourActions(actions, cancel);
        message.focus({ preventScroll: true });
    };

    const mountElevator = (body, message, actions, finish, cancel) => {
        message.textContent = "Der nächste Abschnitt befindet sich im zweiten Stock. Bitte wählen Sie Ihr Ziel. Der Aufzug folgt einer eigenen Auslegung der Hausordnung.";
        const display = element("div", "amt-elevator-display", "EG · Antragsannahme");
        display.setAttribute("aria-live", "polite");
        const floors = element("div", "amt-floor-buttons");
        let diverted = false;
        const ride = floor => {
            if (floor !== 2) {
                display.textContent = floor === -1 ? "−1 · Kellerarchiv" : "1 · Flur ohne Zuständigkeit";
                message.textContent = "Hier ist Ihr nächster Abschnitt nicht. Bitte die Taste 2 betätigen. Wir hätten auch nur diese Taste anbieten können.";
                return;
            }
            if (!diverted) {
                diverted = true;
                display.textContent = "−1 · Kellerarchiv (ungeplanter Halt)";
                message.textContent = "Sie haben Ihr Ziel beinahe erreicht. Der Aufzug wollte noch kurz seine alten Akten besuchen. Bitte Stockwerk 2 erneut wählen.";
                return;
            }
            display.textContent = "2 · Nächster Abschnitt";
            message.textContent = "Ziel erreicht. Bitte treten Sie zwischen den gedanklichen Türen hindurch.";
            floors.hidden = true;
            setDetourActions(actions, cancel, button("Aussteigen & weiter", finish, true));
            message.focus({ preventScroll: true });
        };
        [[-1, "−1 · Archiv"], [1, "1 · Flur"], [2, "2 · Weiter"]].forEach(([floor, label]) => floors.append(button(label, () => ride(floor))));
        body.append(display, floors);
        setDetourActions(actions, cancel);
        message.focus({ preventScroll: true });
    };

    const mountSlowClick = (body, message, actions, finish, cancel) => {
        message.textContent = "Bitte beantragen Sie das Weitergehen durch Betätigung des nachstehenden Knopfes. Das Klicktempo wird mit unangemessener Aufmerksamkeit betrachtet.";
        let attempt = 0;
        let pressedAt = null;
        const target = button("Weiter beantragen", event => {
            const duration = event.detail === 0 || pressedAt === null ? 0 : performance.now() - pressedAt;
            pressedAt = null;
            attempt++;
            if (attempt === 1) {
                message.textContent = "Sie haben zu schnell auf Weiter geklickt. Bitte führen Sie einen besonders langsamen Klick aus: kurz gedrückt halten und dann loslassen. Die Tastatur bleibt ebenfalls zulässig.";
                target.textContent = "Besonders langsam klicken";
                return;
            }
            message.textContent = duration >= 800 ? "Zu langsam. Wir haben den ersten Klick wieder zugelassen. Vielen Dank für Ihre zeitliche Mitarbeit."
                : "Ihr Klicktempo ist weiterhin nicht eindeutig verwaltungsüblich. Aus Zeitgründen haben wir den ersten Klick wieder zugelassen.";
            setDetourActions(actions, cancel, button("Mit dem ersten Klick weiter", finish, true));
            message.focus({ preventScroll: true });
        }, true);
        target.addEventListener("pointerdown", () => { pressedAt = performance.now(); });
        target.addEventListener("pointercancel", () => { pressedAt = null; });
        setDetourActions(actions, cancel, target);
        message.focus({ preventScroll: true });
    };

    const mountNonselection = (body, message, actions, finish, cancel) => {
        message.textContent = "Die folgenden drei Kästchen dürfen ausdrücklich NICHT angekreuzt werden. Ihre Nichtauswahl ist anschließend gesondert zu bestätigen.";
        const choices = element("div", "amt-nonselection");
        const checks = [1, 2, 3].map(number => {
            const label = element("label", "amt-check");
            const check = document.createElement("input");
            check.type = "checkbox";
            label.append(check, element("span", "", `Kästchen ${number} · bitte nicht ankreuzen`));
            choices.append(label);
            return check;
        });
        const label = element("label", "amt-check amt-confirmation-extra");
        const confirm = document.createElement("input");
        confirm.type = "checkbox";
        label.append(confirm, element("span", "", "Ich bestätige, alle drei Kästchen ordnungsgemäß nicht angekreuzt zu haben."));
        choices.append(label);
        body.append(choices);
        setDetourActions(actions, cancel, button("Nichtauswahl bestätigen & weiter", () => {
            const selected = checks.find(check => check.checked);
            if (selected) {
                message.textContent = "Eine Auswahl wurde festgestellt. Bitte entfernen Sie sämtliche drei möglichen Kreuze. Nur die Bestätigung darunter soll angekreuzt sein.";
                selected.focus();
            } else if (!confirm.checked) {
                message.textContent = "Ihre Nichtauswahl ist tadellos. Leider fehlt noch das Kreuz zur Bestätigung der fehlenden Kreuze.";
                confirm.focus();
            } else finish();
        }, true));
        // Props are unnamed and never affect the real application confirmations.
        message.focus({ preventScroll: true });
    };

    const mountTrash = (body, message, actions, finish, cancel) => {
        message.textContent = "Der Papierkorb ist beleidigt: Hier wird immer alles aufgehoben. Bitte schenken Sie ihm einen leeren Zettel, damit er sich gebraucht fühlt.";
        const scene = element("div", "amt-trash-scene");
        scene.setAttribute("aria-hidden", "true");
        const paper = element("span", "amt-blank-paper", "LEER");
        const bin = element("span", "amt-sad-bin", "▥");
        scene.append(paper, bin);
        body.append(scene);
        setDetourActions(actions, cancel, button("Leeren Zettel schenken", () => {
            paper.hidden = true;
            bin.textContent = "▥ ♥";
            scene.classList.add("amt-trash-happy");
            message.textContent = "Vielen Dank. Endlich werde ich hier gebraucht. Der Papierkorb hat ausschließlich den leeren Geschenkzettel erhalten; Ihre Angaben sind weiterhin vorhanden.";
            setDetourActions(actions, cancel, button("Papierkorb in Ruhe glücklich sein lassen & weiter", finish, true));
            message.focus({ preventScroll: true });
        }, true));
        message.focus({ preventScroll: true });
    };

    const mountPump = (body, message, actions, finish, cancel) => {
        message.textContent = "Ihr Fortschrittsbalken hat Luft verloren. Bitte dreimal mit der Dienstfahrradpumpe nachpumpen. Berstende Anträge werden nicht angenommen.";
        const gauge = element("progress", "amt-pump-gauge");
        gauge.max = 100;
        gauge.value = 10;
        gauge.setAttribute("aria-label", "Luftfüllung des dekorativen Fortschrittsbalkens");
        const reading = element("p", "amt-prop-reading", "10 % Luftfüllung · 0 von 3 Pumpstößen");
        reading.setAttribute("aria-live", "polite");
        let pumps = 0;
        body.append(gauge, reading);
        const pump = button("↥ Einmal kräftig pumpen", () => {
            pumps++;
            gauge.value = 10 + pumps * 30;
            reading.textContent = `${gauge.value} % Luftfüllung · ${pumps} von 3 Pumpstößen`;
            if (pumps === 3) {
                message.textContent = "Sollfüllung erreicht. Mehr Pumpen wäre Übererfüllung. Der echte Bearbeitungsstand wurde durch diese körperliche Vorstellungskraft nicht beeinflusst.";
                setDetourActions(actions, cancel, button("Ventil schließen & weiter", finish, true));
                message.focus({ preventScroll: true });
            }
        }, true);
        setDetourActions(actions, cancel, pump);
        message.focus({ preventScroll: true });
    };

    const mountRibbon = (body, message, actions, finish, cancel) => {
        message.textContent = "Der nächste Abschnitt ist fertig, aber noch nicht feierlich eröffnet. Zur Durchtrennung des roten Bandes benötigen Sie eine ordnungsgemäß beantragte Schere.";
        const ribbon = element("div", "amt-opening-ribbon");
        ribbon.setAttribute("aria-hidden", "true");
        ribbon.append(element("span", "", "ERÖFFNUNG"), element("span", "", "AUSSTEHEND"));
        body.append(ribbon);
        setDetourActions(actions, cancel, button("Virtuelle Schere beantragen", () => {
            message.textContent = "Schere bewilligt. Bitte schneiden Sie ausschließlich das Band und keine Ecken aus Ihrem Antrag.";
            setDetourActions(actions, cancel, button("✂ Rotes Band durchtrennen", () => {
                ribbon.classList.add("amt-ribbon-cut");
                ribbon.children[0].textContent = "FEIERLICH";
                ribbon.children[1].textContent = "ERÖFFNET";
                message.textContent = "Der nächste Abschnitt wurde hiermit offiziell eröffnet. Applaus ist selbstständig zu leisten. Es wird kein Publikum bereitgestellt.";
                setDetourActions(actions, cancel, button("Innerlich applaudieren & weiter", finish, true));
                message.focus({ preventScroll: true });
            }, true));
            message.focus({ preventScroll: true });
        }, true));
        message.focus({ preventScroll: true });
    };

    const customDetours = {
        runaway: mountRunaway, silence: mountSilence, parking: mountParking, shrinking: mountShrinking,
        elevator: mountElevator, slowClick: mountSlowClick, nonselection: mountNonselection,
        trash: mountTrash, pump: mountPump, ribbon: mountRibbon
    };

    const playDetour = incident => {
        navs[current].hidden = true;
        const box = element("section", "amt-dialog amt-step-detour");
        box.setAttribute("aria-label", incident.title);
        box.append(element("div", "amt-dialog-title", incident.title));
        const body = element("div", "amt-detour-body");
        const message = element("p", "");
        message.tabIndex = -1;
        message.setAttribute("aria-live", "polite");
        const actions = element("div", "amt-dialog-actions");
        const receipts = element("div", "amt-stamp-receipts");
        receipts.setAttribute("aria-hidden", "true");
        body.append(message, receipts);
        box.append(body, actions);
        sections[current].append(box);
        activeDetour = box;
        updateProgress(true);
        const finish = () => {
            completed.add(current);
            const invalid = invalidField(sections[current]);
            if (invalid) reportInvalid(current, invalid);
            else show(current + 1);
        };
        const cancel = () => {
            clearDetour();
            updateProgress();
            navs[current].querySelector(".amt-next").focus();
        };
        let round = 0;
        const render = () => {
            const [text, choices] = incident.steps[round];
            message.textContent = text;
            message.classList.toggle("amt-upside-down", Boolean(incident.inverted && round === 0));
            if (incident.stamps) {
                receipts.replaceChildren();
                for (let i = 0; i < round; i++) receipts.append(element("span", "amt-rubber-stamp", `AUSFERTIGUNG ${i + 1} · GESTEMPELT`));
            }
            actions.replaceChildren();
            const proceed = () => {
                round++;
                if (round === incident.steps.length) finish();
                else render();
            };
            for (const label of Array.isArray(choices) ? choices : [choices]) actions.append(button(label, proceed, true));
            actions.append(button("Zurück zu meinen Angaben", cancel));
            message.focus({ preventScroll: true });
        };
        if (incident.kind) customDetours[incident.kind](body, message, actions, finish, cancel);
        else render();
        box.scrollIntoView({ block: "nearest", behavior: reducedMotion.matches ? "instant" : "smooth" });
    };

    const advance = () => {
        if (activeDetour || current >= sections.length) return;
        const invalid = invalidField(sections[current]);
        if (invalid) { reportInvalid(current, invalid); return; }
        if (!detours.has(current)) detours.set(current, Math.random() < 0.7 && pool.length ? pool.pop() : null);
        const incident = detours.get(current);
        if (incident && !completed.has(current)) playDetour(incident);
        else show(current + 1);
    };

    sections.forEach((section, index) => {
        const nav = element("div", "amt-step-actions");
        if (index > 0) nav.append(button("Zurück zum vorherigen Abschnitt", () => show(index - 1)));
        const next = button("Weiter »", advance, true);
        next.classList.add("amt-next");
        nav.append(next);
        section.append(nav);
        navs.push(nav);
    });
    const back = button("Zurück zum letzten Abschnitt", () => show(sections.length - 1));
    submission.prepend(back);

    // Keep hidden controls enabled: all answers and selected files stay in the eventual POST.
    // Native form validation cannot focus hidden steps, so validate and reveal them explicitly.
    form.noValidate = true;
    form.classList.add("amt-stepped-form");
    form.addEventListener("submit", event => {
        if (current < sections.length) { event.preventDefault(); advance(); return; }
        for (let i = 0; i < sections.length; i++) {
            const invalid = invalidField(sections[i]);
            if (invalid) { event.preventDefault(); reportInvalid(i, invalid); return; }
        }
    });

    // Server-side errors (including knowledge answers) reopen their section, not a hidden field.
    const errors = form.querySelector(".amt-errors");
    if (errors) {
        const firstError = sections.findIndex(section => section.querySelector(".amt-invalid")
            || [...section.querySelectorAll(".amt-field-error")].some(error => error.textContent.trim()));
        const firstInvalid = firstError >= 0 ? firstError : sections.findIndex(section => invalidField(section));
        show(firstInvalid >= 0 ? firstInvalid : sections.length, false);
    } else show(0, false);
})();
