package ma.facture.invoicing.invoice.numbering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import ma.facture.invoicing.company.TenantDefaults;

@Repository
public class InvoiceSequenceRepository {
    private final JdbcTemplate jdbc;

    public InvoiceSequenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public long nextValue(int year, Instant now) {
        return nextValue(TenantDefaults.DEFAULT_COMPANY_ID, year, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long nextValue(UUID companyId, int year, Instant now) {
        Long value = jdbc.queryForObject("""
                INSERT INTO invoice_sequences(company_id, sequence_year, last_value, updated_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (company_id, sequence_year) DO UPDATE
                SET last_value = invoice_sequences.last_value + 1,
                    updated_at = EXCLUDED.updated_at
                RETURNING last_value
                """, Long.class, companyId, year, Timestamp.from(now));
        if (value == null || value < 1) throw new IllegalStateException("La séquence des factures a renvoyé une valeur invalide");
        return value;
    }
}
