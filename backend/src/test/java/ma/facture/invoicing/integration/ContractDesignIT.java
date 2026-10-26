package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.facture.invoicing.company.*;
import ma.facture.invoicing.contract.design.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserRole;
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
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ContractDesignIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @TempDir static Path storage;
    private static final AtomicInteger CODES = new AtomicInteger(400000);
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
    @Autowired ContractDesignRepository designs;
    private Company company, other;
    @BeforeEach void seed() {
        int code = CODES.getAndAdd(2);
        company = companies.saveAndFlush(new Company("ENT-" + code, "Atlas Services", null, null, null, null));
        other = companies.saveAndFlush(new Company("ENT-" + (code + 1), "Autre entreprise", null, null, null, null));
    }
    @Test void isolatesDesignsAndRejectsStaleVersions() throws Exception {
        mvc.perform(get(url(company)).with(as(UserRole.APP_ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.customized").value(false));
        save(company, null, "Contrat Atlas").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        save(other, null, "Contrat indépendant").andExpect(status().isOk());
        save(company, 0L, "Contrat révisé").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        save(company, 0L, "Ancienne version").andExpect(status().isConflict());
        save(company, null, "Autre première sauvegarde").andExpect(status().isConflict());
        mvc.perform(get(url(company)).with(as(UserRole.APP_ADMIN))).andExpect(jsonPath("$.settings.title").value("Contrat révisé"));
        mvc.perform(get(url(other)).with(as(UserRole.APP_ADMIN))).andExpect(jsonPath("$.settings.title").value("Contrat indépendant"));
    }
    @Test void restrictsEveryEndpointToApplicationAdminsAndRejectsInvalidContent() throws Exception {
        mvc.perform(get(url(company))).andExpect(status().isUnauthorized());
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.INVOICE_MANAGER, UserRole.VIEWER)) {
            mvc.perform(get(url(company)).with(as(role))).andExpect(status().isForbidden());
            mvc.perform(put(url(company)).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new ContractDesignDtos.Request(settings("Interdit"), null)))).andExpect(status().isForbidden());
            mvc.perform(post(url(company) + "/pdf").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(settings("Interdit")))).andExpect(status().isForbidden());
        }
        String invalid = json.writeValueAsString(new ContractDesignDtos.Request(settings("Invalide"), null)).replace("\"marginMm\":20", "\"marginMm\":0");
        mvc.perform(put(url(company)).with(as(UserRole.APP_ADMIN)).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        var badContent = settings("Invalide");
        ((com.fasterxml.jackson.databind.node.ObjectNode) badContent.content()).put("onclick", "alert(1)");
        mvc.perform(post(url(company) + "/pdf").with(as(UserRole.APP_ADMIN)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(badContent))).andExpect(status().isBadRequest());
        assertThat(designs.findById(company.getId())).isEmpty();
        mvc.perform(get("/api/v1/companies/" + UUID.randomUUID() + "/contract-design").with(as(UserRole.APP_ADMIN))).andExpect(status().isNotFound());
    }
    @Test void exportsCurrentUnsavedContentWithoutPersistingIt() throws Exception {
        byte[] pdf = mvc.perform(post(url(company) + "/pdf").with(as(UserRole.APP_ADMIN)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(settings("Contrat à exporter"))))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(designs.findById(company.getId())).isEmpty();
    }
    @Test void concurrentFirstSavesHaveOneWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> { ready.countDown(); start.await(); return save(company, null, "Concurrent").andReturn().getResponse().getStatus(); };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
    }
    private org.springframework.test.web.servlet.ResultActions save(Company target, Long version, String title) throws Exception {
        return mvc.perform(put(url(target)).with(as(UserRole.APP_ADMIN)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new ContractDesignDtos.Request(settings(title), version))));
    }
    private static String url(Company company) { return "/api/v1/companies/" + company.getId() + "/contract-design"; }
    private static ContractDesignSettings settings(String title) {
        var base = ContractDesignSettings.defaults();
        return new ContractDesignSettings(title, base.paperFormat(), base.orientation(), base.marginMm(), base.showLogo(), base.pageNumbers(), base.content());
    }
    private RequestPostProcessor as(UserRole role) {
        var actor = new AuthenticatedUser(UUID.randomUUID(), "designer@example.com", role, company.getId());
        return authentication(new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
