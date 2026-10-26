package ma.facture.invoicing.invoice.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import ma.facture.invoicing.company.Company;
import ma.facture.invoicing.invoice.design.InvoiceDesignSettings;
import ma.facture.invoicing.invoice.design.InvoiceFreeLayout.Block;
import ma.facture.invoicing.invoice.design.InvoiceFreeLayout.Kind;
import ma.facture.invoicing.common.ValidationException;
import ma.facture.invoicing.invoice.entity.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.*;
import java.text.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

@Component
public class DesignedInvoicePdfRenderer {
    public byte[] render(Invoice invoice, String words, Company company, InvoiceDesignSettings settings, byte[] logo, boolean preview) {
        if (invoice.getInvoiceNumber() == null || invoice.getLines().isEmpty())
            throw new InvoicePdfException("La facture doit être numérotée et contenir au moins une ligne");
        return new Layout(settings).render(invoice, words, company, logo, preview);
    }

    private static final class Layout {
        private final InvoiceDesignSettings style;
        private final Color accent;
        private final String family;
        private Layout(InvoiceDesignSettings style) {
            this.style = style;
            accent = Color.decode(style.accentColor());
            family = switch (style.fontFamily()) {
                case SANS -> FontFactory.HELVETICA;
                case SERIF -> FontFactory.TIMES_ROMAN;
                case MONO -> FontFactory.COURIER;
            };
        }

