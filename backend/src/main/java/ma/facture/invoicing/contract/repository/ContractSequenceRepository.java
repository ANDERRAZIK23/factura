package ma.facture.invoicing.contract.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContractSequenceRepository {
    private final JdbcTemplate jdbc;
    public ContractSequenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public long nextValue(int year) {
        Long value = jdbc.queryForObject("""
                INSERT INTO contract_sequences(sequence_year, last_value, version)
                VALUES (?, 1, 0)
                ON CONFLICT (sequence_year) DO UPDATE
                SET last_value = contract_sequences.last_value + 1,
                    version = contract_sequences.version + 1
                RETURNING last_value
                """, Long.class, year);
        if (value == null || value < 1) throw new IllegalStateException("Contract sequence returned an invalid value");
        return value;
    }
}
