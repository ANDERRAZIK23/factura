package ma.facture.invoicing.contract.design;

import com.fasterxml.jackson.databind.JsonNode;
import ma.facture.invoicing.common.ValidationException;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Validates the editor's document schema; HTML, scripts and external resources are never accepted. */
@Component
public class ContractDocumentValidator {
    private static final Set<String> BLOCKS = Set.of("paragraph", "heading", "bulletList", "orderedList", "table", "horizontalRule", "pageBreak");
    private static final Set<String> INLINE = Set.of("text", "hardBreak", "templateField");
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline", "strike", "textStyle", "highlight");
    public static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("companyName", "Nom de l’entreprise"), Map.entry("companyAddress", "Adresse de l’entreprise"),
            Map.entry("companyIce", "ICE de l’entreprise"), Map.entry("companyPhone", "Téléphone de l’entreprise"),
            Map.entry("companyEmail", "E-mail de l’entreprise"), Map.entry("clientName", "Nom du client"),
            Map.entry("clientAddress", "Adresse du client"), Map.entry("clientIce", "ICE du client"),
            Map.entry("contractReference", "Référence du contrat"), Map.entry("startDate", "Date de début"), Map.entry("endDate", "Date de fin"));
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?|rgb\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*\\)");
    private static final Pattern SIZE = Pattern.compile("(\\d{1,2}(?:\\.\\d{1,2})?)(pt|px)");

    public void validate(JsonNode document) {
        if (document == null || !"doc".equals(document.path("type").asText())) invalid("Le document doit contenir une page de texte.");
        if (document.toString().length() > 500_000) invalid("Le document dépasse la limite de 500 000 caractères.");
        visit(document, null, 0, new int[2]);
    }
    private void visit(JsonNode node, String parent, int depth, int[] totals) {
        if (depth > 16 || ++totals[0] > 10_000) invalid("Le document est trop complexe (10 000 éléments ou 16 niveaux maximum).");
        if (!node.isObject()) invalid("Élément de document invalide.");
        keys(node, Set.of("type", "attrs", "content", "marks", "text"));
        String type = node.path("type").asText();
        if (parent != null && !childAllowed(parent, type)) invalid("Élément non pris en charge dans " + parent + " : " + type + ".");
        JsonNode attrs = node.path("attrs");
        if (!attrs.isMissingNode() && !attrs.isObject()) invalid("Attributs de document invalides.");
        attributes(type, attrs);
        JsonNode marks = node.path("marks");
        if (!marks.isMissingNode()) {
            if (!INLINE.contains(type) || !marks.isArray() || marks.size() > 6) invalid("Mise en forme invalide.");
            Set<String> seen = new HashSet<>();
            for (JsonNode mark : marks) {
                String name = mark.path("type").asText();
                if (!mark.isObject() || !MARKS.contains(name) || !seen.add(name)) invalid("Mise en forme non prise en charge : " + name + ".");
                keys(mark, Set.of("type", "attrs"));
                JsonNode markAttrs = mark.path("attrs");
                if (!markAttrs.isMissingNode() && !markAttrs.isObject()) invalid("Attributs de mise en forme invalides.");
                attributes(name, markAttrs);
            }
        }
        if ("text".equals(type)) {
            if (!node.path("text").isTextual() || node.path("text").asText().isEmpty()) invalid("Texte invalide.");
            totals[1] += node.path("text").asText().length();
            if (totals[1] > 100_000) invalid("Le contrat est limité à 100 000 caractères de texte.");
        } else if (node.has("text")) invalid("Texte inattendu.");
        JsonNode children = node.path("content");
        if (!children.isMissingNode() && !children.isArray()) invalid("Contenu de document invalide.");
        boolean leaf = INLINE.contains(type) || Set.of("pageBreak", "horizontalRule").contains(type);
        if (leaf && children.size() > 0) invalid("Contenu inattendu dans " + type + ".");
        if (!leaf && !Set.of("paragraph", "heading").contains(type) && children.size() == 0) invalid("L’élément " + type + " ne peut pas être vide.");
        if ("heading".equals(type) && !attrs.path("level").isIntegralNumber()) invalid("Niveau de titre manquant.");
        if ("templateField".equals(type) && !FIELDS.containsKey(attrs.path("key").asText())) invalid("Champ de modèle inconnu.");
        if ("listItem".equals(type) && !"paragraph".equals(children.path(0).path("type").asText())) invalid("Un élément de liste doit commencer par un paragraphe.");
        if ("table".equals(type)) {
            int columns = children.path(0).path("content").size();
            if (columns < 1 || columns > 10 || children.size() > 200) invalid("Un tableau est limité à 10 colonnes et 200 lignes.");
            for (JsonNode row : children) if (row.path("content").size() != columns) invalid("Les lignes du tableau doivent avoir le même nombre de colonnes.");
        }
        for (JsonNode child : children) visit(child, type, depth + 1, totals);
    }
    private static boolean childAllowed(String parent, String child) {
        return switch (parent) {
            case "doc" -> BLOCKS.contains(child);
            case "paragraph", "heading" -> INLINE.contains(child);
            case "bulletList", "orderedList" -> "listItem".equals(child);
            case "listItem", "tableCell", "tableHeader" -> Set.of("paragraph", "heading", "bulletList", "orderedList", "horizontalRule").contains(child);
            case "table" -> "tableRow".equals(child);
            case "tableRow" -> Set.of("tableCell", "tableHeader").contains(child);
            default -> false;
        };
    }
    private static void attributes(String type, JsonNode attrs) {
        Set<String> allowed = switch (type) {
            case "paragraph" -> Set.of("textAlign");
            case "heading" -> Set.of("textAlign", "level");
            case "orderedList" -> Set.of("start", "type");
            case "tableCell", "tableHeader" -> Set.of("colspan", "rowspan", "colwidth", "align");
            case "templateField" -> Set.of("key");
            case "textStyle" -> Set.of("fontFamily", "fontSize", "color");
            case "highlight" -> Set.of("color");
            default -> Set.of();
        };
        keys(attrs, allowed);
        attrs.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull()) return;
            boolean valid = switch (key) {
                case "textAlign", "align" -> value.isTextual() && Set.of("left", "center", "right", "justify").contains(value.asText());
                case "level" -> integer(value, 1, 3);
                case "start" -> integer(value, 1, 10_000);
                case "type" -> value.isTextual() && Set.of("1", "a", "A", "i", "I").contains(value.asText());
                case "colspan", "rowspan" -> integer(value, 1, 1);
                case "colwidth" -> value.isArray() && value.size() == 1 && integer(value.get(0), 25, 2000);
                case "key" -> value.isTextual() && FIELDS.containsKey(value.asText());
                case "fontFamily" -> value.isTextual() && Set.of("Arial", "Times New Roman", "Courier New").contains(value.asText().replace("\"", "").replace("'", ""));
                case "fontSize" -> validSize(value);
                case "color" -> validColor(value);
                default -> false;
            };
            if (!valid) invalid("Valeur non prise en charge pour " + key + ". Les cellules fusionnées et les polices externes ne sont pas prises en charge.");
        });
    }
    private static boolean validSize(JsonNode value) {
        if (!value.isTextual()) return false;
        var match = SIZE.matcher(value.asText());
        return match.matches() && Double.parseDouble(match.group(1)) >= 8 && Double.parseDouble(match.group(1)) <= 72;
    }
    private static boolean validColor(JsonNode value) {
        if (!value.isTextual() || !COLOR.matcher(value.asText()).matches()) return false;
        if (value.asText().startsWith("#")) return true;
        return Arrays.stream(value.asText().replaceAll("[^0-9,]", "").split(",")).allMatch(part -> Integer.parseInt(part) <= 255);
    }
    private static boolean integer(JsonNode value, int min, int max) { return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= min && value.intValue() <= max; }
    private static void keys(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) invalid("Attribut non pris en charge : " + key + "."); });
    }
    private static void invalid(String message) { throw new ValidationException(message); }
}
