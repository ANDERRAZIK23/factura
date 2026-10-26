package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.facture.invoicing.company.TenantDefaults;
import ma.facture.invoicing.invoice.design.InvoiceDesignSettings;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class InvoiceDesignMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test void promotesLatestModelPerCompanyAndPreservesOriginalsAndAuditData() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("10").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID company = TenantDefaults.DEFAULT_COMPANY_ID, other = UUID.randomUUID(), actor = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, company_code, company_name, created_at, updated_at) VALUES (?, 'ENT-900001', 'Other', NOW(), NOW())", other);
        jdbc.update("INSERT INTO users (id, full_name, email, password_hash, role, company_id, created_at, updated_at) VALUES (?, 'Admin', 'admin@example.com', 'hash', 'APP_ADMIN', ?, NOW(), NOW())", actor, company);
        ObjectMapper json = new ObjectMapper();
        var settings = json.valueToTree(InvoiceDesignSettings.defaults());
        UUID[] clients = {
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                UUID.fromString("00000000-0000-0000-0000-000000000014")};
        for (int index = 0; index < clients.length; index++) {
            UUID scope = index < 2 ? company : other;
            jdbc.update("INSERT INTO clients (id, client_code, client_name, company_id, created_by, updated_by, created_at, updated_at) VALUES (?, ?, 'Client', ?, ?, ?, NOW(), NOW())",
                    clients[index], "CLI-90000" + index, scope, actor, actor);
            ((com.fasterxml.jackson.databind.node.ObjectNode) settings).put("footerText", "Model " + index);
            jdbc.update("INSERT INTO client_invoice_designs (client_id, company_id, settings, version, updated_by, updated_at) VALUES (?, ?, CAST(? AS jsonb), ?, ?, CAST(? AS timestamptz))",
                    clients[index], scope, json.writeValueAsString(settings), index == 0 ? 7 : 2, actor,
                    index == 0 ? "2026-10-18T10:00:00Z" : "2026-10-19T10:00:00Z");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_invoice_designs", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_invoice_designs", Integer.class)).isEqualTo(4);
        ((com.fasterxml.jackson.databind.node.ObjectNode) settings).put("footerText", "Model 1");
        assertThat(json.readTree(jdbc.queryForObject("SELECT settings::text FROM company_invoice_designs WHERE company_id = ?", String.class, company))).isEqualTo(settings);
        assertThat(jdbc.queryForObject("SELECT version FROM company_invoice_designs WHERE company_id = ?", Long.class, company)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM company_invoice_designs WHERE company_id = ?", UUID.class, company)).isEqualTo(actor);
        assertThat(jdbc.queryForObject("SELECT updated_at = '2026-10-19T10:00:00Z'::timestamptz FROM company_invoice_designs WHERE company_id = ?", Boolean.class, company)).isTrue();
        assertThat(jdbc.queryForObject("SELECT settings->>'footerText' FROM company_invoice_designs WHERE company_id = ?", String.class, other)).isEqualTo("Model 2");
        jdbc.update("DELETE FROM clients WHERE id = ?", clients[1]);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_invoice_designs WHERE company_id = ?", Integer.class, company)).isEqualTo(1);
    }
}
