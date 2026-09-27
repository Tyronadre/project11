package de.tyro.project11.portal.leisure;

import jakarta.validation.constraints.*;
import java.util.UUID;

public class LeisureForm {
    @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", message = "Bitte das Formular neu öffnen.")
    private String token = UUID.randomUUID().toString();
    @Size(max = 120, message = "Bitte höchstens 120 Zeichen verwenden.") private String subject = "";
    @Size(max = 2000, message = "Bitte höchstens 2000 Zeichen begründen.") private String explanation = "";
    private Long activityId;
    @NotNull @Size(max = 40) private String enthusiasm = "erheblich";
    public String getToken() { return token; }
    public void setToken(String value) { token = value; }
    public String getSubject() { return subject; }
    public void setSubject(String value) { subject = value == null ? "" : value.strip(); }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { explanation = value == null ? "" : value.strip(); }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long value) { activityId = value; }
    public String getEnthusiasm() { return enthusiasm; }
    public void setEnthusiasm(String value) { enthusiasm = value; }
}
