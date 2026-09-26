package de.tyro.project11.portal;

import java.util.UUID;

public class TravelDraft {
    private final String id = UUID.randomUUID().toString();
    private final TravelKind kind;
    private final PortalQuestions.Questionnaire questions = PortalQuestions.draw();
    private TravelForm form = new TravelForm();
    private AbsenceDraft.Stage stage = AbsenceDraft.Stage.EDITING;
    private Long submittedId;

    public TravelDraft(TravelKind kind) { this.kind = kind; }
    public String getId() { return id; }
    public TravelKind getKind() { return kind; }
    public PortalQuestions.Questionnaire getQuestions() { return questions; }
    public TravelForm getForm() { return form; }
    public AbsenceDraft.Stage getStage() { return stage; }
    public Long getSubmittedId() { return submittedId; }
    public void edit() { stage = AbsenceDraft.Stage.EDITING; }
    public void update(TravelForm form) { this.form = form; edit(); }
    public void review() { stage = AbsenceDraft.Stage.REVIEW; }
    public void confirm() { stage = AbsenceDraft.Stage.FINAL; }
    public void submitted(long id) { submittedId = id; stage = AbsenceDraft.Stage.SUBMITTED; }
}
