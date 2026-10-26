package ma.facture.invoicing.invoice.design;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** Coordinates are thousandths of the page, measured from its top left. */
public record InvoiceFreeLayout(@NotNull @Size(min = 9, max = 40) List<@NotNull @Valid Block> blocks) {
    public enum Kind { LOGO, COMPANY, TITLE, CLIENT, METADATA, ITEMS, TOTALS, AMOUNT_WORDS, FOOTER, TEXT, RECTANGLE }
    public enum TextAlignment { LEFT, CENTER, RIGHT }
    public record Block(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String id,
            @NotNull Kind kind,
            @Min(0) @Max(980) int x, @Min(0) @Max(980) int y,
            @Min(20) @Max(1000) int width, @Min(20) @Max(1000) int height,
            @Pattern(regexp = "#[0-9a-fA-F]{6}") String backgroundColor,
            @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String borderColor,
            @Min(0) @Max(5) int borderWidth,
            @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String textColor,
            @Min(8) @Max(32) int fontSize,
            @NotNull TextAlignment textAlignment,
            @NotNull @Size(max = 500) String text) {
        @JsonIgnore @AssertTrue(message = "Le bloc doit rester à l’intérieur de la feuille")
        public boolean isInsidePage() { return x + width <= 1000 && y + height <= 1000; }
    }

    @JsonIgnore @AssertTrue(message = "Conservez un seul exemplaire de chaque bloc de facture et des identifiants uniques")
    public boolean isStructureValid() {
        if (blocks == null || blocks.stream().anyMatch(Objects::isNull)) return true;
        Set<String> ids = new HashSet<>();
        EnumSet<Kind> required = EnumSet.complementOf(EnumSet.of(Kind.TEXT, Kind.RECTANGLE));
        for (Block block : blocks) {
            if (!ids.add(block.id())) return false;
            if (block.kind() != null && block.kind() != Kind.TEXT && block.kind() != Kind.RECTANGLE
                    && !required.remove(block.kind())) return false;
        }
        return required.isEmpty();
    }
}
