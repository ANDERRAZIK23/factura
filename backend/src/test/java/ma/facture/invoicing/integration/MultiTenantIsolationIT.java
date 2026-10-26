package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.facture.invoicing.auth.RefreshTokenRepository;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.company.Company;
import ma.facture.invoicing.company.CompanyBrandingRepository;
import ma.facture.invoicing.company.CompanyRepository;
import ma.facture.invoicing.contract.entity.Contract;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.contract.repository.ContractRepository;
import ma.facture.invoicing.invoice.entity.Invoice;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.product.entity.Product;
import ma.facture.invoicing.product.entity.ProductUnit;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.user.UserEntity;
import ma.facture.invoicing.user.UserRepository;
import ma.facture.invoicing.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MultiTenantIsolationIT {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "AdminPassword123!";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
    @Autowired PasswordEncoder encoder;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserRepository users;
    @Autowired InvoiceRepository invoices;
    @Autowired ContractRepository contracts;
    @Autowired ProductRepository products;
    @Autowired ClientRepository clients;
    @Autowired CompanyRepository companies;
    @Autowired CompanyBrandingRepository branding;
    @Autowired ma.facture.invoicing.invoice.settlement.InvoicePaymentRepository invoicePayments;
    @Autowired ma.facture.invoicing.invoice.settlement.InvoiceReminderRepository invoiceReminders;

    private Company companyA;
    private Company companyB;
    private UserEntity adminA;
    private UserEntity adminB;
    private Client clientA;
    private Client clientB;
    private Product productA;
    private Product productB;
    private Contract contractA;
    private Contract contractB;
    private Invoice invoiceA;
    private Invoice invoiceB;

    @BeforeEach
    void seedTwoIndependentCompanies() {
        invoiceReminders.deleteAll();
        invoicePayments.deleteAll();
        invoices.deleteAll();
        contracts.deleteAll();
        products.deleteAll();
        clients.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        branding.deleteAll();
        companies.deleteAll();

        companyA = companies.save(new Company("ENT-100001", "Company A", "Casablanca", null, "a@example.com", "111111111111111"));
        companyB = companies.save(new Company("ENT-100002", "Company B", "Rabat", null, "b@example.com", "222222222222222"));
        adminA = users.save(new UserEntity("Administrator A", "admin-a@example.com", encoder.encode(PASSWORD), UserRole.ADMIN, companyA.getId()));
        adminB = users.save(new UserEntity("Administrator B", "admin-b@example.com", encoder.encode(PASSWORD), UserRole.ADMIN, companyB.getId()));

        clientA = clients.save(new Client("CLI-100001", "Client A", null, "Casablanca", "client-a@example.com", null, adminA.getId(), companyA.getId()));
        clientB = clients.save(new Client("CLI-100002", "Client B", null, "Rabat", "client-b@example.com", null, adminB.getId(), companyB.getId()));
        productA = products.save(new Product("PRD-100001", "Product A", null, new BigDecimal("100.00"), new BigDecimal("20.00"), ProductUnit.PIECE, adminA.getId(), companyA.getId()));
        productB = products.save(new Product("PRD-100002", "Product B", null, new BigDecimal("200.00"), new BigDecimal("20.00"), ProductUnit.PIECE, adminB.getId(), companyB.getId()));

        contractA = contracts.save(new Contract("CTR-2026-100001", clientA, LocalDate.of(2026, 1, 1), null, PaymentMethod.BANK_TRANSFER, BigDecimal.ZERO, "Contract A", adminA.getId()));
        contractB = contracts.save(new Contract("CTR-2026-100002", clientB, LocalDate.of(2026, 1, 1), null, PaymentMethod.CHECK, BigDecimal.ZERO, "Contract B", adminB.getId()));
        invoiceA = invoices.save(new Invoice(LocalDate.of(2026, 8, 1), clientA, null, PaymentMethod.BANK_TRANSFER, "ORDER-A", adminA.getId()));
        invoiceB = invoices.save(new Invoice(LocalDate.of(2026, 8, 2), clientB, null, PaymentMethod.CHECK, "ORDER-B", adminB.getId()));
    }

    @Test
    void companyAOnlyListsAndReadsItsOwnSensitiveRecords() throws Exception {
        String token = login(adminA.getEmail());

        assertTenantList(token, "/api/v1/clients", "clientName", "Client A", "Client B");
        assertTenantList(token, "/api/v1/products", "productName", "Product A", "Product B");
        assertTenantList(token, "/api/v1/contracts", "contractCode", "CTR-2026-100001", "CTR-2026-100002");
        assertTenantList(token, "/api/v1/invoices", "client.clientName", "Client A", "Client B");
        assertTenantList(token, "/api/v1/users", "email", "admin-a@example.com", "admin-b@example.com");

        assertForeignRecordIsHidden(token, "/api/v1/clients/" + clientB.getId());
        assertForeignRecordIsHidden(token, "/api/v1/products/" + productB.getId());
        assertForeignRecordIsHidden(token, "/api/v1/contracts/" + contractB.getId());
        assertForeignRecordIsHidden(token, "/api/v1/invoices/" + invoiceB.getId());
        assertForeignRecordIsHidden(token, "/api/v1/users/" + adminB.getId());

        mvc.perform(get("/api/v1/company").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Company A"));
    }

    @Test
    void companyBOnlyListsAndReadsItsOwnSensitiveRecords() throws Exception {
        String token = login(adminB.getEmail());

        assertTenantList(token, "/api/v1/clients", "clientName", "Client B", "Client A");
        assertTenantList(token, "/api/v1/products", "productName", "Product B", "Product A");
        assertTenantList(token, "/api/v1/contracts", "contractCode", "CTR-2026-100002", "CTR-2026-100001");
        assertTenantList(token, "/api/v1/invoices", "client.clientName", "Client B", "Client A");
        assertTenantList(token, "/api/v1/users", "email", "admin-b@example.com", "admin-a@example.com");

        assertForeignRecordIsHidden(token, "/api/v1/clients/" + clientA.getId());
        assertForeignRecordIsHidden(token, "/api/v1/products/" + productA.getId());
        assertForeignRecordIsHidden(token, "/api/v1/contracts/" + contractA.getId());
        assertForeignRecordIsHidden(token, "/api/v1/invoices/" + invoiceA.getId());
        assertForeignRecordIsHidden(token, "/api/v1/users/" + adminA.getId());
    }

    @Test
    void companyARejectsForeignClientSelectionWhenCreatingInvoicesAndContracts() throws Exception {
        String token = login(adminA.getEmail());
        Map<String, Object> invoice = Map.of(
                "invoiceDate", "2026-08-08", "clientId", clientB.getId(), "paymentMethod", "CHECK", "lines", List.of());
        mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(invoice)))
                .andExpect(status().isNotFound());

        Map<String, Object> contract = Map.of(
                "clientId", clientB.getId(), "startDate", "2026-08-08", "paymentMethod", "CHECK", "lines", List.of());
        mvc.perform(post("/api/v1/contracts").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(contract)))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformAdministratorManagesSelectedCompanyUsersAndBrandingWithoutBreakingTenantIsolation() throws Exception {
        UserEntity platform = users.save(new UserEntity("Platform Administrator", "platform@example.com",
                encoder.encode(PASSWORD), UserRole.APP_ADMIN, companyA.getId()));
        String platformToken = login(platform.getEmail());

        Map<String, Object> newViewer = Map.of("fullName", "Company B Viewer", "email", "viewer-b@example.com",
                "password", PASSWORD, "role", "VIEWER");
        mvc.perform(post("/api/v1/companies/{id}/users", companyB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(newViewer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(companyB.getId().toString()))
                .andExpect(jsonPath("$.role").value("VIEWER"));

        String companyBUsers = mvc.perform(get("/api/v1/companies/{id}/users", companyB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String companyAUsers = mvc.perform(get("/api/v1/companies/{id}/users", companyA.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(companyBUsers).contains("viewer-b@example.com");
        assertThat(companyAUsers).doesNotContain("viewer-b@example.com");

        mvc.perform(post("/api/v1/companies/{id}/users", companyB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(adminA.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(newViewer)))
                .andExpect(status().isForbidden());

        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", png);
        mvc.perform(multipart("/api/v1/companies/{id}/branding", companyB.getId()).file(logo)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Company B"));

        mvc.perform(get("/api/v1/company/branding").header(HttpHeaders.AUTHORIZATION, bearer(login(adminB.getEmail()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.logoDataUrl").isString());
        mvc.perform(get("/api/v1/companies/{id}/branding", companyB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(adminA.getEmail()))))
                .andExpect(status().isForbidden());
    }

    private void assertTenantList(String token, String path, String field, String expected, String forbidden) throws Exception {
        MvcResult result = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]." + field).value(expected)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(forbidden);
    }

    private void assertForeignRecordIsHidden(String token, String path) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNotFound());
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
