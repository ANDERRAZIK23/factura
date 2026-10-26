package ma.facture.invoicing.sales;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import ma.facture.invoicing.invoice.pdf.InvoicePdfException;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Component
public class QuotationPdfRenderer {
    public byte[] render(Quotation quote) {
        try (var output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 45, 45);
            PdfWriter.getInstance(document, output); document.open();
            var terms = quote.getTerms();
            document.add(new Paragraph("DEVIS " + quote.getQuoteNumber(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22)));
            document.add(new Paragraph("Émis le " + date(quote.getIssueDate()) + " — valable jusqu’au " + date(quote.getValidUntil())));
            if (quote.getStatus().equals("CANCELLED")) document.add(new Paragraph("DEVIS ANNULÉ"));
            document.add(new Paragraph("\n" + party(terms.company())));
            document.add(new Paragraph("\nDestinataire\n" + party(terms.client()) + "\n\n"));
            PdfPTable table = new PdfPTable(new float[]{4, 1, 1.5f, 1, 1, 1.5f}); table.setWidthPercentage(100); table.setHeaderRows(1);
            for (String label : new String[]{"Désignation", "Qté", "PU HT", "Remise %", "TVA %", "Total HT"}) {
                PdfPCell cell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                cell.setPadding(7); cell.setBackgroundColor(new java.awt.Color(235, 238, 245)); table.addCell(cell);
            }
            for (var line : terms.lines()) {
                var a = line.amounts();
                for (String text : new String[]{line.productCode() + "\n" + line.productName(), a.quantity().stripTrailingZeros().toPlainString(), a.unitPriceExcludingTax().toPlainString(), a.discountRate().toPlainString(), a.vatRate().toPlainString(), a.netAmountExcludingTax().toPlainString()}) {
                    PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 9))); cell.setPadding(7); table.addCell(cell);
                }
            }
            document.add(table);
            document.add(new Paragraph("\nNet HT : " + terms.totals().taxableAmount().toPlainString() + " MAD"));
            document.add(new Paragraph("TVA : " + terms.totals().totalVat().toPlainString() + " MAD"));
            document.add(new Paragraph("Total TTC : " + terms.totals().totalIncludingTax().toPlainString() + " MAD", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
            document.close(); return output.toByteArray();
        } catch (java.io.IOException | DocumentException exception) { throw new InvoicePdfException("Impossible de produire le devis", exception); }
    }
    private static String date(java.time.LocalDate date) { return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
    private static String party(CommercialTerms.Party party) {
        return party.name() + (party.address() == null ? "" : "\n" + party.address())
                + (party.ice() == null ? "" : "\nICE : " + party.ice())
                + (party.phone() == null ? "" : "\n" + party.phone()) + (party.email() == null ? "" : "\n" + party.email());
    }
}
