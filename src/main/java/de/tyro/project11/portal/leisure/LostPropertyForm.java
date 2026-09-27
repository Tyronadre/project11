package de.tyro.project11.portal.leisure;

import jakarta.validation.constraints.*;

public class LostPropertyForm {
    @NotNull(message = "Bitte Verlust oder Fund auswählen.") private LostProperty.Kind kind = LostProperty.Kind.LOST;
    @NotBlank(message = "Was ist abhandengekommen oder aufgetaucht?")
    @Size(max = 120, message = "Die Bezeichnung darf höchstens 120 Zeichen enthalten.") private String subject = "";
    @NotBlank(message = "Bitte die Fundumstände oder den Verlust beschreiben.")
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen enthalten.") private String description = "";
    private Long parentId;
    public LostProperty.Kind getKind() { return kind; }
    public void setKind(LostProperty.Kind value) { kind = value; }
    public String getSubject() { return subject; }
    public void setSubject(String value) { subject = value == null ? "" : value.strip(); }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value == null ? "" : value.strip(); }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { parentId = value; }
}
