package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.facture.invoicing.auth.*;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class AuthenticationIT {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "AdminPassword123!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt-secret", () -> SECRET); registry.add("app.security.issuer", () -> "test-issuer"); registry.add("app.security.audience", () -> "test-audience");
        registry.add("app.security.initial-admin-name", () -> "Bootstrap Admin"); registry.add("app.security.initial-admin-email", () -> "bootstrap@example.com"); registry.add("app.security.initial-admin-password", () -> PASSWORD);
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder encoder; @Autowired JdbcTemplate jdbc;
    private UserEntity admin; private UserEntity manager; private UserEntity viewer;

    @BeforeEach void seed() {
        refreshTokens.deleteAll(); users.deleteAll();
        admin = users.save(new UserEntity("Admin","admin@example.com",encoder.encode(PASSWORD),UserRole.ADMIN));
        manager = users.save(new UserEntity("Manager","manager@example.com",encoder.encode(PASSWORD),UserRole.INVOICE_MANAGER));
        viewer = users.save(new UserEntity("Viewer","viewer@example.com",encoder.encode(PASSWORD),UserRole.VIEWER));
    }

    @Test void healthIsPublicAndDoesNotExposeDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
    }
    @Test void missingTokenReturnsProblem401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
    @Test void loginAndMeSucceedWithoutExposingPasswordHash() throws Exception {
        var login = login(" ADMIN@EXAMPLE.COM ", PASSWORD); String token = accessToken(login);
        login.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.user.role").value("ADMIN")).andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,"Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com")).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void invalidPasswordAndUnknownEmailHaveSameGenericFailure() throws Exception {
        String known = login("admin@example.com","wrong").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = login("unknown@example.com","wrong").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(known).get("detail").asText()).isEqualTo(json.readTree(unknown).get("detail").asText()).isEqualTo("Adresse e-mail ou mot de passe incorrect");
    }
    @Test void disabledUserCannotLoginOrRefresh() throws Exception {
        var login = login("viewer@example.com",PASSWORD).andReturn(); String cookie = cookie(login);
        viewer.setEnabled(false); users.saveAndFlush(viewer);
        login("viewer@example.com",PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie(cookie))).andExpect(status().isUnauthorized());
    }
    @Test void refreshRotatesAndRejectsReplay() throws Exception {
        var login = login("admin@example.com",PASSWORD).andReturn(); String oldCookie = cookie(login);
        var refresh = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie(oldCookie))).andExpect(status().isOk()).andReturn();
        assertThat(cookie(refresh)).isNotEqualTo(oldCookie); assertThat(accessToken(refresh)).isNotBlank();
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie(oldCookie))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie(cookie(refresh)))).andExpect(status().isUnauthorized());
    }
    @Test void logoutRevokesAndClearsCookie() throws Exception {
        var login = login("admin@example.com",PASSWORD).andReturn(); String value = cookie(login);
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie(value))).andExpect(status().isNoContent()).andExpect(header().string(HttpHeaders.SET_COOKIE,org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie(value))).andExpect(status().isUnauthorized());
    }
    @Test void adminCreatesUserAndDuplicateEmailIsRejected() throws Exception {
        String token = accessToken(login("admin@example.com",PASSWORD)); String body = "{\"fullName\":\"New Manager\",\"email\":\"NEW@example.com\",\"password\":\"StrongPassword123!\",\"role\":\"INVOICE_MANAGER\"}";
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("new@example.com")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }
    @Test void invalidUserFieldsReturnFieldErrors() throws Exception {
        String token = accessToken(login("admin@example.com",PASSWORD));
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"weak\",\"role\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists()).andExpect(jsonPath("$.errors.password").exists());
    }
    @ParameterizedTest @EnumSource(value=UserRole.class,names={"INVOICE_MANAGER","VIEWER"})
    void nonAdminsReceive403ForUserManagement(UserRole role) throws Exception {
        String email = role == UserRole.VIEWER ? "viewer@example.com" : "manager@example.com"; String token = accessToken(login(email,PASSWORD));
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION,"Bearer "+token)).andExpect(status().isForbidden()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
    @Test void expiredAndModifiedAccessTokensAreRejected() throws Exception {
        String expired = Jwts.builder().subject(admin.getId().toString()).issuer("test-issuer").audience().add("test-audience").and()
                .expiration(Date.from(Instant.now().minusSeconds(5))).claim("email",admin.getEmail()).claim("role","ADMIN")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,"Bearer "+expired)).andExpect(status().isUnauthorized());
        String valid = accessToken(login("admin@example.com",PASSWORD)); String modified = valid.substring(0,valid.length()-2)+(valid.endsWith("a")?"b":"a");
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,"Bearer "+modified)).andExpect(status().isUnauthorized());
    }
    @Test void unapprovedCorsOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header(HttpHeaders.ORIGIN,"https://evil.example").header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
    @Test void postgresRoleConstraintRejectsInvalidRole() {
        assertThatThrownBy(() -> jdbc.update("insert into users(id,company_id,full_name,email,password_hash,role,enabled,version,created_at,updated_at) values (?,?,?,?,?,?,true,0,now(),now())",
                UUID.randomUUID(), admin.getCompanyId(), "Invalid","invalid@example.com","hash","ROOT")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private ResultActions login(String email,String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",email,"password",password)))); }
    private String accessToken(ResultActions actions) throws Exception { return accessToken(actions.andExpect(status().isOk()).andReturn()); }
    private String accessToken(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText(); }
    private String cookie(MvcResult result) { String header=result.getResponse().getHeader(HttpHeaders.SET_COOKIE); return header.substring(header.indexOf('=')+1,header.indexOf(';')); }
    private jakarta.servlet.http.Cookie cookie(String value) { return new jakarta.servlet.http.Cookie(RefreshCookieService.COOKIE_NAME,value); }
}
