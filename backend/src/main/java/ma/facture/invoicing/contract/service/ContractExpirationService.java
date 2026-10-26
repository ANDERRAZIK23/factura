package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.repository.ContractRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
@ConditionalOnProperty(name = "app.contracts.expiration-job.enabled", havingValue = "true", matchIfMissing = true)
public class ContractExpirationService {
    private final ContractRepository contracts;
    private final Clock clock;
    public ContractExpirationService(ContractRepository contracts, Clock clock) { this.contracts = contracts; this.clock = clock; }
    @Transactional
    public int expireEligible() { return contracts.expireEligible(LocalDate.now(clock), Instant.now(clock)); }

    @Scheduled(cron = "${app.contracts.expiration-job.cron:0 0 1 * * *}")
    public void scheduledExpiration() { expireEligible(); }
}
