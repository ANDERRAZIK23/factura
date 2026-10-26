package ma.facture.invoicing.invoice.pdf;

import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.company.Company;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.design.InvoiceDesignSettings;
import ma.facture.invoicing.invoice.design.InvoiceDesignSettings.*;
import ma.facture.invoicing.invoice.design.InvoiceFreeLayout;
import ma.facture.invoicing.invoice.design.InvoiceFreeLayout.*;
import ma.facture.invoicing.common.ValidationException;
import ma.facture.invoicing.invoice.entity.Invoice;
import ma.facture.invoicing.product.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class DesignedInvoicePdfRendererTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private final DesignedInvoicePdfRenderer renderer = new DesignedInvoicePdfRenderer();
    private final FrenchAmountInWordsService words = new FrenchAmountInWordsService();
    private final Company company = new Company("ENT-000001", "Atlas Fournitures SARL", "12, boulevard Zerktouni, Casablanca", "+212 522 00 00 00", "facturation@atlas.example", "001527304000014");

    @ParameterizedTest @EnumSource(Template.class)
    void rendersEachTemplateOnOnePageWithLogoAndFinancialTotals(Template template) throws Exception {
        Invoice invoice = invoice(3);
        var style = settings(template, FontFamily.SANS, 10, PaperFormat.A4, true, "Merci pour votre confiance. RIB : 123 456 789.");
        byte[] pdf = render(invoice, style, logo());
        write("designer-" + template.name().toLowerCase() + ".pdf", pdf);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(reader.getPageSize(1).getWidth()).isEqualTo(595);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("FACTURE", "Atlas Client", "Atlas Fournitures SARL", "PRD-000001", "Description détaillée", "Unité", "Remise", "20%", "88,98", "Arrêtée à la somme", "RIB : 123 456 789.");
            assertThat(reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT).getKeys()).isNotEmpty();
        } finally { reader.close(); }
    }

    @Test void respectsHiddenOptionsWithoutChangingMandatoryTotals() throws Exception {
        Invoice invoice = invoice(3);
        var style = settings(Template.MINIMAL, FontFamily.SERIF, 12, PaperFormat.LETTER, false, "Texte personnalisé");
        byte[] pdf = render(invoice, style, logo());
        write("designer-minimal-hidden.pdf", pdf);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getPageSize(1).getWidth()).isEqualTo(612);
            String text = allText(reader);
            assertThat(text).contains("Total HT", "TVA", "Total TTC", "88,98", "Texte personnalisé")
                    .doesNotContain("PRD-000001", "Description détaillée", "Unité", "20%", "Arrêtée à la somme");
            assertThat(reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT)).isNull();
        } finally { reader.close(); }
    }

    @Test void repeatsHeadersAndFullLongFooterOnEveryPage() throws Exception {
        String footer = "RIB : 123 456 789\nConditions de règlement : paiement à trente jours après réception de la facture.\n" + "Informations complémentaires et coordonnées bancaires. ".repeat(3) + "\nFIN DU PIED DE PAGE";
        assertThat(footer.length()).isLessThanOrEqualTo(300);
        var style = settings(Template.CLASSIC, FontFamily.MONO, 12, PaperFormat.A4, true, footer);
        byte[] pdf = render(invoice(55), style, null);
        write("designer-multipage.pdf", pdf);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThan(2);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            assertThat(extractor.getTextFromPage(2)).contains("Désignation", "Remise", "PRD-000001");
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertThat(extractor.getTextFromPage(page)).contains("FIN DU PIED DE PAGE", "Page " + page);
            }
            assertThat(allText(reader).replaceAll("\\s+", " ")).contains("1 631,30", words.convert(invoice(55).getTotalIncludingTax()));
        } finally { reader.close(); }
    }

    @Test void placesFramesAndTextAtTheirSavedCoordinates() throws Exception {
        byte[] pdf = render(invoice(3), freeSettings(), logo());
        write("designer-free-frames.pdf", pdf);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(allText(reader)).contains("FACTURE", "Atlas Client", "Atlas Fournitures SARL", "88,98", "CADRE PERSONNALISÉ", "Merci pour votre confiance");
            // The rectangle at (100, 700), size (200, 100), uses top-left thousandths of an A4 sheet.
            assertThat(new String(reader.getPageContent(1), java.nio.charset.StandardCharsets.ISO_8859_1))
                    .contains("59.5 168.4 119 84.2 re");
        } finally { reader.close(); }
    }

    @Test void paginatesFreeLayoutWithoutDroppingArticlesAndOnlyPrintsTotalsOnLastPage() throws Exception {
        Invoice invoice = invoice(55);
        byte[] pdf = render(invoice, freeSettings(), null);
        write("designer-free-multipage.pdf", pdf);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThan(2);
            var extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertThat(extractor.getTextFromPage(page)).contains("FACTURE", "Désignation", "Merci pour votre confiance", "Page " + page);
                if (page < reader.getNumberOfPages()) assertThat(extractor.getTextFromPage(page)).doesNotContain("Total TTC", "Arrêtée à la somme");
            }
            String text = allText(reader);
            assertThat(text.split("PRD-000001", -1).length - 1).isEqualTo(55);
            assertThat(text.replaceAll("\\s+", " ")).contains("1 631,30", words.convert(invoice.getTotalIncludingTax()));
        } finally { reader.close(); }
    }

    @Test void rejectsUndersizedFixedBlocksInsteadOfClippingTheirContent() {
        var settings = freeSettings();
        var blocks = settings.layout().blocks().stream().map(block -> block.kind() == Kind.CLIENT
                ? new Block(block.id(), block.kind(), block.x(), block.y(), block.width(), 20, block.backgroundColor(), block.borderColor(), block.borderWidth(), block.textColor(), block.fontSize(), block.textAlignment(), block.text()) : block).toList();
        assertThatThrownBy(() -> render(invoice(3), withLayout(settings, new InvoiceFreeLayout(blocks)), null))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Client").hasMessageContaining("trop petit");
    }

    @Test void rejectsAnArticlesZoneThatCannotFitAnyContent() {
        var settings = freeSettings();
        var blocks = settings.layout().blocks().stream().map(block -> block.kind() == Kind.ITEMS
                ? new Block(block.id(), block.kind(), block.x(), block.y(), block.width(), 20, null, block.borderColor(), 0, block.textColor(), block.fontSize(), block.textAlignment(), "") : block).toList();
        assertThatThrownBy(() -> render(invoice(3), withLayout(settings, new InvoiceFreeLayout(blocks)), null))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Articles");
    }

    @Test void readsPreviouslySavedSettingsWithoutAFreeLayout() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var data = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(InvoiceDesignSettings.defaults());
        data.remove("layout");
        var old = mapper.treeToValue(data, InvoiceDesignSettings.class);
        assertThat(old.layout()).isNull();
        PdfReader reader = new PdfReader(render(invoice(3), old, null));
        try { assertThat(reader.getNumberOfPages()).isEqualTo(1); assertThat(allText(reader)).contains("88,98"); }
        finally { reader.close(); }
    }

    static InvoiceDesignSettings freeSettings() {
        var blocks = new ArrayList<Block>(List.of(
                block(Kind.LOGO, 60, 45, 200, 70), block(Kind.COMPANY, 60, 120, 420, 145),
                block(Kind.TITLE, 580, 45, 360, 110), block(Kind.CLIENT, 560, 200, 380, 150),
                block(Kind.METADATA, 60, 285, 460, 90), block(Kind.ITEMS, 60, 390, 880, 260),
                block(Kind.TOTALS, 530, 680, 410, 155), block(Kind.AMOUNT_WORDS, 60, 845, 880, 65),
                block(Kind.FOOTER, 60, 920, 880, 50)));
        blocks.addFirst(new Block("rectangle", Kind.RECTANGLE, 100, 700, 200, 100, "#eef1f8", "#243c83", 2, "#263247", 10, TextAlignment.LEFT, ""));
        blocks.add(new Block("note", Kind.TEXT, 100, 710, 350, 60, null, "#243c83", 0, "#243c83", 10, TextAlignment.CENTER, "CADRE PERSONNALISÉ"));
        return withLayout(settings(Template.CLASSIC, FontFamily.SANS, 10, PaperFormat.A4, true, "Merci pour votre confiance"), new InvoiceFreeLayout(blocks));
    }
    private static Block block(Kind kind, int x, int y, int width, int height) {
        return new Block(kind.name().toLowerCase(), kind, x, y, width, height, null, "#243c83", 1, "#263247", kind == Kind.FOOTER ? 8 : 10, TextAlignment.LEFT, "");
    }
    private static InvoiceDesignSettings withLayout(InvoiceDesignSettings d, InvoiceFreeLayout layout) {
        return new InvoiceDesignSettings(d.template(), d.accentColor(), d.fontFamily(), d.fontSize(), d.paperFormat(), d.showLogo(), d.logoPosition(), d.clientPosition(), d.showProductCode(), d.showDescription(), d.showUnit(), d.showDiscount(), d.showVat(), d.showAmountInWords(), d.footerText(), layout);
    }

    private byte[] render(Invoice invoice, InvoiceDesignSettings settings, byte[] logo) {
        return renderer.render(invoice, words.convert(invoice.getTotalIncludingTax()), company, settings, logo, false);
    }
    private static InvoiceDesignSettings settings(Template template, FontFamily font, int size, PaperFormat paper, boolean optional, String footer) {
        return new InvoiceDesignSettings(template, "#243c83", font, size, paper, optional, Alignment.LEFT, Alignment.RIGHT, optional, optional, optional, optional, optional, optional, footer);
    }
    private static Invoice invoice(int count) {
        Client client = new Client("CLI-000001", "Atlas Client", "+212 600 00 00 00", "25, rue des Orangers, Rabat", "client@example.com", "002233445566778", ACTOR);
        Product product = new Product("PRD-000001", "Rouleau adhésif bleu", "Description détaillée - largeur 50 mm", new BigDecimal("13.01"), BigDecimal.valueOf(20), ProductUnit.ROLL, ACTOR);
        Invoice invoice = new Invoice(LocalDate.of(2026, 9, 7), client, null, PaymentMethod.BANK_TRANSFER, "BC-2026-0042", ACTOR);
        List<InvoiceLineInput> inputs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            var line = new InvoiceLineInput(BigDecimal.valueOf(2), new BigDecimal("13.01"), BigDecimal.valueOf(20), BigDecimal.valueOf(5));
            invoice.addLine(product, line.quantity(), line.unitPriceExcludingTax(), line.vatRate(), line.discountRate());
            inputs.add(line);
        }
        invoice.applyCalculation(new InvoiceCalculationService().calculate(inputs));
        invoice.validate("FA-2026-000097", ACTOR, Instant.parse("2026-09-07T12:00:00Z"));
        return invoice;
    }
    private static byte[] logo() throws Exception {
        BufferedImage image = new BufferedImage(240, 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 240, 70);
        graphics.setColor(new Color(36, 60, 131)); graphics.setFont(new Font("SansSerif", Font.BOLD, 40)); graphics.drawString("ATLAS", 4, 48); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
    private static void write(String name, byte[] bytes) throws Exception {
        Path path = Path.of("target/generated-test-pdfs", name);
        Files.createDirectories(path.getParent()); Files.write(path, bytes);
    }
    private static String allText(PdfReader reader) throws Exception {
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
        return text.toString();
    }
}
