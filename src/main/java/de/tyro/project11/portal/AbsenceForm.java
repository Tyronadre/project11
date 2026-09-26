package de.tyro.project11.portal;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AbsenceForm {
    @NotBlank(message = "A.1: Ihr Name ist der Behörde noch einmal mitzuteilen.")
    @Size(max = 120, message = "A.1: Der Name darf höchstens 120 Zeichen umfassen.")
    private String applicantName;
    @NotNull(message = "A.2: Bitte eine Gruppenaktivität zuordnen.")
    private Long activityId;
    @NotNull(message = "A.3: Bitte das Datum der Gruppenaktivität wiederholen.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate activityDate;
    @NotBlank(message = "A.5: Bitte den Zielort angeben.")
    @Size(max = 200, message = "A.5: Höchstens 200 Zeichen.")
    private String destination;
    @NotBlank(message = "A.6: Begleitpersonen angeben; gegebenenfalls ausdrücklich 'keine'.")
    @Size(max = 500, message = "A.6: Höchstens 500 Zeichen.")
    private String companions;
    @NotBlank(message = "A.7: Ohne Grund keine begründete Abwesenheit.")
    @Size(max = 1000, message = "A.7: Höchstens 1.000 Zeichen.")
    private String reason;
    @NotBlank(message = "B.1: Bitte die abweichende Prioritätensetzung erläutern.")
    @Size(max = 2000, message = "B.1: Höchstens 2.000 Zeichen.")
    private String priorityReason;
    @NotBlank(message = "B.2: Eine ausführliche Begründung ist zwingend erforderlich.")
    @Size(min = 50, max = 6000, message = "B.2: Die ausführliche Begründung muss 50 bis 6.000 Zeichen enthalten.")
    private String detailedReason;
    @NotBlank(message = "A.9: Bitte ein Verkehrsmittel benennen.")
    @Size(max = 120, message = "A.9: Höchstens 120 Zeichen.")
    private String transport;
    @NotBlank(message = "A.10: Referat E besteht auf einem Verpflegungsplan.")
    @Size(max = 1000, message = "A.10: Höchstens 1.000 Zeichen.")
    private String catering;
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

    private String clean(String value) { return value == null ? null : value.strip(); }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String v) { applicantName = clean(v); }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long v) { activityId = v; }
    public LocalDate getActivityDate() { return activityDate; }
    public void setActivityDate(LocalDate v) { activityDate = v; }
    public String getDestination() { return destination; }
    public void setDestination(String v) { destination = clean(v); }
    public String getCompanions() { return companions; }
    public void setCompanions(String v) { companions = clean(v); }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = clean(v); }
    public String getPriorityReason() { return priorityReason; }
    public void setPriorityReason(String v) { priorityReason = clean(v); }
    public String getDetailedReason() { return detailedReason; }
    public void setDetailedReason(String v) { detailedReason = clean(v); }
    public String getTransport() { return transport; }
    public void setTransport(String v) { transport = clean(v); }
    public String getCatering() { return catering; }
    public void setCatering(String v) { catering = clean(v); }
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
