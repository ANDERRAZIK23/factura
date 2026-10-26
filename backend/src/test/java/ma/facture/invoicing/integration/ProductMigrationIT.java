package ma.facture.invoicing.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ProductMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test void additiveMigrationPreservesAndHardensLegacyProductRows() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("3").load().migrate();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        UUID user = UUID.randomUUID(); UUID product = UUID.randomUUID();
        jdbc.update("insert into users(id,full_name,email,password_hash,role,enabled,version,created_at,updated_at) values (?,?,?,?,?,true,0,now(),now())",
                user, "Legacy Admin", "legacy@example.com", "hash", "ADMIN");
        jdbc.update("insert into products(id,product_code,product_name,description,unit_price_excluding_tax,default_vat_rate,unit,active,version,created_at,updated_at) values (?,?,?,?,?,?,?,true,0,now(),now())",
                product, "PRD-000125", "Legacy Product", "Preserved", new BigDecimal("12.50"), new BigDecimal("20.00"), "PIECE");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("4").load().migrate();

        assertThat(jdbc.queryForObject("select product_name from products where id=?", String.class, product)).isEqualTo("Legacy Product");
        assertThat(jdbc.queryForObject("select created_by from products where id=?", UUID.class, product)).isEqualTo(user);
        assertThat(jdbc.queryForObject("select nextval('product_code_sequence')", Long.class)).isEqualTo(126L);
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success and version='4'", String.class)).isEqualTo("4");
    }
}
