package ma.facture.invoicing.product.service;

import ma.facture.invoicing.product.exception.ProductCodeGenerationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductCodeGenerator {
    private final JdbcTemplate jdbc;
    public ProductCodeGenerator(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String generate() {
        Long value = jdbc.queryForObject("SELECT nextval('product_code_sequence')", Long.class);
        if (value == null || value < 1) throw new ProductCodeGenerationException();
        return "PRD-%06d".formatted(value);
    }
}
