package ma.facture.invoicing.invoice.numbering;

import ma.facture.invoicing.company.TenantDefaults;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvoiceNumberGeneratorTest {
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    private final InvoiceSequenceRepository sequences = mock(InvoiceSequenceRepository.class);
    private final InvoiceNumberGenerator generator = new InvoiceNumberGenerator(sequences, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void formatsPaddedAndLargerSequencesForValidationYear() {
        when(sequences.nextValue(TenantDefaults.DEFAULT_COMPANY_ID, 2026, NOW)).thenReturn(1L, 97L, 1_000_000L);

        assertThat(generator.generateForValidation()).isEqualTo("FA-2026-000001");
        assertThat(generator.generateForValidation()).isEqualTo("FA-2026-000097");
        assertThat(generator.generateForValidation()).isEqualTo("FA-2026-1000000");
        verify(sequences, org.mockito.Mockito.times(3)).nextValue(TenantDefaults.DEFAULT_COMPANY_ID, 2026, NOW);
    }

    @Test void rejectsInvalidRepositorySequence() {
        when(sequences.nextValue(TenantDefaults.DEFAULT_COMPANY_ID, 2026, NOW)).thenReturn(0L);

        assertThatThrownBy(generator::generateForValidation)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("positive");
    }

    @Test void rejectsYearOutsideDatabaseConstraintBeforeAllocation() {
        InvoiceNumberGenerator invalidYearGenerator = new InvoiceNumberGenerator(sequences,
                Clock.fixed(Instant.parse("1999-12-31T10:15:30Z"), ZoneOffset.UTC));

        assertThatThrownBy(invalidYearGenerator::generateForValidation)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2000");
        verifyNoInteractions(sequences);
    }
}
