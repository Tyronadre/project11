package de.tyro.project11.portal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ApplicationAnswer {
    @Column(name = "section_name", nullable = false, length = 80)
    private String section;
    @Column(name = "question_text", nullable = false, length = 500)
    private String question;
    @Column(name = "answer_text", nullable = false, length = 6000)
    private String answer;

    protected ApplicationAnswer() {}

    public ApplicationAnswer(String section, String question, String answer) {
        this.section = section;
        this.question = question;
        this.answer = answer;
    }

    public String getSection() { return section; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
}
