package ma.facture.invoicing.client.service;

import ma.facture.invoicing.client.exception.ClientCodeGenerationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientCodeGeneratorTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ClientCodeGenerator generator = new ClientCodeGenerator(jdbc);

    @Test void formatsSequenceValues() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L, 999_999L);
        assertThat(generator.generate()).isEqualTo("CLI-000001");
        assertThat(generator.generate()).isEqualTo("CLI-999999");
    }

    @Test void rejectsMissingInvalidAndExhaustedValues() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null, 0L, 1_000_000L);
        assertThatThrownBy(generator::generate).isInstanceOf(ClientCodeGenerationException.class);
        assertThatThrownBy(generator::generate).isInstanceOf(ClientCodeGenerationException.class);
        assertThatThrownBy(generator::generate).isInstanceOf(ClientCodeGenerationException.class);
    }
}
