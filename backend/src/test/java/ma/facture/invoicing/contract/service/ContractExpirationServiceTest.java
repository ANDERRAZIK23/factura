package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractExpirationServiceTest {
    @Test void usesFixedBusinessDateAndIsSafeToRepeat() {
        ContractRepository repository = mock(ContractRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneOffset.UTC);
        when(repository.expireEligible(LocalDate.of(2026, 8, 3), Instant.parse("2026-08-03T01:00:00Z"))).thenReturn(2, 0, 0);
        ContractExpirationService service = new ContractExpirationService(repository, clock);
        assertThat(service.expireEligible()).isEqualTo(2);
        assertThat(service.expireEligible()).isZero();
        service.scheduledExpiration();
        verify(repository, times(3)).expireEligible(LocalDate.of(2026, 8, 3), Instant.parse("2026-08-03T01:00:00Z"));
    }
}
