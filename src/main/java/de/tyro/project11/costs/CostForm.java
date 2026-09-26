package de.tyro.project11.costs;
import jakarta.validation.constraints.*;
import java.util.UUID;

public class CostForm {
    private boolean selectedOnly;
    private java.util.Set<Long> selectedUserIds = new java.util.HashSet<>();
    public boolean isSelectedOnly() { return selectedOnly; }
    public void setSelectedOnly(boolean value) { selectedOnly = value; }
    public java.util.Set<Long> getSelectedUserIds() { return selectedUserIds; }
    public void setSelectedUserIds(java.util.Set<Long> value) { selectedUserIds = value == null ? new java.util.HashSet<>() : new java.util.HashSet<>(value); }
    @NotBlank(message = "Bitte einen Verwendungszweck angeben.")
    @Size(max = 160, message = "Der Verwendungszweck darf höchstens 160 Zeichen enthalten.")
    private String description = "Eventkosten";
    @NotBlank(message = "Bitte einen Betrag eingeben.") @Size(max = 20)
    private String amount = "";
    @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private String requestKey = UUID.randomUUID().toString();
    private long version = -1;
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value == null ? "" : value.strip(); }
    public String getAmount() { return amount; }
    public void setAmount(String value) { amount = value == null ? "" : value.strip(); }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
}
