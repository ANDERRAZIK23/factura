package ma.facture.invoicing.invoice.design;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record InvoiceDesignSettings(
        @NotNull Template template,
        @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Choisissez une couleur hexadécimale valide") String accentColor,
        @NotNull FontFamily fontFamily,
        @Min(9) @Max(12) int fontSize,
        @NotNull PaperFormat paperFormat,
        boolean showLogo,
        @NotNull Alignment logoPosition,
        @NotNull Alignment clientPosition,
        boolean showProductCode,
        boolean showDescription,
        boolean showUnit,
        boolean showDiscount,
        boolean showVat,
        boolean showAmountInWords,
        @NotNull @Size(max = 300)
        @Pattern(regexp = "[^\\r\\n]*(?:\\r?\\n[^\\r\\n]*){0,3}", message = "Le pied de page est limité à quatre lignes") String footerText,
        @Valid InvoiceFreeLayout layout) {
    // Existing saved templates have no layout and retain their flowing pagination.
    public InvoiceDesignSettings(Template template, String accentColor, FontFamily fontFamily, int fontSize,
            PaperFormat paperFormat, boolean showLogo, Alignment logoPosition, Alignment clientPosition,
            boolean showProductCode, boolean showDescription, boolean showUnit, boolean showDiscount,
            boolean showVat, boolean showAmountInWords, String footerText) {
        this(template, accentColor, fontFamily, fontSize, paperFormat, showLogo, logoPosition, clientPosition,
                showProductCode, showDescription, showUnit, showDiscount, showVat, showAmountInWords, footerText, null);
    }
    public enum Template { CLASSIC, MODERN, MINIMAL }
    public enum FontFamily { SANS, SERIF, MONO }
    public enum PaperFormat { A4, LETTER }
    public enum Alignment { LEFT, RIGHT }
    public static InvoiceDesignSettings defaults() {
        return new InvoiceDesignSettings(Template.MODERN, "#3346a8", FontFamily.SANS, 10, PaperFormat.A4,
                true, Alignment.LEFT, Alignment.RIGHT, true, true, true, false, true, true, "");
    }
}
