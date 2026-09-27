package de.tyro.project11.portal;
import jakarta.validation.constraints.*;
public class ComplaintForm {
    @NotBlank(message = "Bitte benenne dein Beschwerdebegehren.")
    @Size(max = 120, message = "Der Betreff darf höchstens 120 Zeichen enthalten.")
    private String subject = "";
    @NotBlank(message = "Bitte beschreibe deine amtliche Unzufriedenheit.")
    @Size(max = 2000, message = "Die Beschwerde darf höchstens 2.000 Zeichen enthalten.")
    private String text = "";
    private Long parentId;
    public String getSubject() { return subject; }
    public void setSubject(String value) { subject = value == null ? "" : value.strip(); }
    public String getText() { return text; }
    public void setText(String value) { text = value == null ? "" : value.strip(); }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { parentId = value; }
}
