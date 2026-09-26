package de.tyro.project11.profile;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Only typed text and a small formatting allowlist are persisted, never editor HTML. */
public final class BlogInline {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private static final Set<String> FONTS = Set.of("serif", "sans", "mono");
    private BlogInline() {}

    public record Run(String text, String font, String color, boolean bold, boolean italic,
                      boolean underline, boolean strike) {
        boolean valid() {
            return text != null && text.length() <= 5000 && font != null && FONTS.contains(font)
                    && color != null && color.matches("#[0-9a-fA-F]{6}");
        }
    }

    public static List<Run> parse(String content, String plainText) {
        if (content == null || content.length() > 60000) throw new IllegalArgumentException("Invalid inline content");
        try {
            Run[] runs = JSON.readValue(content, Run[].class);
            if (runs == null || runs.length > 500 || Arrays.stream(runs).anyMatch(run -> run == null || !run.valid()))
                throw new IllegalArgumentException("Invalid text formatting");
            if (!Arrays.stream(runs).map(Run::text).collect(Collectors.joining()).equals(plainText))
                throw new IllegalArgumentException("Text and formatting must match");
            return List.of(runs);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid text formatting", exception);
        }
    }
}
