package de.tyro.project11.profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
public class BlogForm {
    @NotBlank(message = "Bitte einen Titel eingeben.") @Size(max = 160)
    private String title = "";
    @NotNull @Size(min = 1, max = 40, message = "Ein Beitrag benötigt 1 bis 40 Absätze.")
    private List<@NotNull @Valid BlogBlock> blocks = new ArrayList<>(List.of(new BlogBlock()));
    @AssertTrue(message = "Bitte schreibe etwas in deinen Beitrag.")
    public boolean isContentPresent() {
        return blocks != null && blocks.stream().anyMatch(block -> block != null
                && block.getText() != null && !block.getText().isBlank());
    }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<BlogBlock> getBlocks() { return blocks; }
    public void setBlocks(List<BlogBlock> blocks) { this.blocks = blocks; }
}
