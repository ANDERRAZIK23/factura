package ma.facture.invoicing.invoice.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import ma.facture.invoicing.company.Company;
import ma.facture.invoicing.invoice.entity.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.*;
import java.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class InvoicePdfRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
            .withZone(ZoneOffset.UTC);
    private static final Color BLACK = Color.BLACK;
    private static final Color WHITE = Color.WHITE;

    private final Clock clock;

    public InvoicePdfRenderer(Clock clock) { this.clock = clock; }

    public byte[] render(Invoice invoice, String amountInWords, Company company) {
        if (invoice.getInvoiceNumber() == null) throw new InvoicePdfException("Une facture brouillon ne peut pas être générée en PDF");
        if (invoice.getLines().isEmpty()) throw new InvoicePdfException("Le PDF d’une facture nécessite au moins une ligne");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER, 24, 24, 38, 52);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new FooterEvent(invoice.getInvoiceNumber(), Instant.now(clock)));
            document.addTitle("Facture " + invoice.getInvoiceNumber());
            document.addAuthor(company.getCompanyName());
            document.addSubject("Facture client");
            document.open();
            addHeader(document, invoice);
            addLines(document, invoice);
            addSummary(document, writer, invoice, amountInWords);
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            if (document.isOpen()) document.close();
            throw new InvoicePdfException("Impossible de générer le PDF de la facture", exception);
        }
    }

    private void addHeader(Document document, Invoice invoice) throws DocumentException {
        PdfPTable clientBlock = new PdfPTable(1);
        clientBlock.setWidthPercentage(42);
        clientBlock.setHorizontalAlignment(Element.ALIGN_RIGHT);
        clientBlock.setSpacingBefore(28);
        clientBlock.setKeepTogether(true);

        PdfPCell clientCell = borderless();
        PdfPTable clientIdentity = new PdfPTable(1);
        clientIdentity.setWidthPercentage(100);
        PdfPCell clientName = bordered(value(invoice.getClientNameSnapshot()), font(15, Font.BOLD | Font.UNDERLINE), Element.ALIGN_CENTER, 10);
        clientName.setMinimumHeight(46);
        clientIdentity.addCell(clientName);
        clientIdentity.addCell(clientContact(invoice));
        clientCell.addElement(clientIdentity);
        Paragraph clientIce = new Paragraph("ICE " + value(invoice.getClientIceSnapshot()), font(11, Font.UNDERLINE));
        clientIce.setAlignment(Element.ALIGN_CENTER);
        clientIce.setSpacingBefore(3);
        clientCell.addElement(clientIce);
        clientBlock.addCell(clientCell);
        document.add(clientBlock);

        PdfPTable number = new PdfPTable(new float[]{1.15f, 4f});
        number.setWidthPercentage(100);
        number.setSpacingBefore(26);
        number.addCell(borderless("FACTURE N° :", font(12, Font.BOLD | Font.UNDERLINE), Element.ALIGN_LEFT));
        number.addCell(borderless(invoice.getInvoiceNumber(), font(12, Font.NORMAL), Element.ALIGN_LEFT));
        document.add(number);

        PdfPTable metadata = new PdfPTable(new float[]{1f, 1.05f, 1f});
        metadata.setWidthPercentage(72);
        metadata.setHorizontalAlignment(Element.ALIGN_LEFT);
        metadata.setSpacingBefore(34);
        for (String heading : new String[]{"Date", "Votre Commande", "Mode de règlement"})
            metadata.addCell(bordered(heading, font(11, Font.BOLD), Element.ALIGN_CENTER, 2));
        metadata.addCell(bordered(DATE.format(invoice.getInvoiceDate()), font(10, Font.NORMAL), Element.ALIGN_CENTER, 3));
        metadata.addCell(bordered(empty(invoice.getCustomerOrderReference()), font(10, Font.NORMAL), Element.ALIGN_CENTER, 3));
        metadata.addCell(bordered(payment(invoice), font(10, Font.NORMAL), Element.ALIGN_CENTER, 3));
        document.add(metadata);
    }

    private void addLines(Document document, Invoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4.8f, 1f, 2.65f, 1.4f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(37);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        for (String heading : new String[]{"Désignation", "Quantité", "Prix Unitaire T.T.C", "Montant"})
            table.addCell(bordered(heading, font(11, Font.BOLD), Element.ALIGN_CENTER, 3));

        float minimumHeight = Math.max(23, Math.min(218, 218f / Math.max(1, invoice.getLines().size())));
        for (InvoiceLine line : invoice.getLines()) {
            PdfPCell designation = bordered(designation(line), font(10, Font.NORMAL), Element.ALIGN_LEFT, 12);
            designation.setMinimumHeight(minimumHeight);
            designation.setVerticalAlignment(Element.ALIGN_TOP);
            table.addCell(designation);
            table.addCell(bodyCell(quantity(line.getQuantity()), minimumHeight, Element.ALIGN_CENTER));
            table.addCell(bodyCell(money(unitPriceIncludingTax(line)), minimumHeight, Element.ALIGN_CENTER));
            table.addCell(bodyCell(money(line.getTotalIncludingTax()), minimumHeight, Element.ALIGN_RIGHT));
        }
        document.add(table);
    }

    private void addSummary(Document document, PdfWriter writer, Invoice invoice, String words) throws DocumentException {
        if (writer.getVerticalPosition(true) - document.bottom() < 118) document.add(Chunk.NEXTPAGE);
        PdfPTable totals = new PdfPTable(new float[]{2.35f, 2.45f, .95f, 2.7f, 1.4f});
        totals.setWidthPercentage(100);
        totals.setSpacingBefore(0);
        totals.addCell(bordered("Montant H.T", font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(bordered("T.V.A", font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(bordered("Taux", font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(borderless());
        totals.addCell(bordered("Montant T.T.C", font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(bordered(money(invoice.getTaxableAmount()), font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(bordered(money(invoice.getTotalVat()), font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(bordered(percent(globalVatRate(invoice)), font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        totals.addCell(borderless());
        totals.addCell(bordered(money(invoice.getTotalIncludingTax()), font(9, Font.BOLD), Element.ALIGN_CENTER, 3));
        document.add(totals);

        Paragraph title = new Paragraph("ARRETEE LA PRESENTE FACTURE A LA SOMME DE :", font(10, Font.BOLD | Font.UNDERLINE));
        title.setSpacingBefore(44);
        document.add(title);
        Paragraph amount = new Paragraph(value(words), font(14, Font.NORMAL));
        amount.setSpacingBefore(12);
        document.add(amount);
    }

    private static PdfPCell bodyCell(String text, float minimumHeight, int alignment) {
        PdfPCell cell = bordered(text, font(10, Font.NORMAL), alignment, 10);
        cell.setMinimumHeight(minimumHeight);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    private static PdfPCell clientContact(Invoice invoice) {
        PdfPCell cell = bordered("", font(8.5f, Font.NORMAL), Element.ALIGN_CENTER, 6);
        cell.setMinimumHeight(38);
        Paragraph contact = new Paragraph();
        contact.setAlignment(Element.ALIGN_CENTER);
        contact.setLeading(11);
        String address = empty(invoice.getClientAddressSnapshot());
        String phone = empty(invoice.getClientPhoneSnapshot());
        if (!address.isBlank()) contact.add(new Chunk("Adresse : " + address.strip(), font(8.5f, Font.NORMAL)));
        if (!address.isBlank() && !phone.isBlank()) contact.add(Chunk.NEWLINE);
        if (!phone.isBlank()) contact.add(new Chunk("Tél. : " + phone.strip(), font(8.5f, Font.NORMAL)));
        if (address.isBlank() && phone.isBlank()) contact.add(new Chunk(" ", font(8.5f, Font.NORMAL)));
        cell.addElement(contact);
        return cell;
    }

    private static PdfPCell bordered(String text, Font font, int alignment, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(padding);
        cell.setBorderColor(BLACK);
        cell.setBorderWidth(1.2f);
        cell.setBackgroundColor(WHITE);
        return cell;
    }

    private static PdfPCell borderless() { PdfPCell cell = new PdfPCell(); cell.setBorder(Rectangle.NO_BORDER); return cell; }
    private static PdfPCell borderless(String text, Font font, int alignment) { PdfPCell cell = borderless(); cell.setPhrase(new Phrase(text, font)); cell.setHorizontalAlignment(alignment); return cell; }
    private static Font font(float size, int style) { return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", false, size, style, BLACK); }
    private static String value(String value) { return value == null || value.isBlank() ? "-" : value; }
    private static String empty(String value) { return value == null || value.isBlank() ? "" : value; }
    private static String designation(InvoiceLine line) {
        String name = value(line.getProductNameSnapshot());
        String description = line.getProductDescriptionSnapshot();
        if (description == null || description.isBlank()) return name;
        String normalizedDescription = description.strip();
        return normalizedDescription.equalsIgnoreCase(name) ? name : name + " - " + normalizedDescription;
    }
    private static String payment(Invoice invoice) {
        String method = value(invoice.getPaymentMethodSnapshot());
        return switch (method) {
            case "CASH" -> "espèces";
            case "CHECK" -> "chèque";
            case "BANK_TRANSFER" -> "virement";
            case "CARD" -> "carte";
            case "CREDIT" -> "crédit";
            default -> method.toLowerCase(Locale.FRANCE).replace('_', ' ');
        };
    }
    private static BigDecimal unitPriceIncludingTax(InvoiceLine line) {
        if (line.getQuantity().signum() == 0) return BigDecimal.ZERO;
        return line.getTotalIncludingTax().divide(line.getQuantity(), 2, RoundingMode.HALF_UP);
    }
    private static BigDecimal globalVatRate(Invoice invoice) {
        BigDecimal firstRate = invoice.getLines().getFirst().getVatRate();
        if (invoice.getLines().stream().allMatch(line -> line.getVatRate().compareTo(firstRate) == 0)) return firstRate;
        if (invoice.getTaxableAmount().signum() == 0) return BigDecimal.ZERO;
        return invoice.getTotalVat().multiply(BigDecimal.valueOf(100))
                .divide(invoice.getTaxableAmount(), 2, RoundingMode.HALF_UP);
    }
    private static String money(BigDecimal value) { return decimal("#,##0.00").format(value); }
    private static String quantity(BigDecimal value) { return decimal("#,##0.##").format(value); }
    private static String percent(BigDecimal value) { return decimal("0.##").format(value) + "%"; }
    private static DecimalFormat decimal(String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private static final class FooterEvent extends PdfPageEventHelper {
        private final String invoiceNumber;
        private final String generatedAt;
        private FooterEvent(String invoiceNumber, Instant generatedAt) {
            this.invoiceNumber = invoiceNumber;
            this.generatedAt = DATE_TIME.format(generatedAt);
        }
        @Override public void onEndPage(PdfWriter writer, Document document) {
            Font footer = font(7, Font.NORMAL);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase(invoiceNumber + " - " + generatedAt, footer), document.left(), 24, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), footer), document.right(), 24, 0);
        }
    }
}
