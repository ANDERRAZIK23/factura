package ma.facture.invoicing.invoice.numbering;

import ma.facture.invoicing.company.TenantDefaults;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceSequenceRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final InvoiceSequenceRepository repository = new InvoiceSequenceRepository(jdbc);

    @Test void returnsAtomicUpsertResult() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(TenantDefaults.DEFAULT_COMPANY_ID), eq(2026), eq(Timestamp.from(NOW)))).thenReturn(42L);

        assertThat(repository.nextValue(2026, NOW)).isEqualTo(42L);
        verify(jdbc).queryForObject(anyString(), eq(Long.class), eq(TenantDefaults.DEFAULT_COMPANY_ID), eq(2026), eq(Timestamp.from(NOW)));
    }

    @Test void rejectsNullAndNonPositiveDatabaseResults() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(TenantDefaults.DEFAULT_COMPANY_ID), eq(2026), eq(Timestamp.from(NOW)))).thenReturn(null, 0L);

        assertThatThrownBy(() -> repository.nextValue(2026, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.nextValue(2026, NOW)).isInstanceOf(IllegalStateException.class);
    }
}
