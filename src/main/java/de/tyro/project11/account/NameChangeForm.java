package de.tyro.project11.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NameChangeForm {
    @NotBlank(message = "Bitte gib deinen Namen ein.")
    @Size(max = 80, message = "Dein Name darf höchstens 80 Zeichen enthalten.")
    private String displayName;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value == null ? null : value.strip(); }
}
