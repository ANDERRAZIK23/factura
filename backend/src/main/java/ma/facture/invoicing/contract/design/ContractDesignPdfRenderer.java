package ma.facture.invoicing.contract.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import ma.facture.invoicing.common.ValidationException;
import ma.facture.invoicing.company.Company;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

@Component
public class ContractDesignPdfRenderer {
    public byte[] render(ContractDesignSettings settings, Company company, byte[] logo) {
        Rectangle size = settings.paperFormat() == ContractDesignSettings.PaperFormat.A4 ? PageSize.A4 : PageSize.LETTER;
        if (settings.orientation() == ContractDesignSettings.Orientation.LANDSCAPE) size = size.rotate();
        float margin = settings.marginMm() * 72f / 25.4f;
        Document document = new Document(size, margin, margin, margin, margin);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            if (settings.pageNumbers()) writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter writer, Document document) {
                    ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                            new Phrase("Page " + writer.getPageNumber(), font("Arial", 8, Font.NORMAL, Color.GRAY)),
                            document.getPageSize().getWidth() / 2, margin / 2, 0);
                }
            });
            document.addTitle(settings.title());
            document.addAuthor(company.getCompanyName());
            document.open();
            if (logo != null) {
                Image image = Image.getInstance(logo);
                image.scaleToFit(110, 55);
                image.setSpacingAfter(12);
                document.add(image);
            }
            for (JsonNode node : settings.content().path("content")) {
                if ("pageBreak".equals(node.path("type").asText())) {
                    writer.setPageEmpty(false);
                    document.newPage();
                }
                else document.add(block(node, company));
            }
            // An empty but valid editor still exports a one-page document.
            writer.setPageEmpty(false);
            document.close();
            return output.toByteArray();
        } catch (DocumentException | java.io.IOException exception) {
            throw new IllegalStateException("Impossible de générer le PDF du contrat", exception);
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    private Element block(JsonNode node, Company company) throws DocumentException {
        return switch (node.path("type").asText()) {
            case "paragraph", "heading" -> paragraph(node, company);
            case "bulletList", "orderedList" -> list(node, company);
            case "table" -> table(node, company);
            case "horizontalRule" -> {
                Paragraph paragraph = new Paragraph();
                paragraph.add(new Chunk(new LineSeparator(0.5f, 100, new Color(203, 213, 225), Element.ALIGN_CENTER, 0)));
                paragraph.setSpacingAfter(8);
                yield paragraph;
            }
            default -> throw new ValidationException("Élément de contrat non pris en charge.");
        };
    }
    private Paragraph paragraph(JsonNode node, Company company) {
        boolean heading = "heading".equals(node.path("type").asText());
        int level = node.path("attrs").path("level").asInt(1);
        float size = heading ? switch (level) { case 1 -> 24; case 2 -> 18; default -> 14; } : 12;
        Paragraph paragraph = new Paragraph();
        paragraph.setFont(font("Arial", size, heading ? Font.BOLD : Font.NORMAL, Color.BLACK));
        paragraph.setLeading(size * 1.5f);
        paragraph.setSpacingBefore(heading ? 12 : 0);
        paragraph.setSpacingAfter(heading ? 8 : 6);
        paragraph.setAlignment(alignment(node.path("attrs").path("textAlign").asText("left")));
        for (JsonNode inline : node.path("content")) {
            String text = switch (inline.path("type").asText()) {
                case "text" -> inline.path("text").asText();
                case "hardBreak" -> "\n";
                case "templateField" -> field(inline.path("attrs").path("key").asText(), company);
                default -> throw new ValidationException("Élément de texte non pris en charge.");
            };
            if (!Charset.forName("windows-1252").newEncoder().canEncode(text))
                throw new ValidationException("L’export PDF prend en charge les caractères latins. Remplacez les caractères non pris en charge avant d’exporter.");
            int style = heading ? Font.BOLD : Font.NORMAL;
            String family = "Arial";
            Color color = Color.BLACK, background = null;
            float fontSize = size;
            for (JsonNode mark : inline.path("marks")) {
                JsonNode attrs = mark.path("attrs");
                switch (mark.path("type").asText()) {
                    case "bold" -> style |= Font.BOLD;
                    case "italic" -> style |= Font.ITALIC;
                    case "underline" -> style |= Font.UNDERLINE;
                    case "strike" -> style |= Font.STRIKETHRU;
                    case "highlight" -> background = attrs.path("color").isTextual() ? color(attrs.path("color").asText()) : new Color(254, 240, 138);
                    case "textStyle" -> {
                        if (attrs.path("fontFamily").isTextual()) family = attrs.path("fontFamily").asText();
                        if (attrs.path("color").isTextual()) color = color(attrs.path("color").asText());
                        if (attrs.path("fontSize").isTextual()) {
                            String value = attrs.path("fontSize").asText();
                            fontSize = Float.parseFloat(value.substring(0, value.length() - 2)) * (value.endsWith("px") ? 0.75f : 1);
                        }
                    }
                    default -> throw new ValidationException("Mise en forme non prise en charge.");
                }
            }
            Chunk chunk = new Chunk(text, font(family, fontSize, style, color));
            paragraph.setLeading(Math.max(paragraph.getLeading(), fontSize * 1.5f));
            if (background != null) chunk.setBackground(background);
            paragraph.add(chunk);
        }
        if (paragraph.isEmpty()) paragraph.add(new Chunk(" ", paragraph.getFont()));
        return paragraph;
    }
    private com.lowagie.text.List list(JsonNode node, Company company) throws DocumentException {
        boolean ordered = "orderedList".equals(node.path("type").asText());
        String type = node.path("attrs").path("type").asText("1");
        com.lowagie.text.List list;
        if (ordered && ("i".equals(type) || "I".equals(type))) list = new RomanList("i".equals(type), 18);
        else {
            list = new com.lowagie.text.List(ordered, ordered && ("a".equals(type) || "A".equals(type)), 18);
            list.setLowercase("a".equals(type));
        }
        list.setIndentationLeft(18);
        list.setFirst(node.path("attrs").path("start").asInt(1));
        if (!ordered) list.setListSymbol(new Chunk("• ", font("Arial", 12, Font.NORMAL, Color.BLACK)));
        for (JsonNode itemNode : node.path("content")) {
            ListItem item = new ListItem();
            item.setFont(font("Arial", 12, Font.NORMAL, Color.BLACK));
            for (JsonNode child : itemNode.path("content")) item.add(block(child, company));
            list.add(item);
        }
        return list;
    }
    private PdfPTable table(JsonNode node, Company company) throws DocumentException {
        JsonNode rows = node.path("content");
        int count = rows.get(0).path("content").size();
        PdfPTable table = new PdfPTable(count);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(12);
        table.setSplitLate(false);
        ArrayList<Float> widths = new ArrayList<>();
        for (JsonNode cell : rows.get(0).path("content")) {
            JsonNode width = cell.path("attrs").path("colwidth").path(0);
            if (width.isNumber()) widths.add(width.floatValue());
        }
        if (widths.size() == count) {
            float[] values = new float[count];
            for (int index = 0; index < count; index++) values[index] = widths.get(index);
            table.setWidths(values);
        }
        boolean header = true;
        for (JsonNode cell : rows.get(0).path("content")) header &= "tableHeader".equals(cell.path("type").asText());
        if (header) table.setHeaderRows(1);
        for (JsonNode row : rows) for (JsonNode cellNode : row.path("content")) {
            PdfPCell cell = new PdfPCell();
            cell.setPadding(7);
            cell.setBorderColor(new Color(148, 163, 184));
            cell.setVerticalAlignment(Element.ALIGN_TOP);
            if ("tableHeader".equals(cellNode.path("type").asText())) cell.setBackgroundColor(new Color(241, 245, 249));
            for (JsonNode child : cellNode.path("content")) {
                Element element = block(child, company);
                if ("tableHeader".equals(cellNode.path("type").asText())) {
                    for (Element part : element.getChunks()) if (part instanceof Chunk chunk)
                        chunk.getFont().setStyle(chunk.getFont().getStyle() | Font.BOLD);
                }
                if (element instanceof Paragraph paragraph && !child.path("attrs").path("textAlign").isTextual())
                    paragraph.setAlignment(alignment(cellNode.path("attrs").path("align").asText("left")));
                cell.addElement(element);
            }
            table.addCell(cell);
        }
        return table;
    }
    private static Font font(String family, float size, int style, Color color) {
        String name = switch (family.replace("\"", "").replace("'", "")) {
            case "Times New Roman" -> FontFactory.TIMES_ROMAN;
            case "Courier New" -> FontFactory.COURIER;
            default -> FontFactory.HELVETICA;
        };
        return FontFactory.getFont(name, "Cp1252", false, size, style, color);
    }
    private static int alignment(String value) {
        return switch (value) { case "center" -> Element.ALIGN_CENTER; case "right" -> Element.ALIGN_RIGHT; case "justify" -> Element.ALIGN_JUSTIFIED; default -> Element.ALIGN_LEFT; };
    }
    private static Color color(String value) {
        if (value.startsWith("rgb")) {
            String[] parts = value.replaceAll("[^0-9,]", "").split(",");
            return new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        if (value.length() == 4) value = "#" + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2) + value.charAt(3) + value.charAt(3);
        return Color.decode(value);
    }
    private static String field(String key, Company company) {
        String value = switch (key) {
            case "companyName" -> company.getCompanyName();
            case "companyAddress" -> company.getAddress();
            case "companyIce" -> company.getIce();
            case "companyPhone" -> company.getPhone();
            case "companyEmail" -> company.getEmail();
            default -> null;
        };
        return value == null || value.isBlank() ? "« " + ContractDocumentValidator.FIELDS.get(key) + " »" : value;
    }
}
