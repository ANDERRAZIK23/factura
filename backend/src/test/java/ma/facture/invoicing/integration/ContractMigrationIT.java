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
class ContractMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test void additiveMigrationPreservesAndHardensLegacyContractAggregate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("4").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID user = UUID.randomUUID(), client = UUID.randomUUID(), product = UUID.randomUUID(), contract = UUID.randomUUID(), line = UUID.randomUUID();
        jdbc.update("insert into users(id,full_name,email,password_hash,role,enabled,version,created_at,updated_at) values (?,?,?,?,?,true,0,now(),now())", user, "Admin", "legacy@example.com", "hash", "ADMIN");
        jdbc.update("insert into clients(id,client_code,client_name,active,version,created_by,updated_by,created_at,updated_at) values (?,?,?,true,0,?,?,now(),now())", client, "CLI-000001", "Legacy Client", user, user);
        jdbc.update("insert into products(id,product_code,product_name,unit_price_excluding_tax,default_vat_rate,unit,active,version,created_by,updated_by,created_at,updated_at) values (?,?,?,10,20,'PIECE',true,0,?,?,now(),now())", product, "PRD-000001", "Legacy Product", user, user);
        jdbc.update("insert into contracts(id,contract_code,client_id,start_date,end_date,payment_method,default_discount_rate,status,notes,version,created_at,updated_at) values (?,?,?,'2026-01-01','2026-12-31','CHECK',5,'DRAFT','Preserved',0,now(),now())", contract, "CTR-2026-000125", client);
        jdbc.update("insert into contract_lines(id,contract_id,product_id,negotiated_unit_price_excluding_tax,vat_rate,discount_rate,minimum_quantity) values (?,?,?,?,?,?,?)", line, contract, product, new BigDecimal("9.50"), new BigDecimal("20"), new BigDecimal("5"), new BigDecimal("2"));

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).target("5").load().migrate();

        assertThat(jdbc.queryForObject("select notes from contracts where id=?", String.class, contract)).isEqualTo("Preserved");
        assertThat(jdbc.queryForObject("select created_by from contracts where id=?", UUID.class, contract)).isEqualTo(user);
        assertThat(jdbc.queryForObject("select created_at is not null from contract_lines where id=?", Boolean.class, line)).isTrue();
        assertThat(jdbc.queryForObject("select last_value from contract_sequences where sequence_year=2026", Long.class)).isEqualTo(125L);
        jdbc.update("update contract_lines set minimum_quantity=null where id=?", line);
        assertThat(jdbc.queryForObject("select minimum_quantity is null from contract_lines where id=?", Boolean.class, line)).isTrue();
    }
}
