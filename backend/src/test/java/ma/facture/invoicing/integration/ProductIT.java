package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.*;
import ma.facture.invoicing.auth.RefreshTokenRepository;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ProductIT {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "AdminPassword123!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword); registry.add("app.security.jwt-secret", () -> SECRET);
        registry.add("app.security.issuer", () -> "test-issuer"); registry.add("app.security.audience", () -> "test-audience");
        registry.add("app.security.initial-admin-name", () -> "Bootstrap Admin"); registry.add("app.security.initial-admin-email", () -> "bootstrap@example.com");
        registry.add("app.security.initial-admin-password", () -> PASSWORD);
    }

    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired ProductRepository products;
    @Autowired ClientRepository clients; @Autowired RefreshTokenRepository refreshTokens; @Autowired PasswordEncoder encoder; @Autowired JdbcTemplate jdbc;
    private UserEntity admin;

    @BeforeEach void seed() {
        products.deleteAll(); clients.deleteAll(); refreshTokens.deleteAll(); users.deleteAll();
        admin = users.save(new UserEntity("Admin", "admin@example.com", encoder.encode(PASSWORD), UserRole.ADMIN));
        users.save(new UserEntity("Manager", "manager@example.com", encoder.encode(PASSWORD), UserRole.INVOICE_MANAGER));
        users.save(new UserEntity("Viewer", "viewer@example.com", encoder.encode(PASSWORD), UserRole.VIEWER));
    }

    @Test void migrationSequenceIndexesAndConstraintsAreInstalled() {
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='4'", String.class)).isEqualTo("4");
        assertThat(jdbc.queryForObject("select nextval('product_code_sequence')", Long.class)).isPositive();
        for (String index : List.of("idx_products_product_name", "idx_products_unit", "idx_products_active", "idx_products_created_at"))
            assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname=?", Integer.class, index)).isOne();
    }

    @ParameterizedTest @EnumSource(value = UserRole.class, names = {"ADMIN", "INVOICE_MANAGER"})
    void writersCreateNormalizedProductsWithGeneratedCodeLocationAndAudit(UserRole role) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(role)))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson("  Blue Roll  ", "13.005", "19.995", "ROLL")))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern(".*/api/v1/products/[0-9a-f-]+")))
                .andExpect(jsonPath("$.productCode").value(org.hamcrest.Matchers.matchesPattern("PRD-[0-9]{6,}")))
                .andExpect(jsonPath("$.productName").value("Blue Roll")).andExpect(jsonPath("$.unitPriceExcludingTax").value(13.01))
                .andExpect(jsonPath("$.defaultVatRate").value(20.00)).andExpect(jsonPath("$.unitPriceIncludingTax").value(15.61))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0)).andReturn();
        UUID actor = users.findByEmailIgnoreCase(role == UserRole.ADMIN ? "admin@example.com" : "manager@example.com").orElseThrow().getId();
        assertThat(body(result).get("createdBy").asText()).isEqualTo(actor.toString());
    }

    @Test void viewerAndAnonymousCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(UserRole.VIEWER))).contentType(MediaType.APPLICATION_JSON)
                .content(productJson("Viewer Product", "1", "0", "PIECE"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(productJson("Anonymous", "1", "0", "PIECE"))).andExpect(status().isUnauthorized());
    }

    @Test void validatesFinancialFieldsNameAndUnitWithProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(UserRole.ADMIN))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\" \",\"unitPriceExcludingTax\":-1,\"defaultVatRate\":101}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.productName").exists()).andExpect(jsonPath("$.fieldErrors.unitPriceExcludingTax").exists())
                .andExpect(jsonPath("$.fieldErrors.defaultVatRate").exists()).andExpect(jsonPath("$.fieldErrors.unit").exists());
    }

    @Test void createsUniqueCodesConcurrentlyWithoutLosingRequests() throws Exception {
        String token = tokenFor(UserRole.ADMIN); ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> work = IntStream.range(0, 16).mapToObj(i -> (Callable<String>) () -> body(create(token, "Concurrent Product " + i, String.valueOf(i + 1), "20", "PIECE")).get("productCode").asText()).toList();
            List<String> codes = new ArrayList<>(); for (Future<String> result : executor.invokeAll(work)) codes.add(result.get(30, TimeUnit.SECONDS));
            assertThat(codes).hasSize(16).doesNotHaveDuplicates().allMatch(code -> code.matches("PRD-[0-9]{6,}"));
            assertThat(products.count()).isEqualTo(16);
        } finally { executor.shutdownNow(); }
    }

    @Test void getsListsSearchesFiltersPaginatesAndSorts() throws Exception {
        String adminToken = tokenFor(UserRole.ADMIN); JsonNode roll = body(create(adminToken, "Atlas Roll", "13", "20", "ROLL"));
        create(adminToken, "Beta Service", "100", "0", "SERVICE"); updateStatus(adminToken, roll.get("id").asText(), false, 0);
        String viewer = tokenFor(UserRole.VIEWER);
        mvc.perform(get("/api/v1/products/{id}", roll.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isOk());
        for (String query : List.of("search=" + roll.get("productCode").asText(), "search=atlas", "active=false", "unit=ROLL", "minPrice=90&maxPrice=110"))
            mvc.perform(get("/api/v1/products?" + query).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products?search=reference").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products?page=0&size=1&sort=unitPriceExcludingTax,desc").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidFiltersSortingAndOversizedPages() throws Exception {
        String token = tokenFor(UserRole.VIEWER);
        for (String query : List.of("size=101", "sort=description,asc", "sort=productName,sideways", "unit=BOX", "minPrice=-1", "minPrice=10&maxPrice=1"))
            mvc.perform(get("/api/v1/products?" + query).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isBadRequest());
        for (String field : List.of("productCode", "productName", "unitPriceExcludingTax", "defaultVatRate", "unit", "active", "createdAt", "updatedAt"))
            mvc.perform(get("/api/v1/products?sort=" + field + ",desc").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
    }

    @Test void updatesWithoutAllowingCodeStatusOrAuditInjectionAndDetectsStaleVersions() throws Exception {
        String token = tokenFor(UserRole.ADMIN); JsonNode created = body(create(token, "Original Product", "10", "20", "PIECE")); String id = created.get("id").asText();
        String payload = "{\"productName\":\"Updated Product\",\"description\":\"Updated\",\"unitPriceExcludingTax\":20.555,\"defaultVatRate\":10.555,\"unit\":\"KG\",\"version\":0,\"productCode\":\"PRD-999999\",\"active\":false,\"createdBy\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(put("/api/v1/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productCode").value(created.get("productCode").asText())).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.unitPriceExcludingTax").value(20.56)).andExpect(jsonPath("$.defaultVatRate").value(10.56)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://invoice-management.local/problems/product-concurrency"));
    }

    @Test void writersChangeStatusIdempotentlyAndViewerCannotMutate() throws Exception {
        String token = tokenFor(UserRole.ADMIN); JsonNode created = body(create(token, "Status Product", "10", "20", "PIECE"));
        JsonNode inactive = body(updateStatus(token, created.get("id").asText(), false, 0)); assertThat(inactive.get("version").asLong()).isEqualTo(1);
        updateStatus(token, created.get("id").asText(), false, 0); updateStatus(token, created.get("id").asText(), true, 1);
        String viewer = tokenFor(UserRole.VIEWER);
        mvc.perform(put("/api/v1/products/{id}", created.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(productUpdateJson("Forbidden", 2))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/products/{id}/status", created.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"version\":2}")).andExpect(status().isForbidden());
    }

    @Test void databaseConstraintsRejectInvalidRows() {
        String sql = "insert into products(id,product_code,company_id,product_name,unit_price_excluding_tax,default_vat_rate,unit,active,version,created_by,updated_by,created_at,updated_at) values (?,?,?,?,?,?,?,true,0,?,?,now(),now())";
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "BAD", admin.getCompanyId(), "Name", 1, 20, "PIECE", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "PRD-800001", admin.getCompanyId(), " ", 1, 20, "PIECE", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "PRD-800002", admin.getCompanyId(), "Name", -1, 20, "PIECE", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "PRD-800003", admin.getCompanyId(), "Name", 1, 101, "PIECE", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "PRD-800004", admin.getCompanyId(), "Name", 1, 20, "BOX", admin.getId(), admin.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void noPhysicalDeleteEndpointExists() throws Exception {
        String token = tokenFor(UserRole.ADMIN); JsonNode product = body(create(token, "Keep Product", "1", "0", "OTHER"));
        mvc.perform(delete("/api/v1/products/{id}", product.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isMethodNotAllowed());
        assertThat(products.count()).isOne();
    }

    private String tokenFor(UserRole role) throws Exception {
        String email = switch (role) { case APP_ADMIN, ADMIN -> "admin@example.com"; case INVOICE_MANAGER -> "manager@example.com"; case VIEWER -> "viewer@example.com"; };
        return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }
    private MvcResult create(String token, String name, String price, String vat, String unit) throws Exception {
        return mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(productJson(name, price, vat, unit))).andExpect(status().isCreated()).andReturn();
    }
    private MvcResult updateStatus(String token, String id, boolean active, long version) throws Exception {
        return mvc.perform(patch("/api/v1/products/{id}/status", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("active", active, "version", version)))).andExpect(status().isOk()).andReturn();
    }
    private String productJson(String name, String price, String vat, String unit) throws Exception {
        return json.writeValueAsString(Map.of("productName", name, "description", "Reference product", "unitPriceExcludingTax", price, "defaultVatRate", vat, "unit", unit));
    }
    private String productUpdateJson(String name, long version) throws Exception { return json.writeValueAsString(Map.of("productName", name, "unitPriceExcludingTax", 10, "defaultVatRate", 20, "unit", "PIECE", "version", version)); }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private static String bearer(String token) { return "Bearer " + token; }
}
