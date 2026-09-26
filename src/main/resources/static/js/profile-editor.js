(() => {
    const form = document.querySelector('#blog-form');
    if (!form) return;
    const loading = document.querySelector('#editor-loading');
    if (!window.Quill) {
        loading.textContent = 'Der Editor konnte nicht geladen werden. Bitte lade die Seite neu. Dein gespeicherter Beitrag bleibt erhalten.';
        return;
    }
    const Delta = Quill.import('delta');
    const Font = Quill.import('formats/font');
    Font.whitelist = ['serif', 'sans', 'mono'];
    Quill.register(Font, true);
    const toolbar = document.querySelector('#rich-toolbar');
    const shell = document.querySelector('#rich-editor-shell');
    const fields = document.querySelector('#editor-fields');
    const error = document.querySelector('#editor-error');
    const fonts = new Set(['serif', 'sans', 'mono']);
    const color = value => /^#[0-9a-f]{6}$/i.test(value || '') ? value : '#203c32';
    const quill = new Quill('#rich-editor', {
        theme: 'snow', placeholder: 'Es begann mit …',
        formats: ['font', 'color', 'bold', 'italic', 'underline', 'strike', 'header', 'blockquote'],
        modules: {toolbar: false, history: {delay: 700, maxStack: 100, userOnly: true}}
    });
    quill.root.setAttribute('role', 'textbox');
    quill.root.setAttribute('aria-labelledby', 'rich-text-label');
    quill.root.setAttribute('aria-multiline', 'true');
    quill.root.setAttribute('aria-describedby', 'editor-error');
    quill.root.setAttribute('spellcheck', 'true');
    // Pasted media and unsupported rich content never become blog attachments.
    ['IMG', 'VIDEO', 'AUDIO', 'IFRAME', 'OBJECT', 'SCRIPT', 'STYLE'].forEach(tag =>
        quill.clipboard.addMatcher(tag, () => new Delta()));
    let initial = new Delta();
    document.querySelectorAll('#editor-seed [data-block]').forEach(block => {
        block.querySelectorAll('span').forEach(run => {
            initial = initial.insert(run.textContent, {
                font: fonts.has(run.dataset.font) ? run.dataset.font : 'serif', color: color(run.dataset.color),
                bold: run.dataset.bold === 'true', italic: run.dataset.italic === 'true',
                underline: run.dataset.underline === 'true', strike: run.dataset.strike === 'true'
            });
        });
        initial = initial.insert('\n', block.dataset.kind === 'heading' ? {header: 3}
                : block.dataset.kind === 'quote' ? {blockquote: true} : {});
    });
    quill.setContents(initial, 'silent');
    quill.history.clear();
    document.querySelector('#editor-seed').remove();
    shell.hidden = false;
    loading.hidden = true;
    document.querySelector('#blog-save').disabled = false;
    let selection = {index: 0, length: 0};
    let dirty = false;

    function updateToolbar() {
        const format = quill.getFormat(selection.index, selection.length);
        toolbar.querySelectorAll('[data-format]').forEach(button =>
            button.setAttribute('aria-pressed', format[button.dataset.format] === true ? 'true' : 'false'));
        document.querySelector('#rich-font').value = Array.isArray(format.font) ? '' : (format.font || 'serif');
        document.querySelector('#rich-color').value = color(format.color);
        document.querySelector('#rich-kind').value = format.header ? 'heading' : format.blockquote ? 'quote' : 'paragraph';
    }
    function restoreSelection() {
        quill.focus({preventScroll: true});
        quill.setSelection(selection.index, selection.length, 'silent');
    }
    function apply(format, value) {
        restoreSelection();
        quill.history.cutoff();
        quill.format(format, value, 'user');
        quill.history.cutoff();
        updateToolbar();
    }
    toolbar.addEventListener('mousedown', event => {
        if (event.target.closest('button')) event.preventDefault();
    });
    toolbar.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        if (button.dataset.format) {
            apply(button.dataset.format, quill.getFormat(selection.index, selection.length)[button.dataset.format] !== true);
        } else {
            restoreSelection();
            if (button.dataset.action === 'undo') quill.history.undo();
            if (button.dataset.action === 'redo') quill.history.redo();
            if (button.dataset.action === 'clear') {
                quill.history.cutoff();
                if (selection.length) quill.removeFormat(selection.index, selection.length, 'user');
                else ['bold', 'italic', 'underline', 'strike', 'font', 'color'].forEach(name => quill.format(name, false, 'user'));
                quill.history.cutoff();
            }
            updateToolbar();
        }
    });
    document.querySelector('#rich-font').addEventListener('change', event => apply('font', event.target.value));
    document.querySelector('#rich-color').addEventListener('input', event => apply('color', event.target.value));
    document.querySelector('#rich-kind').addEventListener('change', event => {
        const kind = event.target.value;
        restoreSelection(); quill.history.cutoff();
        quill.formatLine(selection.index, selection.length, {header: kind === 'heading' ? 3 : false,
            blockquote: kind === 'quote'}, 'user');
        quill.history.cutoff(); updateToolbar();
    });
    quill.on('selection-change', range => {
        if (range) { selection = {index: range.index, length: range.length}; updateToolbar(); }
    });

    function blocksFromDocument() {
        const blocks = [];
        let runs = [];
        for (const op of quill.getContents().ops) {
            if (typeof op.insert !== 'string') throw new Error('Medien werden noch nicht unterstützt.');
            const attrs = op.attributes || {};
            const parts = op.insert.split('\n');
            parts.forEach((part, index) => {
                if (part) runs.push({text: part, font: fonts.has(attrs.font) ? attrs.font : 'serif',
                    color: color(attrs.color), bold: attrs.bold === true, italic: attrs.italic === true,
                    underline: attrs.underline === true, strike: attrs.strike === true});
                if (index < parts.length - 1) {
                    blocks.push({text: runs.map(run => run.text).join(''),
                        kind: attrs.header ? 'heading' : attrs.blockquote ? 'quote' : 'paragraph',
                        inlineContent: JSON.stringify(runs)});
                    runs = [];
                }
            });
        }
        if (runs.length) throw new Error('Der Beitrag konnte nicht gelesen werden. Bitte versuche es erneut.');
        return blocks;
    }
    function updateCount() {
        document.querySelector('#editor-count').textContent = `${Math.max(0, quill.getLength() - 1).toLocaleString('de-DE')} Zeichen`;
    }
    quill.on('text-change', () => { dirty = true; error.hidden = true; updateCount(); });
    form.querySelector('[name="title"]').addEventListener('input', () => { dirty = true; });
    form.addEventListener('submit', event => {
        try {
            const blocks = blocksFromDocument();
            if (!blocks.some(block => block.text.trim())) throw new Error('Bitte schreibe etwas in deinen Beitrag.');
            if (blocks.length > 40) throw new Error('Dein Beitrag darf höchstens 40 Absätze enthalten.');
            if (blocks.some(block => block.text.length > 5000)) throw new Error('Ein Absatz darf höchstens 5.000 Zeichen enthalten. Bitte teile den Text mit Enter auf.');
            if (blocks.some(block => block.inlineContent.length > 60000 || JSON.parse(block.inlineContent).length > 500))
                throw new Error('Ein Absatz enthält zu viele verschiedene Formatierungen. Bitte teile ihn auf.');
            fields.replaceChildren();
            blocks.forEach((block, index) => Object.entries(block).forEach(([name, value]) => {
                const input = document.createElement('input'); input.type = 'hidden';
                input.name = `blocks[${index}].${name}`; input.value = value; fields.append(input);
            }));
            dirty = false;
        } catch (exception) {
            event.preventDefault(); error.textContent = exception.message; error.hidden = false;
            quill.root.setAttribute('aria-invalid', 'true');
        }
    });
    window.addEventListener('beforeunload', event => {
        if (dirty) { event.preventDefault(); event.returnValue = ''; }
    });
    updateToolbar(); updateCount();
})();
