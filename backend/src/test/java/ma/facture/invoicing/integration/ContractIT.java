package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.*;
import ma.facture.invoicing.auth.RefreshTokenRepository;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.contract.repository.ContractRepository;
import ma.facture.invoicing.contract.service.ContractExpirationService;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(ContractIT.FixedClockConfig.class)
class ContractIT {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "AdminPassword123!";
    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword); registry.add("app.security.jwt-secret", () -> SECRET);
        registry.add("app.security.issuer", () -> "test-issuer"); registry.add("app.security.audience", () -> "test-audience");
        registry.add("app.security.initial-admin-name", () -> "Bootstrap Admin"); registry.add("app.security.initial-admin-email", () -> "bootstrap@example.com");
        registry.add("app.security.initial-admin-password", () -> PASSWORD); registry.add("app.contracts.expiration-job.enabled", () -> "true");
    }
    @TestConfiguration static class FixedClockConfig { @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }

    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
    @Autowired ClientRepository clients; @Autowired ProductRepository products; @Autowired ContractRepository contracts;
    @Autowired RefreshTokenRepository refreshTokens; @Autowired JdbcTemplate jdbc; @Autowired ContractExpirationService expiration;
    private UserEntity admin; private Client activeClient; private Product activeProduct; private Product secondProduct;

    @BeforeEach void seed() {
        contracts.deleteAll(); products.deleteAll(); clients.deleteAll(); refreshTokens.deleteAll(); users.deleteAll();
        admin = users.save(new UserEntity("Admin", "admin@example.com", encoder.encode(PASSWORD), UserRole.ADMIN));
        users.save(new UserEntity("Manager", "manager@example.com", encoder.encode(PASSWORD), UserRole.INVOICE_MANAGER));
        users.save(new UserEntity("Viewer", "viewer@example.com", encoder.encode(PASSWORD), UserRole.VIEWER));
        activeClient = clients.save(new Client("CLI-900001", "Atlas Client", null, null, null, null, admin.getId()));
        activeProduct = products.save(new Product("PRD-900001", "Blue Roll", null, new BigDecimal("13"), new BigDecimal("20"), ProductUnit.ROLL, admin.getId()));
        secondProduct = products.save(new Product("PRD-900002", "Delivery", null, new BigDecimal("10"), BigDecimal.ZERO, ProductUnit.SERVICE, admin.getId()));
    }

    @Test void migrationTablesIndexesAndConstraintsAreInstalled() {
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='5'", String.class)).isEqualTo("5");
        for (String table : List.of("contract_sequences", "contracts", "contract_lines"))
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name=?", Integer.class, table)).isOne();
        for (String index : List.of("idx_contracts_contract_code", "idx_contracts_client_id", "idx_contracts_status", "idx_contracts_start_date", "idx_contracts_end_date", "idx_contracts_payment_method", "idx_contracts_created_at", "idx_contract_lines_contract_id", "idx_contract_lines_product_id"))
            assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname=?", Integer.class, index)).isOne();
    }

    @ParameterizedTest @EnumSource(value = UserRole.class, names = {"ADMIN", "INVOICE_MANAGER"})
    void managersCreateNormalizedDraftWithGeneratedCodeLocationDefaultsAndAudit(UserRole role) throws Exception {
        JsonNode result = completed(create(tokenFor(role), activeClient.getId(), true, true));
        assertThat(result.get("createdBy").asText()).isEqualTo(users.findByEmailIgnoreCase(role == UserRole.ADMIN ? "admin@example.com" : "manager@example.com").orElseThrow().getId().toString());
    }

    @Test void createsDraftWithNormalizedAggregate() throws Exception {
        JsonNode body = completed(create(tokenFor(UserRole.ADMIN), activeClient.getId(), true, true));
        assertThat(body.get("contractCode").asText()).matches("CTR-2026-[0-9]{6,}");
        assertThat(body.get("status").asText()).isEqualTo("DRAFT"); assertThat(body.get("usable").asBoolean()).isFalse();
        assertThat(body.at("/client/clientName").asText()).isEqualTo("Atlas Client");
        assertThat(body.at("/lines/0/negotiatedUnitPriceExcludingTax").decimalValue()).isEqualByComparingTo("13.01");
        assertThat(body.at("/lines/0/vatRate").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(body.at("/lines/0/discountRate").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(body.at("/lines/0/minimumQuantity").decimalValue()).isEqualByComparingTo("1.235");
        assertThat(body.at("/lines/0/unitPriceIncludingTaxPreview").decimalValue()).isEqualByComparingTo("15.61");
        assertThat(body.get("createdBy").asText()).isEqualTo(admin.getId().toString());
    }

    @Test void viewerAndAnonymousCannotCreateOrMutate() throws Exception {
        String payload = contractJson(activeClient.getId(), true, true);
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(UserRole.VIEWER))).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/contracts").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        JsonNode contract = completed(create(tokenFor(UserRole.ADMIN), activeClient.getId(), true, true)); String viewer = tokenFor(UserRole.VIEWER);
        mvc.perform(put("/api/v1/contracts/{id}", contract.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content(updateJson(contract))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/contracts/{id}/activate", contract.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/contracts/{id}/cancel", contract.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
    }

    @Test void validatesDatesDuplicatesUnknownResourcesAndRates() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(contractJson(activeClient.getId(), true, true).replace("2027-07-31", "2025-07-31"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(contractJson(activeClient.getId(), true, true).replace("\"lines\":[", "\"lines\":[" + lineJson(activeProduct.getId(), null) + ","))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(contractJson(UUID.randomUUID(), true, true))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(contractJson(activeClient.getId(), true, true).replace(activeProduct.getId().toString(), UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(contractJson(activeClient.getId(), true, true).replace("19.995", "101"))).andExpect(status().isBadRequest());
    }

    @Test void listsReadsSearchesFiltersSortsAndRejectsInvalidQueries() throws Exception {
        String adminToken = tokenFor(UserRole.ADMIN); JsonNode created = completed(create(adminToken, activeClient.getId(), true, true)); String viewer = tokenFor(UserRole.VIEWER);
        mvc.perform(get("/api/v1/contracts/{id}", created.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.lines.length()").value(2));
        for (String query : List.of("search=" + created.get("contractCode").asText(), "search=atlas", "clientId=" + activeClient.getId(), "status=DRAFT", "paymentMethod=CHECK", "startDateFrom=2026-01-01&startDateTo=2026-12-31", "usable=false"))
            mvc.perform(get("/api/v1/contracts?" + query).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String query : List.of("size=101", "sort=client,asc", "sort=createdAt,sideways", "status=BAD", "paymentMethod=CRYPTO", "startDateFrom=2027-01-01&startDateTo=2026-01-01"))
            mvc.perform(get("/api/v1/contracts?" + query).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/contracts/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isNotFound());
    }

    @Test void updatesDraftReconcilesLinesAndRejectsStaleForeignAndActiveUpdates() throws Exception {
        String token = tokenFor(UserRole.ADMIN); JsonNode created = completed(create(token, activeClient.getId(), true, false)); String id = created.get("id").asText();
        String update = updateJson(created); JsonNode updated = body(mvc.perform(put("/api/v1/contracts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk()).andReturn());
        assertThat(updated.get("contractCode").asText()).isEqualTo(created.get("contractCode").asText()); assertThat(updated.get("lines").size()).isEqualTo(2);
        mvc.perform(put("/api/v1/contracts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://invoice-management.local/problems/contract-concurrency"));
        JsonNode other = completed(create(token, activeClient.getId(), true, false));
        String stolen = updateJson(updated).replace(updated.at("/lines/0/id").asText(), other.at("/lines/0/id").asText());
        mvc.perform(put("/api/v1/contracts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(stolen)).andExpect(status().isConflict());
        JsonNode activated = activate(token, id, updated.get("version").asLong());
        mvc.perform(put("/api/v1/contracts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(updateJson(activated))).andExpect(status().isConflict());
    }

    @Test void activatesPricesAndCancelsWithAuditAndIdempotency() throws Exception {
        String token = tokenFor(UserRole.ADMIN); JsonNode draft = completed(create(token, activeClient.getId(), true, true)); String id = draft.get("id").asText();
        JsonNode active = activate(token, id, 0); assertThat(active.get("status").asText()).isEqualTo("ACTIVE"); assertThat(active.get("usable").asBoolean()).isTrue(); assertThat(active.get("activatedBy").asText()).isEqualTo(admin.getId().toString());
        mvc.perform(get("/api/v1/contracts/{id}/pricing", id).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(2)).andExpect(jsonPath("$.products[0].unitPriceIncludingTaxPreview").value(15.61));
        mvc.perform(post("/api/v1/contracts/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isBadRequest());
        JsonNode cancelled = body(mvc.perform(post("/api/v1/contracts/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" Agreement ended \",\"version\":1}")).andExpect(status().isOk()).andReturn());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED"); assertThat(cancelled.get("cancellationReason").asText()).isEqualTo("Agreement ended");
        mvc.perform(post("/api/v1/contracts/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Again\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("Agreement ended"));
        mvc.perform(get("/api/v1/contracts/{id}/pricing", id).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/contracts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
    }

    @Test void activationValidatesLinesClientProductAndPastEnd() throws Exception {
        String token = tokenFor(UserRole.ADMIN); JsonNode empty = completed(create(token, activeClient.getId(), false, false));
        mvc.perform(post("/api/v1/contracts/{id}/activate", empty.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
        JsonNode inactiveProduct = completed(create(token, activeClient.getId(), true, false)); Product disabled = products.findById(activeProduct.getId()).orElseThrow(); disabled.setStatus(false, admin.getId()); products.saveAndFlush(disabled);
        mvc.perform(post("/api/v1/contracts/{id}/activate", inactiveProduct.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
        Product enabledAgain = products.findById(activeProduct.getId()).orElseThrow(); enabledAgain.setStatus(true, admin.getId()); products.saveAndFlush(enabledAgain);
        Client inactiveClient = clients.findById(activeClient.getId()).orElseThrow(); inactiveClient.setStatus(false, admin.getId()); clients.saveAndFlush(inactiveClient);
        mvc.perform(post("/api/v1/contracts/{id}/activate", inactiveProduct.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
    }

    @Test void createsCodesConcurrentlyAndExpiresOnlyPastContractsIdempotently() throws Exception {
        String token = tokenFor(UserRole.ADMIN); ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<String>> calls = IntStream.range(0, 10).mapToObj(i -> (Callable<String>) () -> completed(create(token, activeClient.getId(), true, false)).get("contractCode").asText()).toList();
            List<String> codes = new ArrayList<>(); for (Future<String> future : pool.invokeAll(calls)) codes.add(future.get(30, TimeUnit.SECONDS));
            assertThat(codes).hasSize(10).doesNotHaveDuplicates().allMatch(code -> code.matches("CTR-2026-[0-9]{6,}"));
        } finally { pool.shutdownNow(); }
        String id = contracts.findAll().getFirst().getId().toString();
        jdbc.update("update contracts set status='ACTIVE', start_date='2025-01-01', end_date='2026-08-02' where id=?::uuid", id);
        assertThat(expiration.expireEligible()).isOne(); assertThat(expiration.expireEligible()).isZero();
        assertThat(jdbc.queryForObject("select status from contracts where id=?::uuid", String.class, id)).isEqualTo("EXPIRED");
    }

    @Test void databaseConstraintsRejectInvalidContractAndLineRows() {
        UUID contract = UUID.randomUUID();
        String contractSql = "insert into contracts(id,contract_code,company_id,client_id,start_date,end_date,payment_method,default_discount_rate,status,version,created_by,updated_by,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,0,?,?,now(),now())";
        assertThatThrownBy(() -> jdbc.update(contractSql, contract, "CTR-2026-999991", activeClient.getCompanyId(), activeClient.getId(), LocalDate.of(2026,2,1), LocalDate.of(2026,1,1), "CHECK", 0, "DRAFT", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(contractSql, UUID.randomUUID(), "CTR-2026-999992", activeClient.getCompanyId(), activeClient.getId(), LocalDate.of(2026,1,1), null, "CRYPTO", 0, "DRAFT", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update(contractSql, contract, "CTR-2026-999993", activeClient.getCompanyId(), activeClient.getId(), LocalDate.of(2026,1,1), null, "CHECK", 0, "DRAFT", admin.getId(), admin.getId());
        String lineSql = "insert into contract_lines(id,contract_id,product_id,negotiated_unit_price_excluding_tax,vat_rate,discount_rate,minimum_quantity,version,created_at,updated_at) values (?,?,?,?,?,?,?,0,now(),now())";
        assertThatThrownBy(() -> jdbc.update(lineSql, UUID.randomUUID(), contract, activeProduct.getId(), -1, 20, 0, 1)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(lineSql, UUID.randomUUID(), contract, activeProduct.getId(), 1, 101, 0, 1)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(lineSql, UUID.randomUUID(), contract, activeProduct.getId(), 1, 20, 101, 1)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(lineSql, UUID.randomUUID(), contract, activeProduct.getId(), 1, 20, 0, 0)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private MvcResult create(String token, UUID clientId, boolean lines, boolean multiple) throws Exception {
        return mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(contractJson(clientId, lines, multiple)))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern(".*/api/v1/contracts/[0-9a-f-]+"))).andReturn();
    }
    private JsonNode completed(MvcResult result) throws Exception { return body(result); }
    private JsonNode activate(String token, String id, long version) throws Exception { return body(mvc.perform(post("/api/v1/contracts/{id}/activate", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}")).andExpect(status().isOk()).andReturn()); }
    private String contractJson(UUID clientId, boolean lines, boolean multiple) {
        String values = lines ? lineJson(activeProduct.getId(), null) + (multiple ? "," + lineJson(secondProduct.getId(), "1") : "") : "";
        return "{\"clientId\":\"" + clientId + "\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-07-31\",\"paymentMethod\":\"CHECK\",\"defaultDiscountRate\":5,\"notes\":\" Annual agreement \",\"lines\":[" + values + "]}";
    }
    private String lineJson(UUID productId, String discount) { return "{\"productId\":\"" + productId + "\",\"negotiatedUnitPriceExcludingTax\":13.005,\"vatRate\":19.995," + (discount == null ? "" : "\"discountRate\":" + discount + ",") + "\"minimumQuantity\":1.2345,\"notes\":\" Negotiated \"}"; }
    private String updateJson(JsonNode contract) {
        JsonNode line = contract.at("/lines/0");
        return "{\"clientId\":\"" + activeClient.getId() + "\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-07-31\",\"paymentMethod\":\"BANK_TRANSFER\",\"defaultDiscountRate\":3,\"notes\":\"Updated\",\"version\":" + contract.get("version").asLong() + ",\"lines\":[{\"id\":\"" + line.get("id").asText() + "\",\"productId\":\"" + activeProduct.getId() + "\",\"negotiatedUnitPriceExcludingTax\":14,\"vatRate\":20,\"discountRate\":3,\"version\":" + line.get("version").asLong() + "}," + lineJson(secondProduct.getId(), "3") + "]}";
    }
    private String tokenFor(UserRole role) throws Exception { String email = switch (role) { case APP_ADMIN, ADMIN -> "admin@example.com"; case INVOICE_MANAGER -> "manager@example.com"; case VIEWER -> "viewer@example.com"; }; return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isOk()).andReturn()).get("accessToken").asText(); }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private static String bearer(String token) { return "Bearer " + token; }
}
