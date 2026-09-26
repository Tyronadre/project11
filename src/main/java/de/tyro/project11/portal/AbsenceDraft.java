package de.tyro.project11.portal;

import java.util.UUID;

/** Session-local workflow; question selection remains stable across corrections and refreshes. */
public class AbsenceDraft {
    public enum Stage { EDITING, REVIEW, FINAL, SUBMITTED }
    private final String id = UUID.randomUUID().toString();
    private final PortalQuestions.Questionnaire questions = PortalQuestions.draw();
    private AbsenceForm form = new AbsenceForm();
    private Stage stage = Stage.EDITING;
    private Long submittedId;

    public String getId() { return id; }
    public PortalQuestions.Questionnaire getQuestions() { return questions; }
    public AbsenceForm getForm() { return form; }
    public Stage getStage() { return stage; }
    public Long getSubmittedId() { return submittedId; }
    public void edit() { stage = Stage.EDITING; }
    public void update(AbsenceForm value) { form = value; stage = Stage.EDITING; }
    public void review() { stage = Stage.REVIEW; }
    public void confirm() { stage = Stage.FINAL; }
    public void submitted(long applicationId) { submittedId = applicationId; stage = Stage.SUBMITTED; }
}
