package de.tyro.project11.profile;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/** Structured text, never executable HTML. New block types can be added for media later. */
@Embeddable
public class BlogBlock {
    @NotNull @Size(max = 5000)
    @Column(name = "body_text", nullable = false, length = 5000) private String text = "";
    @NotNull @Pattern(regexp = "serif|sans|mono")
    @Column(nullable = false, length = 10) private String font = "serif";
    @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @Column(nullable = false, length = 7) private String color = "#203c32";
    @NotNull @Pattern(regexp = "paragraph|heading|quote")
    @Column(name = "block_kind", nullable = false, length = 12) private String kind = "paragraph";
    private boolean bold;
    private boolean italic;
    // Nullable for posts saved before selection-based formatting was introduced.
    @Size(max = 60000)
    @Column(name = "inline_content", length = 60000) private String inlineContent;

    public String getInlineContent() { return inlineContent; }
    public void setInlineContent(String inlineContent) { this.inlineContent = inlineContent; }
    @AssertTrue(message = "Die Textformatierung ist ungültig. Bitte den Beitrag erneut prüfen.")
    public boolean isInlineContentValid() {
        if (inlineContent == null || inlineContent.isEmpty()) return true;
        try { BlogInline.parse(inlineContent, text); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
    public java.util.List<BlogInline.Run> getRuns() {
        if (inlineContent != null && !inlineContent.isEmpty() && isInlineContentValid())
            return BlogInline.parse(inlineContent, text);
        // Also safe when redisplaying a rejected form.
        return java.util.List.of(new BlogInline.Run(text == null ? "" : text,
                java.util.Set.of("serif", "sans", "mono").contains(font == null ? "" : font) ? font : "serif",
                color != null && color.matches("#[0-9a-fA-F]{6}") ? color : "#203c32", bold, italic, false, false));
    }
    public BlogBlock() {}
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getFont() { return font; }
    public void setFont(String font) { this.font = font; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }
}
