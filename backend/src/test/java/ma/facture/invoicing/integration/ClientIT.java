package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.*;
import ma.facture.invoicing.auth.RefreshTokenRepository;
import ma.facture.invoicing.client.repository.ClientRepository;
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
class ClientIT {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "AdminPassword123!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
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
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ClientRepository clients;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    private UserEntity admin;

    @BeforeEach void seed() {
        clients.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        admin = users.save(new UserEntity("Admin", "admin@example.com", encoder.encode(PASSWORD), UserRole.ADMIN));
        users.save(new UserEntity("Manager", "manager@example.com", encoder.encode(PASSWORD), UserRole.INVOICE_MANAGER));
        users.save(new UserEntity("Viewer", "viewer@example.com", encoder.encode(PASSWORD), UserRole.VIEWER));
    }

    @Test void migrationAndSequenceAreInstalled() {
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='3'", String.class)).isEqualTo("3");
        assertThat(jdbc.queryForObject("select nextval('client_code_sequence')", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname='uq_clients_company_ice_not_null'", Integer.class)).isOne();
    }

    @ParameterizedTest @EnumSource(value = UserRole.class, names = {"ADMIN", "INVOICE_MANAGER"})
    void writersCreateClientsWithGeneratedCodeLocationAndAudit(UserRole role) throws Exception {
        String token = tokenFor(role);
        MvcResult result = mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(clientJson("  Atlas SARL  ", "001 527 304 000 014")))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern(".*/api/v1/clients/[0-9a-f-]+")))
                .andExpect(jsonPath("$.clientCode").value(org.hamcrest.Matchers.matchesPattern("CLI-[0-9]{6}")))
                .andExpect(jsonPath("$.clientName").value("Atlas SARL"))
                .andExpect(jsonPath("$.email").value("sales@example.com"))
                .andExpect(jsonPath("$.ice").value("001527304000014"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID expectedActor = users.findByEmailIgnoreCase(role == UserRole.ADMIN ? "admin@example.com" : "manager@example.com").orElseThrow().getId();
        assertThat(body.get("createdBy").asText()).isEqualTo(expectedActor.toString());
        assertThat(body.get("updatedBy").asText()).isEqualTo(expectedActor.toString());
    }

    @Test void viewerAndAnonymousCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(UserRole.VIEWER)))
                .contentType(MediaType.APPLICATION_JSON).content(clientJson("Viewer Client", null))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON).content(clientJson("Anonymous Client", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test void validatesNameEmailIceAndReturnsFieldErrors() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\" \",\"email\":\"bad\",\"ice\":\"123\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.clientName").exists()).andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.ice").exists()).andExpect(jsonPath("$.detail").value("Un ou plusieurs champs sont invalides"));
    }

    @Test void duplicateIceReturnsSafeConflictAndDoesNotCreateSecondClient() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        create(token, "First Client", "001527304000014");
        mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("Second Client", "001527304000014")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://invoice-management.local/problems/duplicate-client-ice"))
                .andExpect(jsonPath("$.detail").value("Un client avec cet ICE existe déjà"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("constraint"))));
        assertThat(clients.count()).isOne();
    }

    @Test void createsUniqueCodesConcurrentlyWithoutLosingRequests() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> work = IntStream.range(0, 16).mapToObj(index -> (Callable<String>) () -> {
                MvcResult result = create(token, "Concurrent Client " + index, null);
                return json.readTree(result.getResponse().getContentAsString()).get("clientCode").asText();
            }).toList();
            List<String> codes = new ArrayList<>();
            for (Future<String> result : executor.invokeAll(work)) codes.add(result.get(30, TimeUnit.SECONDS));
            assertThat(codes).hasSize(16).doesNotHaveDuplicates().allMatch(code -> code.matches("CLI-[0-9]{6}"));
            assertThat(clients.count()).isEqualTo(16);
        } finally { executor.shutdownNow(); }
    }

    @Test void getsClientAndReturns404ForMissingClient() throws Exception {
        String token = tokenFor(UserRole.VIEWER);
        String adminToken = tokenFor(UserRole.ADMIN);
        JsonNode created = body(create(adminToken, "Get Client", null));
        mvc.perform(get("/api/v1/clients/{id}", created.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientCode").value(created.get("clientCode").asText()));
        mvc.perform(get("/api/v1/clients/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test void listsPaginatesSearchesEverySupportedFieldAndFiltersStatus() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        JsonNode atlas = body(create(token, "Atlas Trading", "111111111111111"));
        createWithDetails(token, "Beta Services", "+212 6 77-88", "BETA@EXAMPLE.COM", "222222222222222");
        updateStatus(token, atlas.get("id").asText(), false, atlas.get("version").asLong());

        mvc.perform(get("/api/v1/clients?page=0&size=1&sort=clientName,asc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
        for (String search : List.of(atlas.get("clientCode").asText(), "ATLAS", "+212 6 77", "beta@example.com", "222222222222222")) {
            mvc.perform(get("/api/v1/clients").param("search", search).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(get("/api/v1/clients?active=false").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].clientName").value("Atlas Trading"));
        mvc.perform(get("/api/v1/clients?active=true").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].clientName").value("Beta Services"));
    }

    @Test void enforcesSortAllowlistAndMaximumPageSize() throws Exception {
        String token = tokenFor(UserRole.VIEWER);
        mvc.perform(get("/api/v1/clients?sort=ice,asc").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/clients?sort=clientName,drop table").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/clients?size=101").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isBadRequest());
        for (String field : List.of("clientCode", "clientName", "createdAt", "updatedAt", "active"))
            mvc.perform(get("/api/v1/clients?sort=" + field + ",desc").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
    }

    @Test void updatesClientWithoutAllowingCodeOrStatusManipulation() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        JsonNode created = body(create(token, "Original Client", null));
        String payload = "{\"clientName\":\"Updated Client\",\"phoneNumber\":\"+212 6 00\",\"address\":\"Rabat\",\"email\":\"UPDATED@EXAMPLE.COM\",\"ice\":null,\"version\":0,\"clientCode\":\"CLI-999999\",\"active\":false,\"createdBy\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(put("/api/v1/clients/{id}", created.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientName").value("Updated Client"))
                .andExpect(jsonPath("$.clientCode").value(created.get("clientCode").asText())).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.email").value("updated@example.com")).andExpect(jsonPath("$.version").value(1));
    }

    @Test void staleUpdateReturns409() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        JsonNode created = body(create(token, "Versioned Client", null));
        String id = created.get("id").asText();
        update(token, id, "First Update", 0).andExpect(status().isOk());
        update(token, id, "Stale Update", 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://invoice-management.local/problems/client-concurrency"));
    }

    @Test void writersChangeStatusIdempotentlyAndViewerCannotMutate() throws Exception {
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.INVOICE_MANAGER)) {
            clients.deleteAll();
            String writer = tokenFor(role);
            JsonNode created = body(create(writer, role + " Client", null));
            MvcResult deactivated = updateStatus(writer, created.get("id").asText(), false, 0);
            long version = body(deactivated).get("version").asLong();
            assertThat(version).isEqualTo(1);
            updateStatus(writer, created.get("id").asText(), false, 0);
            updateStatus(writer, created.get("id").asText(), true, version);
        }
        String adminToken = tokenFor(UserRole.ADMIN);
        JsonNode client = body(create(adminToken, "Viewer Protected", null));
        String viewer = tokenFor(UserRole.VIEWER);
        update(viewer, client.get("id").asText(), "Forbidden", 0).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/clients/{id}/status", client.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"version\":0}" )).andExpect(status().isForbidden());
    }

    @Test void noPhysicalDeleteEndpointExists() throws Exception {
        String token = tokenFor(UserRole.ADMIN);
        JsonNode client = body(create(token, "Keep Client", null));
        mvc.perform(delete("/api/v1/clients/{id}", client.get("id").asText()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isMethodNotAllowed());
        assertThat(clients.count()).isOne();
    }

    @Test void databaseConstraintsRejectBlankNamesInvalidIceAndInvalidCodes() {
        String sql = "insert into clients(id,client_code,company_id,client_name,ice,active,version,created_by,updated_by,created_at,updated_at) values (?,?,?,?,?,true,0,?,?,now(),now())";
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "CLI-800001", admin.getCompanyId(), "  ", null, admin.getId(), admin.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "CLI-800002", admin.getCompanyId(), "Name", "123", admin.getId(), admin.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "BAD-CODE", admin.getCompanyId(), "Name", null, admin.getId(), admin.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private String tokenFor(UserRole role) throws Exception {
        String email = switch (role) { case APP_ADMIN, ADMIN -> "admin@example.com"; case INVOICE_MANAGER -> "manager@example.com"; case VIEWER -> "viewer@example.com"; };
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isOk()).andReturn();
        return body(result).get("accessToken").asText();
    }

    private MvcResult create(String token, String name, String ice) throws Exception {
        return mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(clientJson(name, ice))).andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createWithDetails(String token, String name, String phone, String email, String ice) throws Exception {
        return mvc.perform(post("/api/v1/clients").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientName", name, "phoneNumber", phone, "email", email, "ice", ice))))
                .andExpect(status().isCreated()).andReturn();
    }

    private ResultActions update(String token, String id, String name, long version) throws Exception {
        return mvc.perform(put("/api/v1/clients/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientName", name, "version", version))));
    }

    private MvcResult updateStatus(String token, String id, boolean active, long version) throws Exception {
        return mvc.perform(patch("/api/v1/clients/{id}/status", id).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("active", active, "version", version)))).andExpect(status().isOk()).andReturn();
    }

    private String clientJson(String name, String ice) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clientName", name); data.put("phoneNumber", "+212 5 00-00"); data.put("address", "Casablanca"); data.put("email", "SALES@EXAMPLE.COM");
        if (ice != null) data.put("ice", ice);
        return json.writeValueAsString(data);
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private static String bearer(String token) { return "Bearer " + token; }
}
