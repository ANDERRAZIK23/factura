package ma.facture.invoicing.contract.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import ma.facture.invoicing.company.Company;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ContractDesignPdfRendererTest {
    private final ContractDesignPdfRenderer renderer = new ContractDesignPdfRenderer();
    private final Company company = new Company("ENT-000002", "Atlas Services SARL", "12 rue des Orangers", "+212 600 00 00 00", "contact@atlas.example", "001234567890123");
    @Test void exportsCompanyFieldsTablesAndPageNumbers() throws Exception {
        byte[] bytes = renderer.render(ContractDesignSettings.defaults(), company, null);
        PdfReader reader = new PdfReader(bytes);
        try {
            StringBuilder text = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
            assertThat(text.toString()).contains("Atlas Services SARL", "12 rue des Orangers", "001234567890123", "Objet du contrat", "Nom du client", "Nom, date et signature", "Page 1");
            assertThat(reader.getInfo()).containsEntry("Title", "Modèle de contrat");
            Path output = Path.of("target/generated-test-pdfs/contract-template.pdf");
            Files.createDirectories(output.getParent());
            Files.write(output, bytes);
        } finally { reader.close(); }
    }
    @Test void honoursPageBreaksAndLandscapePaper() throws Exception {
        var content = new ObjectMapper().readTree("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Première page"}]},
                {"type":"pageBreak"},{"type":"paragraph","content":[{"type":"text","text":"Deuxième page"}]}]}
                """);
        var settings = new ContractDesignSettings("Contrat paysage", ContractDesignSettings.PaperFormat.LETTER, ContractDesignSettings.Orientation.LANDSCAPE, 25, false, false, content);
        PdfReader reader = new PdfReader(renderer.render(settings, company, null));
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            assertThat(reader.getPageSizeWithRotation(1).getWidth()).isGreaterThan(reader.getPageSizeWithRotation(1).getHeight());
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Première page").doesNotContain("Deuxième page", "Page 1");
            assertThat(new PdfTextExtractor(reader).getTextFromPage(2)).contains("Deuxième page");
        } finally { reader.close(); }
    }
    @Test void exportsAnEmptyDocumentAndKeepsExplicitEmptyPages() throws Exception {
        for (String nodes : new String[] {"{\"type\":\"paragraph\"}", "{\"type\":\"pageBreak\"},{\"type\":\"paragraph\"}"}) {
            var content = new ObjectMapper().readTree("{\"type\":\"doc\",\"content\":[" + nodes + "]}");
            var settings = new ContractDesignSettings("Vide", ContractDesignSettings.PaperFormat.A4, ContractDesignSettings.Orientation.PORTRAIT, 20, false, true, content);
            PdfReader reader = new PdfReader(renderer.render(settings, company, null));
            try { assertThat(reader.getNumberOfPages()).isEqualTo(nodes.contains("pageBreak") ? 2 : 1); }
            finally { reader.close(); }
        }
    }
}
