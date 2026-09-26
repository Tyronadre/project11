package de.tyro.project11.profile;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileForm {
    @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Bitte eine gültige Profilfarbe auswählen.")
    private String color = "#52734d";
    @PastOrPresent(message = "Der Geburtstag darf nicht in der Zukunft liegen.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthday;
    @NotNull @Size(max = 254, message = "PayPal darf höchstens 254 Zeichen enthalten.")
    @Pattern(regexp = "(?:|[^\\s@]+@[^\\s@]+\\.[^\\s@]+|https://(?:www\\.)?paypal\\.me/[A-Za-z0-9_-]+/?)", message = "Bitte eine PayPal-E-Mail-Adresse oder einen paypal.me-Link eingeben.")
    private String paypal = "";
    @NotNull @Size(max = 34) private String iban = "";
    @NotNull @Size(max = 20)
    private Map<String, @NotNull @Size(max = 300, message = "Eine Antwort darf höchstens 300 Zeichen enthalten.") String> answers = new LinkedHashMap<>();
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getPaypal() { return paypal; }
    public void setPaypal(String paypal) { this.paypal = paypal == null ? "" : paypal.strip(); }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban == null ? "" : iban.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT); }
    public Map<String, String> getAnswers() { return answers; }
    public void setAnswers(Map<String, String> answers) { this.answers = answers; }
    @AssertTrue(message = "Bitte eine gültige IBAN einschließlich Prüfziffer eingeben.")
    public boolean isIbanValid() {
        if (iban.isEmpty()) return true;
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char c : rearranged.toCharArray()) {
            int value = Character.isDigit(c) ? c - '0' : c - 'A' + 10;
            remainder = (remainder * (value < 10 ? 10 : 100) + value) % 97;
        }
        return remainder == 1;
    }
}
