package ma.facture.invoicing.contract.design;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ma.facture.invoicing.common.ValidationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContractDocumentValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ContractDocumentValidator validator = new ContractDocumentValidator();
    @Test void acceptsTheDefaultTemplateAndSupportedFormatting() throws Exception {
        assertThatCode(() -> validator.validate(ContractDesignSettings.defaults().content())).doesNotThrowAnyException();
        validator.validate(json.readTree("""
                {"type":"doc","content":[{"type":"paragraph","attrs":{"textAlign":"justify"},"content":[
                  {"type":"text","text":"Texte formaté","marks":[{"type":"bold"},{"type":"italic"},{"type":"underline"},{"type":"strike"},
                    {"type":"textStyle","attrs":{"fontFamily":"Times New Roman","fontSize":"14pt","color":"rgb(12, 34, 56)"}},
                    {"type":"highlight","attrs":{"color":"#ff0"}}]}]}, {"type":"pageBreak"}, {"type":"paragraph"}]}
                """));
    }
    @Test void rejectsExecutableResourcesUnknownFieldsAndInvalidValues() throws Exception {
        for (String node : new String[] {
                "{\"type\":\"image\",\"attrs\":{\"src\":\"http://internal/private\"}}",
                "{\"type\":\"paragraph\",\"attrs\":{\"onclick\":\"alert(1)\"}}",
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"templateField\",\"attrs\":{\"key\":\"secret\"}}]}",
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"textStyle\",\"attrs\":{\"fontSize\":\"999pt\"}}]}]}",
                "{\"type\":\"heading\",\"attrs\":{\"level\":null}}",
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"highlight\",\"attrs\":{\"color\":\"rgb(999, 2, 3)\"}}]}]}"
        }) {
            JsonNode document = json.readTree("{\"type\":\"doc\",\"content\":[" + node + "]}");
            assertThatThrownBy(() -> validator.validate(document)).isInstanceOf(ValidationException.class);
        }
    }
    @Test void rejectsMalformedTablesAndExcessiveText() throws Exception {
        JsonNode table = json.readTree("""
                {"type":"doc","content":[{"type":"table","content":[
                  {"type":"tableRow","content":[{"type":"tableCell","content":[{"type":"paragraph"}]}]},
                  {"type":"tableRow","content":[]}]}]}
                """);
        assertThatThrownBy(() -> validator.validate(table)).isInstanceOf(ValidationException.class).hasMessageContaining("colonnes");
        ObjectNode document = json.createObjectNode().put("type", "doc");
        document.putArray("content").addObject().put("type", "paragraph").putArray("content").addObject().put("type", "text").put("text", "x".repeat(100_001));
        assertThatThrownBy(() -> validator.validate(document)).isInstanceOf(ValidationException.class).hasMessageContaining("100 000");
    }
}
