package ma.facture.invoicing.company;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CompanyCodeGenerator {
    private final JdbcTemplate jdbc;
    public CompanyCodeGenerator(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String generate() {
        Long value = jdbc.queryForObject("SELECT nextval('company_code_sequence')", Long.class);
        if (value == null || value < 1) throw new IllegalStateException("Impossible d’attribuer un code entreprise");
        return "ENT-%06d".formatted(value);
    }
}
