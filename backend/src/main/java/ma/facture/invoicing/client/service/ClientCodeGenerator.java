package ma.facture.invoicing.client.service;

import ma.facture.invoicing.client.exception.ClientCodeGenerationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ClientCodeGenerator {
    static final long MAX_CODE_VALUE = 999_999L;
    private final JdbcTemplate jdbc;

    public ClientCodeGenerator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String generate() {
        Long value = jdbc.queryForObject("SELECT nextval('client_code_sequence')", Long.class);
        if (value == null || value < 1 || value > MAX_CODE_VALUE) throw new ClientCodeGenerationException();
        return "CLI-%06d".formatted(value);
    }
}
