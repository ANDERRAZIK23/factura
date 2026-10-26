package ma.facture.invoicing.invoice.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.company.Company;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.entity.Invoice;
import ma.facture.invoicing.product.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class InvoicePdfRendererTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    private final FrenchAmountInWordsService words = new FrenchAmountInWordsService();
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(Clock.fixed(NOW, ZoneOffset.UTC));
    private final Company company = new Company("ENT-000001", "Atlas Fournitures SARL",
            "12, boulevard Zerktouni, Casablanca", "+212 5 22 00 00 00",
            "facturation@atlas.example", "001527304000014");

    @Test void rendersReadableMultiPageInvoiceWithRepeatedHeadersAndFooter() throws Exception {
        Invoice invoice = validatedInvoice(55);
        String amount = words.convert(invoice.getTotalIncludingTax());

        byte[] content = renderer.render(invoice, amount, company);
        Path output = Path.of("target", "generated-test-pdfs", "reference-style-invoice.pdf").toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, content);

        assertThat(new String(content, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(content);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String first = extractor.getTextFromPage(1);
            String second = extractor.getTextFromPage(2);
            StringBuilder allPages = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) allPages.append(extractor.getTextFromPage(page));
            assertThat(first).contains("FACTURE N°", "FA-2026-000097", "Atlas Client",
                    "Adresse : 25, rue des Orangers, Rabat", "Tél. : +212 6 00 00 00 00",
                    "ICE 002233445566778", "Désignation", "Prix Unitaire T.T.C")
                    .doesNotContain("Atlas Fournitures SARL");
            assertThat(second).contains("Désignation", "Page 2");
            assertThat(allPages).asString().contains("Montant H.T", "T.V.A", "Taux", "Montant T.T.C",
                    "20%", "ARRETEE LA PRESENTE FACTURE A LA SOMME DE", amount);
        } finally {
            reader.close();
        }
    }

    @Test void rejectsDraftAndEmptyValidatedInvoice() {
        Client client = client();
        Invoice draft = new Invoice(LocalDate.of(2026, 8, 4), client, null, PaymentMethod.CHECK, null, ACTOR);
        assertThatThrownBy(() -> renderer.render(draft, "Zéro dirham", company)).isInstanceOf(InvoicePdfException.class);
        draft.validate("FA-2026-000001", ACTOR, NOW);
        assertThatThrownBy(() -> renderer.render(draft, "Zéro dirham", company)).isInstanceOf(InvoicePdfException.class);
    }

    @Test void rendersOnlyTheBilledClientInTheVisibleHeader() throws Exception {
        Company configuredCompany = new Company("ENT-000001", "My Company", null, null, null, null);
        Instant validatedAt = Instant.parse("2026-08-08T22:26:38.808808Z");
        InvoicePdfRenderer currentInvoiceRenderer = new InvoicePdfRenderer(Clock.fixed(validatedAt, ZoneOffset.UTC));
        Client billedClient = new Client("CLI-000006", "test", "+212671332286", "Belvédère\nApparemment",
                null, "112334455667788", ACTOR);
        Product product = new Product("PRD-000006", "test", "test", new BigDecimal("124.00"),
                new BigDecimal("20.00"), ProductUnit.SERVICE, ACTOR);
        Invoice invoice = new Invoice(LocalDate.of(2026, 8, 8), billedClient, null, PaymentMethod.CHECK, null, ACTOR);
        InvoiceLineInput input = new InvoiceLineInput(BigDecimal.ONE, new BigDecimal("124.00"),
                new BigDecimal("20.00"), BigDecimal.ZERO);
        invoice.addLine(product, input.quantity(), input.unitPriceExcludingTax(), input.vatRate(), input.discountRate());
        invoice.applyCalculation(new InvoiceCalculationService().calculate(List.of(input)));
        invoice.validate("FA-2026-000006", ACTOR, validatedAt);

        byte[] content = currentInvoiceRenderer.render(invoice, words.convert(invoice.getTotalIncludingTax()), configuredCompany);
        Path output = Path.of("target", "generated-test-pdfs", "FA-2026-000006.pdf").toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, content);

        PdfReader reader = new PdfReader(content);
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("test", "Adresse : Belvédère", "Apparemment", "Tél. : +212671332286",
                            "ICE 112334455667788", "148,80")
                    .doesNotContain("test - test", "My Company", "ICE -");
        } finally {
            reader.close();
        }
    }

    private static Invoice validatedInvoice(int lineCount) {
        Client client = client();
        Product product = new Product("PRD-000001", "Rouleau adhésif bleu", "Qualité industrielle - largeur 50 mm",
                new BigDecimal("13.01"), new BigDecimal("20.00"), ProductUnit.ROLL, ACTOR);
        Invoice invoice = new Invoice(LocalDate.of(2026, 8, 4), client, null, PaymentMethod.BANK_TRANSFER, "BC-2026-0042", ACTOR);
        List<InvoiceLineInput> inputs = new ArrayList<>();
        for (int index = 0; index < lineCount; index++) {
            InvoiceLineInput input = new InvoiceLineInput(new BigDecimal("2.000"), new BigDecimal("13.01"),
                    new BigDecimal("20.00"), new BigDecimal("5.00"));
            inputs.add(input);
            invoice.addLine(product, input.quantity(), input.unitPriceExcludingTax(), input.vatRate(), input.discountRate());
        }
        invoice.applyCalculation(new InvoiceCalculationService().calculate(inputs));
        invoice.validate("FA-2026-000097", ACTOR, NOW);
        return invoice;
    }

    private static Client client() {
        return new Client("CLI-000001", "Atlas Client", "+212 6 00 00 00 00", "25, rue des Orangers, Rabat",
                "client@example.com", "002233445566778", ACTOR);
    }
}
