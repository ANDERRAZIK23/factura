package ma.facture.invoicing.product.service;

import ma.facture.invoicing.product.exception.ProductCodeGenerationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCodeGeneratorTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProductCodeGenerator generator = new ProductCodeGenerator(jdbc);

    @Test void formatsFirstPaddedAndLargeValuesWithoutTruncating() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L, 25L, 1_000_000L);
        assertThat(generator.generate()).isEqualTo("PRD-000001");
        assertThat(generator.generate()).isEqualTo("PRD-000025");
        assertThat(generator.generate()).isEqualTo("PRD-1000000");
        verify(jdbc, times(3)).queryForObject("SELECT nextval('product_code_sequence')", Long.class);
    }

    @Test void rejectsMissingAndInvalidSequenceValues() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null, 0L);
        assertThatThrownBy(generator::generate).isInstanceOf(ProductCodeGenerationException.class);
        assertThatThrownBy(generator::generate).isInstanceOf(ProductCodeGenerationException.class);
    }
}
