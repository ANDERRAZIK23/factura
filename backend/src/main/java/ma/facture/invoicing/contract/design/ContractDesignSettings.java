package ma.facture.invoicing.contract.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import jakarta.validation.constraints.*;

public record ContractDesignSettings(
        @NotBlank @Size(max = 160) String title,
        @NotNull PaperFormat paperFormat,
        @NotNull Orientation orientation,
        @Min(10) @Max(35) int marginMm,
        boolean showLogo,
        boolean pageNumbers,
        @NotNull JsonNode content) {
    public enum PaperFormat { A4, LETTER }
    public enum Orientation { PORTRAIT, LANDSCAPE }
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public static ContractDesignSettings defaults() {
        ObjectNode heading = block("heading", text("CONTRAT DE PRESTATION DE SERVICES"));
        heading.putObject("attrs").put("level", 1).put("textAlign", "center");
        ObjectNode reference = block("paragraph", text("Référence : "), field("contractReference"));
        reference.putObject("attrs").put("textAlign", "center");
        JsonNode document = block("doc", heading, reference,
                block("paragraph", text("Entre les soussignés :")),
                block("paragraph", field("companyName"), text(", située à "), field("companyAddress"), text(". ICE : "), field("companyIce"), text(".")),
                block("paragraph", text("Et "), field("clientName"), text(", domicilié à "), field("clientAddress"), text(".")),
                heading("Article 1 — Objet du contrat"),
                block("paragraph", text("Décrivez ici l’objet du contrat et le périmètre des prestations.")),
                heading("Article 2 — Durée"),
                block("paragraph", text("Le contrat prend effet le "), field("startDate"), text(" et se termine le "), field("endDate"), text(".")),
                heading("Article 3 — Conditions de réalisation"),
                block("paragraph", text("Précisez les prestations, les tarifs et les modalités de règlement convenus entre les parties.")),
                heading("Article 4 — Signatures"),
                block("table",
                        block("tableRow", block("tableHeader", block("paragraph", field("companyName"))), block("tableHeader", block("paragraph", field("clientName")))),
                        block("tableRow", signatureCell(), signatureCell())),
                block("paragraph"));
        return new ContractDesignSettings("Modèle de contrat", PaperFormat.A4, Orientation.PORTRAIT, 20, true, true, document);
    }
    private static ObjectNode heading(String value) {
        ObjectNode node = block("heading", text(value));
        node.putObject("attrs").put("level", 2);
        return node;
    }
    private static ObjectNode signatureCell() { return block("tableCell", block("paragraph", text("Nom, date et signature"))); }
    private static ObjectNode field(String key) {
        ObjectNode node = JSON.objectNode().put("type", "templateField");
        node.putObject("attrs").put("key", key);
        return node;
    }
    private static ObjectNode text(String value) { return JSON.objectNode().put("type", "text").put("text", value); }
    private static ObjectNode block(String type, JsonNode... children) {
        ObjectNode node = JSON.objectNode().put("type", type);
        if (children.length > 0) {
            ArrayNode content = node.putArray("content");
            for (JsonNode child : children) content.add(child);
        }
        return node;
    }
}
