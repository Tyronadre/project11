package de.tyro.project11.profile;

import de.tyro.project11.registration.AppUser;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id private Long id;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private AppUser user;
    @Column(nullable = false, length = 7) private String color = "#52734d";
    private LocalDate birthday;
    @Column(nullable = false, length = 254) private String paypal = "";
    @Column(nullable = false, length = 34) private String iban = "";
    @ElementCollection
    @CollectionTable(name = "profile_answers", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "question_key", length = 30)
    @Column(name = "answer", length = 300, nullable = false)
    private Map<String, String> answers = new LinkedHashMap<>();
    protected UserProfile() {}
    public UserProfile(AppUser user) { this.user = user; }
    public String getColor() { return color; }
    public LocalDate getBirthday() { return birthday; }
    public String getPaypal() { return paypal; }
    public String getIban() { return iban; }
    public Map<String, String> getAnswers() { return Map.copyOf(answers); }
    public void update(ProfileForm form) {
        color = form.getColor(); birthday = form.getBirthday(); paypal = form.getPaypal(); iban = form.getIban();
        answers.clear();
        ProfileQuestions.ALL.forEach(q -> {
            String value = form.getAnswers().getOrDefault(q.key(), "").strip();
            if (!value.isEmpty()) answers.put(q.key(), value);
        });
    }
}
