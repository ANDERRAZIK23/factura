package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.repository.ContractSequenceRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractCodeGeneratorTest {
    private final ContractSequenceRepository sequences = mock(ContractSequenceRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC);
    private final ContractCodeGenerator generator = new ContractCodeGenerator(sequences, clock);

    @Test void formatsFirstPaddedAndLargeValuesForCreationYear() {
        when(sequences.nextValue(2026)).thenReturn(1L, 25L, 1_000_000L);
        assertThat(generator.generate()).isEqualTo("CTR-2026-000001");
        assertThat(generator.generate()).isEqualTo("CTR-2026-000025");
        assertThat(generator.generate()).isEqualTo("CTR-2026-1000000");
        verify(sequences, times(3)).nextValue(2026);
    }

    @Test void rejectsInvalidRepositoryValues() {
        when(sequences.nextValue(2026)).thenReturn(0L);
        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid value");
    }
}
