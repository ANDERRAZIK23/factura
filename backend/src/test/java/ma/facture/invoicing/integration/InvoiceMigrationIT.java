package ma.facture.invoicing.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class InvoiceMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test void additiveMigrationPreservesLegacyDraftAndBackfillsUpdater() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("5").load().migrate();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        UUID user = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        jdbc.update("insert into users(id,full_name,email,password_hash,role,enabled,version,created_at,updated_at) values (?,?,?,?,?,true,0,now(),now())",
                user, "Legacy Admin", "legacy-invoice@example.com", "hash", "ADMIN");
        jdbc.update("insert into clients(id,client_code,client_name,active,created_by,updated_by,version,created_at,updated_at) values (?,?,?,true,?,?,0,now(),now())",
                client, "CLI-990001", "Legacy Client", user, user);
        jdbc.update("insert into invoices(id,invoice_date,client_id,created_by_user_id,payment_method,status,version,created_at,updated_at) values (?,?,?,?,?,'DRAFT',0,now(),now())",
                invoice, LocalDate.of(2026, 8, 4), client, user, "CHECK");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("6").load().migrate();

        assertThat(jdbc.queryForObject("select status from invoices where id=?", String.class, invoice)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select updated_by from invoices where id=?", UUID.class, invoice)).isEqualTo(user);
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='6'", String.class)).isEqualTo("6");
    }
}
