package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.*;
import ma.facture.invoicing.auth.RefreshTokenRepository;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.contract.repository.ContractRepository;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(InvoiceIT.FixedClockConfig.class)
class InvoiceIT {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "AdminPassword123!";
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @TempDir static Path pdfStorage;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt-secret", () -> SECRET);
        registry.add("app.security.issuer", () -> "test-issuer");
        registry.add("app.security.audience", () -> "test-audience");
        registry.add("app.security.initial-admin-name", () -> "Bootstrap Admin");
        registry.add("app.security.initial-admin-email", () -> "bootstrap@example.com");
        registry.add("app.security.initial-admin-password", () -> PASSWORD);
        registry.add("app.contracts.expiration-job.enabled", () -> "false");
        registry.add("app.invoice.storage.path", () -> pdfStorage.toString());
    }

    @TestConfiguration static class FixedClockConfig {
        @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ClientRepository clients;
    @Autowired ProductRepository products;
    @Autowired ContractRepository contracts;
    @Autowired InvoiceRepository invoices;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JdbcTemplate jdbc;

    private UserEntity admin;
    private Client client;
    private Product product;

    @BeforeEach void seed() {
        jdbc.execute("DELETE FROM invoice_reminders");
        jdbc.execute("DELETE FROM invoice_payments");
        invoices.deleteAll();
        contracts.deleteAll();
        products.deleteAll();
        clients.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        admin = users.save(new UserEntity("Admin", "admin@example.com", encoder.encode(PASSWORD), UserRole.ADMIN));
        users.save(new UserEntity("Manager", "manager@example.com", encoder.encode(PASSWORD), UserRole.INVOICE_MANAGER));
        users.save(new UserEntity("Viewer", "viewer@example.com", encoder.encode(PASSWORD), UserRole.VIEWER));
        client = clients.save(new Client("CLI-910001", "Atlas Client", "0500000000", "Historic address", null,
                "001527304000014", admin.getId()));
        product = products.save(new Product("PRD-910001", "Blue Roll", "Historic description", new BigDecimal("13.00"),
                new BigDecimal("20.00"), ProductUnit.ROLL, admin.getId()));
    }

    @Test void createsValidatesSnapshotsPaysCancelsAndKeepsNumber() throws Exception {
        String token = tokenFor("admin@example.com");
        JsonNode draft = body(mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(invoiceJson(true)))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern(".*/api/v1/invoices/[0-9a-f-]+"))).andReturn());
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("invoiceNumber").isNull()).isTrue();
        assertThat(draft.get("createdByName").asText()).isEqualTo(admin.getFullName());
        assertThat(draft.get("updatedByName").asText()).isEqualTo(admin.getFullName());
        assertThat(draft.get("totalIncludingTax").decimalValue()).isEqualByComparingTo("29.66");

        JsonNode validated = body(mvc.perform(post("/api/v1/invoices/{id}/validate", draft.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + draft.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(validated.get("invoiceNumber").asText()).matches("FA-2026-[0-9]{6,}");
        assertThat(validated.get("validatedAt").asText()).isEqualTo(NOW.toString());
        assertThat(validated.get("validatorName").asText()).isEqualTo(admin.getFullName());
        assertThat(validated.get("pdfAvailable").asBoolean()).isTrue();
        assertThat(validated.get("pdfDownloadUrl").asText()).isEqualTo("/api/v1/invoices/" + draft.get("id").asText() + "/pdf");
        MvcResult pdf = mvc.perform(get("/api/v1/invoices/{id}/pdf", draft.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(validated.get("invoiceNumber").asText() + ".pdf"))).andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");

        Client changedClient = clients.findById(client.getId()).orElseThrow();
        changedClient.update("Changed client", null, "Changed address", null, null, admin.getId());
        clients.saveAndFlush(changedClient);
        Product changedProduct = products.findById(product.getId()).orElseThrow();
        changedProduct.update("Changed product", "Changed description", BigDecimal.ONE, BigDecimal.ZERO, ProductUnit.OTHER, admin.getId());
        products.saveAndFlush(changedProduct);

        mvc.perform(get("/api/v1/invoices/{id}", draft.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.client.clientName").value("Atlas Client"))
                .andExpect(jsonPath("$.client.address").value("Historic address"))
                .andExpect(jsonPath("$.lines[0].productName").value("Blue Roll"))
                .andExpect(jsonPath("$.lines[0].productDescription").value("Historic description"));
        mvc.perform(put("/api/v1/invoices/{id}", draft.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(invoiceUpdateJson(validated.get("version").asLong())))
                .andExpect(status().isConflict());

        JsonNode paid = body(mvc.perform(post("/api/v1/invoices/{id}/mark-paid", draft.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + validated.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/v1/invoices/{id}/cancel", draft.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + paid.get("version").asLong() + "}"))
                .andExpect(status().isConflict());

        JsonNode secondDraft = body(mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(invoiceJson(true)))
                .andExpect(status().isCreated()).andReturn());
        JsonNode secondValidated = body(mvc.perform(post("/api/v1/invoices/{id}/validate", secondDraft.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + secondDraft.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode cancelled = body(mvc.perform(post("/api/v1/invoices/{id}/cancel", secondDraft.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + secondValidated.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("invoiceNumber").asText()).isEqualTo(secondValidated.get("invoiceNumber").asText());
    }

    @Test void enforcesValidationAuthorizationQueriesAndOptimisticLocking() throws Exception {
        String adminToken = tokenFor("admin@example.com");
        String viewerToken = tokenFor("viewer@example.com");
        mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON).content(invoiceJson(true))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(invoiceJson(true)))
                .andExpect(status().isUnauthorized());

        JsonNode empty = body(mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(invoiceJson(false)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/v1/invoices/{id}/validate", empty.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("https://invoice-management.local/problems/invalid-invoice"));
        mvc.perform(put("/api/v1/invoices/{id}", empty.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(invoiceUpdateJson(99)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://invoice-management.local/problems/invoice-concurrency"));

        mvc.perform(get("/api/v1/invoices?search=atlas&status=DRAFT&clientId=" + client.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String query : List.of("size=101", "status=UNKNOWN", "sort=client,asc", "sort=createdAt,sideways",
                "invoiceDateFrom=2026-08-05&invoiceDateTo=2026-08-04"))
            mvc.perform(get("/api/v1/invoices?" + query).header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                    .andExpect(status().isBadRequest());
    }

    @Test void migrationInstallsInvoiceConstraintsAndIndexes() {
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='6'", String.class)).isEqualTo("6");
        for (String index : List.of("idx_invoices_contract_id", "idx_invoices_invoice_date", "idx_invoices_created_at", "idx_invoice_lines_product_id"))
            assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname=?", Integer.class, index)).isOne();
        String sql = "insert into invoices(id,company_id,invoice_date,client_id,created_by_user_id,payment_method,subtotal_excluding_tax,total_discount,taxable_amount,total_vat,total_including_tax,status,version,updated_by,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,0,?,now(),now())";
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), admin.getCompanyId(), LocalDate.of(2026, 8, 4), client.getId(), admin.getId(),
                "CRYPTO", 10, 0, 10, 2, 12, "DRAFT", admin.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), admin.getCompanyId(), LocalDate.of(2026, 8, 4), client.getId(), admin.getId(),
                "CHECK", 10, 1, 10, 2, 12, "DRAFT", admin.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private String invoiceJson(boolean withLine) {
        String lines = withLine ? "{\"productId\":\"" + product.getId() + "\",\"quantity\":2,\"unitPriceExcludingTax\":13.005,\"vatRate\":19.995,\"discountRate\":5}" : "";
        return "{\"invoiceDate\":\"2026-08-04\",\"clientId\":\"" + client.getId()
                + "\",\"paymentMethod\":\"CHECK\",\"customerOrderReference\":\"ORDER-1\",\"lines\":[" + lines + "]}";
    }

    private String invoiceUpdateJson(long version) {
        return "{\"invoiceDate\":\"2026-08-04\",\"clientId\":\"" + client.getId()
                + "\",\"paymentMethod\":\"CHECK\",\"customerOrderReference\":\"ORDER-1\",\"lines\":["
                + "{\"productId\":\"" + product.getId() + "\",\"quantity\":2,\"unitPriceExcludingTax\":13,\"vatRate\":20}],"
                + "\"version\":" + version + "}";
    }

    private String tokenFor(String email) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private static String bearer(String token) { return "Bearer " + token; }
}