        private byte[] render(Invoice invoice, String words, Company company, byte[] logo, boolean preview) {
            if (style.layout() != null) return renderFree(invoice, words, company, logo, preview);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Rectangle pageSize = style.paperFormat() == InvoiceDesignSettings.PaperFormat.A4 ? PageSize.A4 : PageSize.LETTER;
            float footerHeight = footerHeight(pageSize.getWidth() - 72);
            Document document = new Document(pageSize, 36, 36, 36, Math.max(70, footerHeight + 54));
            try {
                PdfWriter writer = PdfWriter.getInstance(document, output);
                writer.setPageEvent(new PdfPageEventHelper() {
                    @Override public void onEndPage(PdfWriter writer, Document document) {
                        ColumnText footer = new ColumnText(writer.getDirectContent());
                        footer.setSimpleColumn(document.left(), 30, document.right(), 30 + footerHeight);
                        footer.addElement(new Paragraph(style.footerText(), font(8, Font.NORMAL, Color.DARK_GRAY)));
                        try { footer.go(); }
                        catch (DocumentException exception) { throw new InvoicePdfException("Impossible de dessiner le pied de page", exception); }
                        ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                                new Phrase(invoice.getInvoiceNumber() + (preview ? " - DONNÉES D’EXEMPLE" : ""), font(8, Font.NORMAL, Color.GRAY)), document.left(), 18, 0);
                        ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                                new Phrase("Page " + writer.getPageNumber(), font(8, Font.NORMAL, Color.GRAY)), document.right(), 18, 0);
                    }
                });
                document.addTitle(preview ? "Aperçu de facture" : "Facture " + invoice.getInvoiceNumber());
                document.addAuthor(company.getCompanyName());
                document.open();
                header(document, invoice, company, logo, preview);
                lines(document, invoice);
                totals(document, invoice, words);
                document.close();
                return output.toByteArray();
            } catch (Exception exception) {
                if (document.isOpen()) document.close();
                if (exception instanceof InvoicePdfException pdf) throw pdf;
                throw new InvoicePdfException("Impossible de générer la facture personnalisée", exception);
            }
        }

        private float footerHeight(float width) {
            ColumnText measure = new ColumnText(null);
            measure.setSimpleColumn(0, 0, width, 300);
            measure.addElement(new Paragraph(style.footerText(), font(8, Font.NORMAL, Color.DARK_GRAY)));
            try {
                if (ColumnText.hasMoreText(measure.go(true)))
                    throw new InvoicePdfException("Le pied de page est trop long");
                return 300 - measure.getYLine() + 4;
            } catch (DocumentException exception) {
                throw new InvoicePdfException("Impossible de mesurer le pied de page", exception);
            }
        }

        private byte[] renderFree(Invoice invoice, String words, Company company, byte[] logo, boolean preview) {
            Rectangle page = style.paperFormat() == InvoiceDesignSettings.PaperFormat.A4 ? PageSize.A4 : PageSize.LETTER;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(page, 0, 0, 0, 0);
            try {
                PdfWriter writer = PdfWriter.getInstance(document, output);
                document.addTitle(preview ? "Aperçu de facture" : "Facture " + invoice.getInvoiceNumber());
                document.addAuthor(company.getCompanyName());
                document.open();
                writer.setPageEmpty(false);
                Block items = style.layout().blocks().stream().filter(block -> block.kind() == Kind.ITEMS).findFirst().orElseThrow();
                PdfPTable table = lineTable(invoice);
                table.setSpacingBefore(0);
                table.setSpacingAfter(0);
                table.setWidthPercentage(100);
                table.setKeepTogether(false);
                table.setSplitRows(false);
                checkTable(table, items, page, true);
                ColumnText articles = new ColumnText(writer.getDirectContent());
                articles.addElement(table);
                boolean more;
                do {
                    position(articles, items, page);
                    boolean last = !ColumnText.hasMoreText(ColumnText.duplicate(articles).go(true));
                    for (Block block : style.layout().blocks()) {
                        if (visible(block, last)) frame(writer.getDirectContentUnder(), block, page);
                    }
                    float top = articles.getYLine();
                    more = ColumnText.hasMoreText(articles.go());
                    if (more && articles.getYLine() >= top - .1f)
                        throw new ValidationException("Agrandissez le bloc Articles : aucune ligne ne tient dans sa hauteur.");
                    for (Block block : style.layout().blocks()) {
                        if (visible(block, last) && block.kind() != Kind.ITEMS && block.kind() != Kind.RECTANGLE)
                            drawBlock(writer, block, page, invoice, words, company, logo);
                    }
                    ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase((preview ? "DONNÉES D’EXEMPLE · " : "") + "Page " + writer.getPageNumber(),
                                    font(7, Font.NORMAL, Color.GRAY)), page.getWidth() - 12, 7, 0);
                    writer.setPageEmpty(false);
                    if (more) document.newPage();
                } while (more);
                document.close();
                return output.toByteArray();
            } catch (Exception exception) {
                if (document.isOpen()) document.close();
                if (exception instanceof ValidationException validation) throw validation;
                throw new InvoicePdfException("Impossible de générer la facture en mise en page libre", exception);
            }
        }

        private boolean visible(Block block, boolean last) {
            return switch (block.kind()) {
                case LOGO -> style.showLogo();
                case AMOUNT_WORDS -> last && style.showAmountInWords();
                case TOTALS -> last;
                default -> true;
            };
        }

        private void frame(PdfContentByte canvas, Block block, Rectangle page) {
            float x = block.x() * page.getWidth() / 1000;
            float y = page.getHeight() * (1000 - block.y() - block.height()) / 1000;
            canvas.saveState();
            if (block.backgroundColor() != null) canvas.setColorFill(Color.decode(block.backgroundColor()));
            canvas.setColorStroke(Color.decode(block.borderColor()));
            canvas.setLineWidth(block.borderWidth());
            canvas.rectangle(x, y, block.width() * page.getWidth() / 1000, block.height() * page.getHeight() / 1000);
            if (block.backgroundColor() != null && block.borderWidth() > 0) canvas.fillStroke();
            else if (block.backgroundColor() != null) canvas.fill();
            else if (block.borderWidth() > 0) canvas.stroke();
            else canvas.newPath();
            canvas.restoreState();
        }

        private void position(ColumnText column, Block block, Rectangle page) {
            float padding = 5 + block.borderWidth();
            if (block.width() * page.getWidth() / 1000 <= padding * 2 || block.height() * page.getHeight() / 1000 <= padding * 2)
                throw new ValidationException("Agrandissez le bloc « " + blockLabel(block.kind()) + " » pour laisser de la place au contenu.");
            column.setSimpleColumn(block.x() * page.getWidth() / 1000 + padding,
                    (1000 - block.y() - block.height()) * page.getHeight() / 1000 + padding,
                    (block.x() + block.width()) * page.getWidth() / 1000 - padding,
                    (1000 - block.y()) * page.getHeight() / 1000 - padding);
        }

        private void drawBlock(PdfWriter writer, Block block, Rectangle page, Invoice invoice, String words,
                Company company, byte[] logo) throws Exception {
            ColumnText column = new ColumnText(writer.getDirectContent());
            position(column, block, page);
            Color color = Color.decode(block.textColor());
            switch (block.kind()) {
                case LOGO -> {
                    if (logo == null) return;
                    float padding = 5 + block.borderWidth();
                    float width = block.width() * page.getWidth() / 1000 - 2 * padding;
                    float height = block.height() * page.getHeight() / 1000 - 2 * padding;
                    if (width <= 0 || height <= 0) throw new ValidationException("Agrandissez le bloc Logo.");
                    Image image = Image.getInstance(logo);
                    image.scaleToFit(width, height);
                    float offset = switch (block.textAlignment()) {
                        case LEFT -> 0; case CENTER -> (width - image.getScaledWidth()) / 2; case RIGHT -> width - image.getScaledWidth();
                    };
                    image.setAbsolutePosition(block.x() * page.getWidth() / 1000 + padding + offset,
                            (1000 - block.y()) * page.getHeight() / 1000 - padding - image.getScaledHeight());
                    writer.getDirectContent().addImage(image);
                    return;
                }
                case COMPANY -> {
                    column.addElement(paragraph(company.getCompanyName(), block, color, true));
                    column.addElement(paragraph(join(company.getAddress(), company.getPhone(), company.getEmail(),
                            company.getIce() == null ? null : "ICE : " + company.getIce()), block, color, false));
                }
                case TITLE -> {
                    column.addElement(paragraph("FACTURE", block, color, true));
                    column.addElement(paragraph(invoice.getInvoiceNumber(), block, color, false));
                }
                case CLIENT -> {
                    column.addElement(paragraph("FACTURÉ À", block, color, true));
                    column.addElement(paragraph(join(invoice.getClientNameSnapshot(), invoice.getClientAddressSnapshot(),
                            invoice.getClientPhoneSnapshot(), invoice.getClientIceSnapshot() == null ? null : "ICE : " + invoice.getClientIceSnapshot()), block, color, false));
                }
                case METADATA -> column.addElement(paragraph("Date : " + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(invoice.getInvoiceDate())
                        + "\nRéf. client : " + (invoice.getCustomerOrderReference() == null ? "-" : invoice.getCustomerOrderReference())
                        + "\nPaiement : " + payment(invoice.getPaymentMethodSnapshot()), block, color, false));
                case TOTALS -> {
                    PdfPTable totals = totalsTable(invoice);
                    totals.setWidthPercentage(100);
                    totals.setKeepTogether(false);
                    checkTable(totals, block, page, false);
                    column.addElement(totals);
                }
                case AMOUNT_WORDS -> column.addElement(paragraph("Arrêtée à la somme de : " + words, block, color, false));
                case FOOTER -> column.addElement(paragraph(style.footerText(), block, color, false));
                case TEXT -> {
                    if (!java.nio.charset.Charset.forName("windows-1252").newEncoder().canEncode(block.text()))
                        throw new ValidationException("Le texte libre doit utiliser des caractères latins compatibles avec la police PDF.");
                    column.addElement(paragraph(block.text(), block, color, false));
                }
                default -> throw new IllegalArgumentException("Bloc inattendu : " + block.kind());
            }
            if (ColumnText.hasMoreText(ColumnText.duplicate(column).go(true)))
                throw new ValidationException("Le bloc « " + blockLabel(block.kind()) + " » est trop petit. Agrandissez-le pour afficher tout son contenu.");
            column.go();
        }

        private Paragraph paragraph(String text, Block block, Color color, boolean bold) {
            Paragraph paragraph = new Paragraph(text, font(block.fontSize(), bold ? Font.BOLD : Font.NORMAL, color));
            paragraph.setLeading(block.fontSize() * 1.25f);
            paragraph.setAlignment(switch (block.textAlignment()) { case LEFT -> Element.ALIGN_LEFT; case CENTER -> Element.ALIGN_CENTER; case RIGHT -> Element.ALIGN_RIGHT; });
            return paragraph;
        }

        private void checkTable(PdfPTable table, Block block, Rectangle page, boolean paginate) {
            float padding = 2 * (5 + block.borderWidth());
            float width = block.width() * page.getWidth() / 1000 - padding;
            float height = block.height() * page.getHeight() / 1000 - padding;
            if (width <= 0 || height <= 0) throw new ValidationException("Agrandissez le bloc « " + blockLabel(block.kind()) + " ».");
            table.setTotalWidth(width);
            table.calculateHeights(true);
            if (paginate) {
                for (int index = table.getHeaderRows(); index < table.size(); index++) {
                    if (table.getHeaderHeight() + table.getRowHeight(index) > height)
                        throw new ValidationException("Agrandissez le bloc Articles : une ligne et son en-tête dépassent la zone disponible.");
                }
            } else if (table.getTotalHeight() > height) {
                throw new ValidationException("Le bloc « " + blockLabel(block.kind()) + " » est trop petit. Agrandissez-le pour afficher tout son contenu.");
            }
        }

        private String blockLabel(Kind kind) {
            return switch (kind) {
                case COMPANY -> "Entreprise"; case TITLE -> "Titre et numéro"; case CLIENT -> "Client";
                case METADATA -> "Informations"; case TOTALS -> "Totaux"; case AMOUNT_WORDS -> "Montant en lettres";
                case FOOTER -> "Pied de page"; case TEXT -> "Texte libre"; case ITEMS -> "Articles"; default -> kind.name();
            };
        }

        private void header(Document document, Invoice invoice, Company company, byte[] logo, boolean preview) throws Exception {
            PdfPTable header = new PdfPTable(new float[]{1, 1});
            header.setWidthPercentage(100);
            PdfPCell identity = plain();
            if (style.showLogo() && logo != null) {
                Image image = Image.getInstance(logo);
                image.scaleToFit(120, 60);
                image.setAlignment(style.logoPosition() == InvoiceDesignSettings.Alignment.LEFT ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                identity.addElement(image);
            }
            Paragraph companyName = new Paragraph(company.getCompanyName(), font(16, Font.BOLD, accent));
            companyName.setSpacingBefore(8);
            identity.addElement(companyName);
            identity.addElement(new Paragraph(join(company.getAddress(), company.getPhone(), company.getEmail()), font(style.fontSize(), Font.NORMAL, Color.DARK_GRAY)));
            if (company.getIce() != null) identity.addElement(new Paragraph("ICE : " + company.getIce(), font(style.fontSize(), Font.NORMAL, Color.DARK_GRAY)));
            PdfPCell title = plain();
            title.setHorizontalAlignment(Element.ALIGN_RIGHT);
            boolean band = style.template() == InvoiceDesignSettings.Template.MODERN;
            if (band) { title.setBackgroundColor(accent); title.setPadding(16); }
            Color titleColor = band ? contrast(accent) : accent;
            Paragraph heading = new Paragraph("FACTURE", font(25, Font.BOLD, titleColor));
            heading.setAlignment(Element.ALIGN_RIGHT);
            title.addElement(heading);
            Paragraph number = new Paragraph(invoice.getInvoiceNumber(), font(12, Font.BOLD, titleColor));
            number.setAlignment(Element.ALIGN_RIGHT);
            number.setSpacingBefore(10);
            title.addElement(number);
            if (preview) {
                Paragraph example = new Paragraph("Données d’exemple", font(9, Font.NORMAL, titleColor));
                example.setAlignment(Element.ALIGN_RIGHT);
                title.addElement(example);
            }
            if (style.logoPosition() == InvoiceDesignSettings.Alignment.LEFT) { header.addCell(identity); header.addCell(title); }
            else { header.addCell(title); header.addCell(identity); }
            document.add(header);

            PdfPTable recipient = new PdfPTable(1);
            recipient.setWidthPercentage(50);
            recipient.setHorizontalAlignment(style.clientPosition() == InvoiceDesignSettings.Alignment.LEFT ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            recipient.setSpacingBefore(16);
            recipient.setKeepTogether(true);
            PdfPCell client = plain();
            client.setPadding(9);
            if (style.template() != InvoiceDesignSettings.Template.MINIMAL) client.setBackgroundColor(new Color(245, 247, 250));
            client.addElement(new Paragraph("FACTURÉ À", font(8, Font.BOLD, accent)));
            client.addElement(new Paragraph(invoice.getClientNameSnapshot(), font(13, Font.BOLD, Color.BLACK)));
            client.addElement(new Paragraph(join(invoice.getClientAddressSnapshot(), invoice.getClientPhoneSnapshot()), font(style.fontSize(), Font.NORMAL, Color.DARK_GRAY)));
            if (invoice.getClientIceSnapshot() != null) client.addElement(new Paragraph("ICE : " + invoice.getClientIceSnapshot(), font(style.fontSize(), Font.NORMAL, Color.DARK_GRAY)));
            recipient.addCell(client);
            document.add(recipient);

            PdfPTable metadata = new PdfPTable(3);
            metadata.setWidthPercentage(100);
            metadata.setSpacingBefore(16);
            metadata.addCell(meta("Date de facture", DateTimeFormatter.ofPattern("dd/MM/yyyy").format(invoice.getInvoiceDate())));
            metadata.addCell(meta("Référence client", invoice.getCustomerOrderReference() == null ? "-" : invoice.getCustomerOrderReference()));
            metadata.addCell(meta("Mode de paiement", payment(invoice.getPaymentMethodSnapshot())));
            document.add(metadata);
        }

        private PdfPCell meta(String label, String value) {
            PdfPCell cell = plain();
            cell.addElement(new Paragraph(label, font(8, Font.NORMAL, Color.GRAY)));
            cell.addElement(new Paragraph(value, font(style.fontSize(), Font.BOLD, Color.DARK_GRAY)));
            return cell;
        }

        private void lines(Document document, Invoice invoice) throws DocumentException {
            document.add(lineTable(invoice));
        }

        private PdfPTable lineTable(Invoice invoice) {
            var headings = new ArrayList<String>();
            var widths = new ArrayList<Float>();
            headings.add("Désignation"); widths.add(3.5f);
            headings.add("Qté"); widths.add(.85f);
            if (style.showUnit()) { headings.add("Unité"); widths.add(1.4f); }
            headings.add("Prix HT"); widths.add(1.5f);
            if (style.showDiscount()) { headings.add("Remise"); widths.add(1.2f); }
            if (style.showVat()) { headings.add("TVA"); widths.add(.9f); }
            headings.add("Total HT"); widths.add(1.6f);
            float[] proportions = new float[widths.size()];
            for (int i = 0; i < proportions.length; i++) proportions[i] = widths.get(i);
            PdfPTable table = new PdfPTable(proportions);
            table.setWidthPercentage(100);
            table.setSpacingBefore(18);
            table.setSpacingAfter(12);
            table.setHeaderRows(1);
            table.setSplitLate(false);
            for (int i = 0; i < headings.size(); i++) table.addCell(cell(headings.get(i), true, i == 0));
            for (InvoiceLine line : invoice.getLines()) {
                String name = line.getProductNameSnapshot();
                if (style.showProductCode()) name = line.getProductCodeSnapshot() + "\n" + name;
                if (style.showDescription() && line.getProductDescriptionSnapshot() != null) name += "\n" + line.getProductDescriptionSnapshot();
                table.addCell(cell(name, false, true));
                table.addCell(cell(number(line.getQuantity(), "#,##0.###"), false, false));
                if (style.showUnit()) table.addCell(cell(unit(line.getUnitSnapshot()), false, false));
                table.addCell(cell(money(line.getUnitPriceExcludingTax()), false, false));
                if (style.showDiscount()) table.addCell(cell(number(line.getDiscountRate(), "0.##") + "%", false, false));
                if (style.showVat()) table.addCell(cell(number(line.getVatRate(), "0.##") + "%", false, false));
                table.addCell(cell(money(line.getNetAmountExcludingTax()), false, false));
            }
            return table;
        }

        private void totals(Document document, Invoice invoice, String words) throws DocumentException {
            document.add(totalsTable(invoice));
            if (style.showAmountInWords()) {
                Paragraph amount = new Paragraph("Arrêtée à la somme de : " + words, font(style.fontSize(), Font.NORMAL, Color.DARK_GRAY));
                amount.setSpacingBefore(20);
                amount.setKeepTogether(true);
                document.add(amount);
            }
        }

        private PdfPTable totalsTable(Invoice invoice) {
            PdfPTable table = new PdfPTable(new float[]{1.7f, 1});
            table.setWidthPercentage(50);
            table.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.setKeepTogether(true);
            total(table, "Sous-total HT", invoice.getSubtotalExcludingTax(), false);
            if (invoice.getTotalDiscount().signum() > 0) total(table, "Remises", invoice.getTotalDiscount(), false);
            total(table, "Total HT", invoice.getTaxableAmount(), false);
            total(table, "TVA", invoice.getTotalVat(), false);
            total(table, "Total TTC · MAD", invoice.getTotalIncludingTax(), true);
            return table;
        }
        private void total(PdfPTable table, String label, BigDecimal amount, boolean emphasis) {
            table.addCell(cell(label, emphasis, true));
            table.addCell(cell(money(amount), emphasis, false));
        }
        private PdfPCell cell(String text, boolean heading, boolean left) {
            boolean band = heading && style.template() == InvoiceDesignSettings.Template.MODERN;
            PdfPCell cell = new PdfPCell(new Phrase(text, font(style.fontSize(), heading ? Font.BOLD : Font.NORMAL, band ? contrast(accent) : Color.DARK_GRAY)));
            cell.setHorizontalAlignment(left ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(6);
            cell.setBorder(style.template() == InvoiceDesignSettings.Template.CLASSIC ? Rectangle.BOX : Rectangle.BOTTOM);
            cell.setBorderWidth(.5f);
            cell.setBorderColor(heading ? accent : new Color(220, 225, 232));
            if (heading) cell.setBackgroundColor(band ? accent : new Color(245, 247, 250));
            return cell;
        }
        private PdfPCell plain() { PdfPCell cell = new PdfPCell(); cell.setBorder(Rectangle.NO_BORDER); cell.setPadding(4); return cell; }
        private Font font(float size, int weight, Color color) { return FontFactory.getFont(family, "Cp1252", false, size, weight, color); }
    }
    private static String join(String... values) { return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining("\n")); }
    private static Color contrast(Color color) { return (.299 * color.getRed() + .587 * color.getGreen() + .114 * color.getBlue()) > 155 ? Color.BLACK : Color.WHITE; }
    private static String money(BigDecimal amount) { return number(amount, "#,##0.00"); }
    private static String number(BigDecimal amount, String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }
    private static String unit(String unit) { return switch (unit) { case "PIECE" -> "Pièce"; case "KG" -> "kg"; case "METER" -> "m"; case "LITER" -> "L"; case "ROLL" -> "Rouleau"; case "SERVICE" -> "Service"; default -> "Autre"; }; }
    private static String payment(String value) { return switch (value) { case "CASH" -> "Espèces"; case "CHECK" -> "Chèque"; case "BANK_TRANSFER" -> "Virement bancaire"; default -> value; }; }
}
