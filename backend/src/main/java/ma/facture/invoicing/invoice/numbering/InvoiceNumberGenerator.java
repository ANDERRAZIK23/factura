package ma.facture.invoicing.invoice.numbering;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.Locale;
import java.util.UUID;
import ma.facture.invoicing.company.TenantDefaults;

@Service
public class InvoiceNumberGenerator {
    private final InvoiceSequenceRepository sequences;
    private final Clock clock;

    public InvoiceNumberGenerator(InvoiceSequenceRepository sequences, Clock clock) {
        this.sequences = sequences;
        this.clock = clock;
    }

    @Transactional
    public String generateForValidation() {
        return generateForValidation(TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Transactional
    public String generateForValidation(UUID companyId) {
        Instant now = Instant.now(clock);
        int year = Year.now(clock).getValue();
        if (year < 2000) throw new IllegalStateException("L’année de numérotation des factures doit être égale ou postérieure à 2000");
        long sequence = sequences.nextValue(companyId, year, now);
        if (sequence < 1) throw new IllegalStateException("La séquence des factures doit être positive");
        return String.format(Locale.ROOT, "FA-%d-%06d", year, sequence);
    }
}
