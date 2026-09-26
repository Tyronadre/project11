package de.tyro.project11.portal;

import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TravelForm {
    private Map<String, String> values = new LinkedHashMap<>();
    public Map<String, String> getValues() { return values; }
    public void setValues(Map<String, String> values) { this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values); }
    public String value(String key) { String value = values.get(key); return value == null ? "" : value.strip(); }
    private String clean(String value) { return value == null ? null : value.strip(); }
    @Size(min = 3, max = 3, message = "C: Genau drei ergänzende Angaben sind erforderlich.")
    private List<@NotBlank(message = "C: Alle drei ergänzenden Angaben sind Pflichtfelder.")
            @Size(max = 300, message = "C: Höchstens 300 Zeichen pro Angabe.") String> personalAnswers = new ArrayList<>(List.of("", "", ""));
    @NotBlank(message = "D.1: Bitte den Wissensnachweis erbringen.")
    @Size(max = 120)
    private String knowledgeAnswer;
    @NotBlank(message = "D.2: Eine Stellungnahme zur hypothetischen Lage fehlt.")
    @Size(min = 20, max = 3000, message = "D.2: Bitte 20 bis 3.000 Zeichen zur hypothetischen Lage verfassen.")
    private String absurdAnswer;
    @NotBlank(message = "D.3: Ihre soziale Loyalität wurde noch nicht eingeordnet.")
    @Size(max = 200)
    private String loyaltyAnswer;
    @Size(max = 1000, message = "D.4: Höchstens 1.000 Zeichen.")
    private String auditAnswer = "";
    @AssertTrue(message = "E.1: Bitte Ihre Angaben bestätigen.")
    private boolean confirmedDetails;
    @AssertTrue(message = "E.2: Bitte die Bestätigung Ihrer Angaben bestätigen.")
    private boolean confirmedConfirmation;
    @AssertTrue(message = "E.3: Bitte bestätigen, dass Sie die Bestätigung verstanden haben.")
    private boolean understoodConfirmation;
    @AssertTrue(message = "E.4: Die drei Bestätigungen müssen abschließend bestätigt werden.")
    private boolean confirmedAll;
    @AssertTrue(message = "E.5: Bitte die Allgemeinen Gruppenbedingungen ausdrücklich akzeptieren.")
    private boolean acceptedTerms;

    public List<String> getPersonalAnswers() { return personalAnswers; }
    public void setPersonalAnswers(List<String> v) { personalAnswers = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public String getKnowledgeAnswer() { return knowledgeAnswer; }
    public void setKnowledgeAnswer(String v) { knowledgeAnswer = clean(v); }
    public String getAbsurdAnswer() { return absurdAnswer; }
    public void setAbsurdAnswer(String v) { absurdAnswer = clean(v); }
    public String getLoyaltyAnswer() { return loyaltyAnswer; }
    public void setLoyaltyAnswer(String v) { loyaltyAnswer = clean(v); }
    public String getAuditAnswer() { return auditAnswer; }
    public void setAuditAnswer(String v) { auditAnswer = v == null ? "" : v.strip(); }
    public boolean isConfirmedDetails() { return confirmedDetails; }
    public void setConfirmedDetails(boolean v) { confirmedDetails = v; }
    public boolean isConfirmedConfirmation() { return confirmedConfirmation; }
    public void setConfirmedConfirmation(boolean v) { confirmedConfirmation = v; }
    public boolean isUnderstoodConfirmation() { return understoodConfirmation; }
    public void setUnderstoodConfirmation(boolean v) { understoodConfirmation = v; }
    public boolean isAcceptedTerms() { return acceptedTerms; }
    public void setAcceptedTerms(boolean v) { acceptedTerms = v; }
    public boolean isConfirmedAll() { return confirmedAll; }
    public void setConfirmedAll(boolean v) { confirmedAll = v; }
}
