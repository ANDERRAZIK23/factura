package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.repository.ContractSequenceRepository;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.Locale;

@Service
public class ContractCodeGenerator {
    private final ContractSequenceRepository sequences;
    private final Clock clock;
    public ContractCodeGenerator(ContractSequenceRepository sequences, Clock clock) { this.sequences = sequences; this.clock = clock; }
    public String generate() {
        int year = LocalDate.now(clock).getYear();
        long value = sequences.nextValue(year);
        if (value < 1) throw new IllegalStateException("Contract sequence returned an invalid value");
        return String.format(Locale.ROOT, "CTR-%d-%06d", year, value);
    }
}
