package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.company.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.design.*;
import ma.facture.invoicing.invoice.dto.InvoiceVersionRequest;
import ma.facture.invoicing.invoice.entity.Invoice;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.invoice.service.InvoiceService;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InvoiceDesignIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @TempDir static Path storage;
    private static final AtomicInteger CODES = new AtomicInteger(300000);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt-secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("app.invoice.storage.path", () -> storage.toString());
        registry.add("app.contracts.expiration-job.enabled", () -> "false");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired InvoiceRepository invoices;
    @Autowired InvoiceDesignRepository designs;
    @Autowired InvoiceService invoiceService;
    private Company company, otherCompany;
    private Client client, otherClient;
    private UserEntity admin;

    @BeforeEach void seed() {
        int code = CODES.getAndAdd(2);
        company = companies.saveAndFlush(new Company("ENT-" + code, "Atlas Fournitures SARL", "12, boulevard Zerktouni, Casablanca", "+212 522 00 00 00", "facturation@atlas.example", "10000000" + code + "1"));
        otherCompany = companies.saveAndFlush(new Company("ENT-" + (code + 1), "Autre entreprise", null, null, null, null));
        admin = users.saveAndFlush(new UserEntity("Admin", "admin" + code + "@example.com", "test-password-hash", UserRole.ADMIN, company.getId()));
        client = clients.saveAndFlush(new Client("CLI-" + code, "Atlas Client", "+212 600 00 00 00", "25, rue des Orangers, Rabat", "client@example.com", "002233445566778", admin.getId(), company.getId()));
        otherClient = clients.saveAndFlush(new Client("CLI-" + (code + 1), "Client privé", null, null, null, null, admin.getId(), otherCompany.getId()));
    }

    @Test void persistsPerCompanyAndRejectsStaleUpdates() throws Exception {
        mvc.perform(get(url(company)).with(as(UserRole.ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false)).andExpect(jsonPath("$.settings.template").value("MODERN"));
        save(company, null, "Modèle original").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(get(url(company)).with(as(UserRole.ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.footerText").value("Modèle original")).andExpect(jsonPath("$.customized").value(true));
        save(company, 0L, "Modèle révisé").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        save(company, 0L, "Version périmée").andExpect(status().isConflict());
        save(company, null, "Création concurrente").andExpect(status().isConflict());
        assertThat(designs.findById(company.getId()).orElseThrow().getSettings().footerText()).isEqualTo("Modèle révisé");
    }

    @Test void persistsFreeGeometryAndValidatesNestedBlocksBeforeSaving() throws Exception {
        var data = json.valueToTree(InvoiceDesignSettings.defaults());
        var blocks = json.createArrayNode();
        String[] kinds = {"LOGO", "COMPANY", "TITLE", "CLIENT", "METADATA", "ITEMS", "TOTALS", "AMOUNT_WORDS", "FOOTER"};
        int[][] boxes = {{60,45,200,70},{60,120,420,145},{580,45,360,110},{560,275,380,105},{60,285,460,90},{60,390,880,260},{530,680,410,155},{60,845,880,65},{60,910,880,70}};
        for (int index = 0; index < kinds.length; index++) {
            var block = blocks.addObject();
            block.put("id", kinds[index].toLowerCase()).put("kind", kinds[index])
                    .put("x", boxes[index][0]).put("y", boxes[index][1]).put("width", boxes[index][2]).put("height", boxes[index][3])
                    .putNull("backgroundColor").put("borderColor", "#243c83").put("borderWidth", 1)
                    .put("textColor", "#263247").put("fontSize", kinds[index].equals("FOOTER") ? 8 : 10)
                    .put("textAlignment", "LEFT").put("text", "");
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).putObject("layout").set("blocks", blocks);
        var settings = json.treeToValue(data, InvoiceDesignSettings.class);
        mvc.perform(put(url(company)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new InvoiceDesignDtos.Request(settings, null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.layout.blocks[3].x").value(560));
        mvc.perform(get(url(company)).with(as(UserRole.ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.layout.blocks[3].height").value(105));
        byte[] preview = mvc.perform(post(previewUrl(company)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(settings))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(pdfText(preview)).contains("1 746,00", "Client de démonstration");
        // Bounds and required financial blocks are checked recursively at the HTTP boundary.
        ((com.fasterxml.jackson.databind.node.ObjectNode) blocks.get(3)).put("x", 999);
        mvc.perform(put(url(company)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("settings", data, "version", 0))))
                .andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode) blocks.get(3)).put("x", 560);
        blocks.remove(6);
        mvc.perform(post(previewUrl(company)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(data))).andExpect(status().isBadRequest());
        assertThat(designs.findById(company.getId()).orElseThrow().getVersion()).isZero();
    }

    @Test void enforcesRolesAndTenantIsolation() throws Exception {
        mvc.perform(get(url(company))).andExpect(status().isUnauthorized());
        for (UserRole role : List.of(UserRole.VIEWER, UserRole.INVOICE_MANAGER)) {
            mvc.perform(get(url(company)).with(as(role))).andExpect(status().isForbidden());
            mvc.perform(post(previewUrl(company)).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(style(""))))
                    .andExpect(status().isForbidden());
            mvc.perform(put(url(company)).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new InvoiceDesignDtos.Request(style(""), null))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(url(otherCompany)).with(as(UserRole.ADMIN))).andExpect(status().isForbidden());
        save(otherCompany, null, "Interdit").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoice-designs").param("companyId", UUID.randomUUID().toString()).with(as(UserRole.APP_ADMIN)))
                .andExpect(status().isNotFound());
        mvc.perform(put(url(otherCompany)).with(as(UserRole.APP_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new InvoiceDesignDtos.Request(style("Modèle privé"), null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyId").value(otherCompany.getId().toString()));
        assertThat(designs.findById(company.getId())).isEmpty();
        assertThat(designs.findById(otherCompany.getId()).orElseThrow().getSettings().footerText()).isEqualTo("Modèle privé");
    }

    @Test void serializesConcurrentFirstSavesWithoutOverwritingTheWinner() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> attempt = () -> {
                ready.countDown();
                start.await();
                return save(company, null, "Modèle concurrent").andReturn().getResponse().getStatus();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(designs.findById(company.getId()).orElseThrow().getVersion()).isZero();
        }
    }

    @Test void previewsWithoutCreatingInvoicesOrSavingDesignAndValidatesInput() throws Exception {
        long count = invoices.count();
        byte[] content = mvc.perform(post(previewUrl(company)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(style("Merci pour votre confiance. RIB : 123 456 789."))))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray();
        assertThat(pdfText(content)).contains("APERÇU", "Client de démonstration", "Atlas Fournitures SARL", "Merci pour votre confiance", "1 746,00");
        assertThat(invoices.count()).isEqualTo(count);
        assertThat(designs.findById(company.getId())).isEmpty();
        Path output = Path.of("target/generated-test-pdfs/designer-preview.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, content);
        String invalid = json.writeValueAsString(new InvoiceDesignDtos.Request(style(""), null)).replace("#243c83", "javascript:bad");
        mvc.perform(put(url(company)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        save(company, null, "1\n2\n3\n4\n5").andExpect(status().isBadRequest());
    }

    @Test void validationUsesCompanyDesignForExistingAndNewClientsAndPreservesStoredPdfs() throws Exception {
        save(company, null, "Pied de page initial").andExpect(status().isOk());
        Product product = products.saveAndFlush(new Product("PRD-" + CODES.getAndIncrement(), "Article réel", "Description réelle", BigDecimal.TEN, BigDecimal.valueOf(20), ProductUnit.PIECE, admin.getId(), company.getId()));
        Invoice draft = new Invoice(LocalDate.now(), client, null, PaymentMethod.BANK_TRANSFER, "BC-42", admin.getId());
        InvoiceLineInput line = new InvoiceLineInput(BigDecimal.valueOf(2), BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.ZERO);
        draft.addLine(product, line.quantity(), line.unitPriceExcludingTax(), line.vatRate(), line.discountRate());
        draft.applyCalculation(new InvoiceCalculationService().calculate(List.of(line)));
        draft = invoices.saveAndFlush(draft);
        invoiceService.validate(draft.getId(), new InvoiceVersionRequest(draft.getVersion()), actor(UserRole.ADMIN));
        byte[] original = Files.readAllBytes(invoiceService.downloadPdf(draft.getId(), actor(UserRole.ADMIN)).path());
        assertThat(pdfText(original)).contains("Pied de page initial", "24,00", "Article réel").doesNotContain("APERÇU", "DONNÉES D’EXEMPLE");
        save(company, 0L, "Nouveau pied de page").andExpect(status().isOk());
        assertThat(Files.readAllBytes(invoiceService.downloadPdf(draft.getId(), actor(UserRole.ADMIN)).path())).isEqualTo(original);
        Client newClient = clients.saveAndFlush(new Client("CLI-" + CODES.getAndIncrement(), "Nouveau client", null, null, null, null, admin.getId(), company.getId()));
        Invoice next = new Invoice(LocalDate.now(), newClient, null, PaymentMethod.BANK_TRANSFER, "BC-43", admin.getId());
        next.addLine(product, line.quantity(), line.unitPriceExcludingTax(), line.vatRate(), line.discountRate());
        next.applyCalculation(new InvoiceCalculationService().calculate(List.of(line)));
        next = invoices.saveAndFlush(next);
        invoiceService.validate(next.getId(), new InvoiceVersionRequest(next.getVersion()), actor(UserRole.ADMIN));
        byte[] shared = Files.readAllBytes(invoiceService.downloadPdf(next.getId(), actor(UserRole.ADMIN)).path());
        assertThat(pdfText(shared)).contains("Nouveau client", "Nouveau pied de page", "24,00");
        assertThat(designs.count()).isGreaterThanOrEqualTo(1);

    }

    @Test void canConfigureAndPreviewACompanyWithoutAnyClients() throws Exception {
        clients.delete(otherClient);
        long count = clients.count();
        mvc.perform(get(url(otherCompany)).with(as(UserRole.APP_ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false));
        mvc.perform(put(url(otherCompany)).with(as(UserRole.APP_ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new InvoiceDesignDtos.Request(style("Modèle commun"), null))))
                .andExpect(status().isOk());
        byte[] pdf = mvc.perform(post(previewUrl(otherCompany)).with(as(UserRole.APP_ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(style("Modèle commun")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdfText(pdf)).contains("Autre entreprise", "Client de démonstration", "Modèle commun");
        assertThat(clients.count()).isEqualTo(count);
    }

    private org.springframework.test.web.servlet.ResultActions save(Company target, Long version, String footer) throws Exception {
        return mvc.perform(put(url(target)).with(as(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new InvoiceDesignDtos.Request(style(footer), version))));
    }
    private static String url(Company target) { return "/api/v1/invoice-designs?companyId=" + target.getId(); }
    private static String previewUrl(Company target) { return "/api/v1/invoice-designs/preview?companyId=" + target.getId(); }
    private AuthenticatedUser actor(UserRole role) { return new AuthenticatedUser(admin.getId(), admin.getEmail(), role, company.getId()); }
    private RequestPostProcessor as(UserRole role) { return authentication(new UsernamePasswordAuthenticationToken(actor(role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))); }
    private static InvoiceDesignSettings style(String footer) {
        var d = InvoiceDesignSettings.defaults();
        return new InvoiceDesignSettings(d.template(), "#243c83", d.fontFamily(), d.fontSize(), d.paperFormat(), false, d.logoPosition(), d.clientPosition(), true, true, true, true, true, true, footer);
    }
    private static String pdfText(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
            return text.toString();
        } finally { reader.close(); }
    }
}
