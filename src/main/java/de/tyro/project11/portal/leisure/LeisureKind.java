package de.tyro.project11.portal.leisure;

import java.util.List;

public enum LeisureKind {
    PERMISSION("antragsberechtigung", "AaEeA", "Antrag auf Erteilung einer Antragsberechtigung", "Die Erlaubnis für genau den Antrag, den Sie gerade stellen."),
    NICKNAME("spitzname", "ASa", "Amtliche Spitznamenanerkennung", "Eine feierliche Urkunde für Ihre völlig inoffizielle Zusatzbezeichnung."),
    ANTICIPATION("vorfreude", "ArV", "Antrag auf rückwirkende Vorfreude", "Das Treffen ist vorbei. Ihre Vorfreude lässt sich noch ordnungsgemäß nachreichen."),
    CERTIFICATE("bescheinigung", "BüNB", "Bescheinigung über das Nichtvorliegen eines Bescheinigungsbedarfs", "Das Dokument für alle, die keines benötigen."),
    JURISDICTION("zustaendigkeit", "ZP", "Zuständigkeitsprüfung", "Wir klären, wer Ihre Angelegenheit weiterreichen darf."),
    WAITING("wartezimmer", "WM", "Digitales Wartezimmer", "Nehmen Sie Platz. Ihre Zeit ist uns ein Verwaltungsvorgang wert.");

    private final String slug, code, title, description;
    LeisureKind(String slug, String code, String title, String description) {
        this.slug = slug; this.code = code; this.title = title; this.description = description;
    }
    public String getSlug() { return slug; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public static List<LeisureKind> forms() { return List.of(PERMISSION, NICKNAME, ANTICIPATION, CERTIFICATE, JURISDICTION); }
    public static LeisureKind fromSlug(String slug) {
        return java.util.Arrays.stream(values()).filter(k -> k.slug.equals(slug)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Dieses Referat wurde nicht gegründet."));
    }
}
