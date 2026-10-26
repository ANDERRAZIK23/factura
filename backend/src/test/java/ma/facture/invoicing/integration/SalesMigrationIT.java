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
class SalesMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Test void preservesHistoricPaidInvoiceAndRecordsUnknownPaymentDateExplicitly() {
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway.target("11").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID company = UUID.fromString("00000000-0000-0000-0000-000000000001"), user = UUID.randomUUID(), client = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("insert into users(id,company_id,full_name,email,password_hash,role,enabled,version,created_at,updated_at) values (?,?,?,?,?,'ADMIN',true,0,now(),now())",user,company,"Legacy Admin","legacy-sales@example.com","hash");
        jdbc.update("insert into clients(id,company_id,client_code,client_name,active,created_by,updated_by,version,created_at,updated_at) values (?,?,?,'Legacy Client',true,?,?,0,now(),now())",client,company,"CLI-990001",user,user);
        jdbc.update("""
            insert into invoices(id,company_id,invoice_number,invoice_date,client_id,created_by_user_id,updated_by,
                validator_user_id,payment_method,status,subtotal_excluding_tax,taxable_amount,total_vat,total_including_tax,
                client_code_snapshot,client_name_snapshot,payment_method_snapshot,pdf_file_path,validated_at,version,created_at,updated_at)
            values (?,?, 'FA-2026-000001','2026-08-01',?,?,?,?,'CHECK','PAID',100,100,20,120,
                'CLI-990001','Legacy Client','CHECK','legacy/unchanged.pdf',now(),4,now(),now())
            """,invoice,company,client,user,user,user);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        assertThat(jdbc.queryForObject("select paid_amount from invoices where id=?",BigDecimal.class,invoice)).isEqualByComparingTo("120");
        assertThat(jdbc.queryForObject("select status from invoices where id=?",String.class,invoice)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select version from invoices where id=?",Long.class,invoice)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select pdf_file_path from invoices where id=?",String.class,invoice)).isEqualTo("legacy/unchanged.pdf");
        assertThat(jdbc.queryForObject("select entry_type from invoice_payments where invoice_id=?",String.class,invoice)).isEqualTo("LEGACY");
        assertThat(jdbc.queryForObject("select payment_date is null from invoice_payments where invoice_id=?",Boolean.class,invoice)).isTrue();
    }
}
