package ma.facture.invoicing.invoice.pdf;

import ma.facture.invoicing.company.TenantDefaults;
import ma.facture.invoicing.config.InvoiceStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

class InvoicePdfStorageTest {
    @TempDir Path temporaryDirectory;

    @Test void storesUnderAnnualServerPathAndResolvesRegularFile() throws Exception {
        InvoicePdfStorage storage = storage();
        byte[] content = "%PDF-1.5 test".getBytes(StandardCharsets.US_ASCII);

        String relative = storage.store("FA-2026-000097", content);

        assertThat(relative).isEqualTo(TenantDefaults.DEFAULT_COMPANY_ID + "/2026/FA-2026-000097.pdf");
        assertThat(Files.readAllBytes(storage.resolve(relative))).isEqualTo(content);
    }

    @Test void rejectsUnsafeEmptyDuplicateAndMissingFiles() {
        InvoicePdfStorage storage = storage();
        assertThatThrownBy(() -> storage.store("../unsafe", new byte[]{1})).isInstanceOf(InvoicePdfException.class);
        assertThatThrownBy(() -> storage.store("FA-2026-000001", new byte[0])).isInstanceOf(InvoicePdfException.class);
        storage.store("FA-2026-000001", new byte[]{1});
        assertThatThrownBy(() -> storage.store("FA-2026-000001", new byte[]{2}))
                .isInstanceOf(InvoicePdfException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> storage.resolve("../outside.pdf")).isInstanceOf(InvoicePdfNotFoundException.class);
        assertThatThrownBy(() -> storage.resolve(temporaryDirectory.resolve("absolute.pdf").toString()))
                .isInstanceOf(InvoicePdfNotFoundException.class);
        assertThatThrownBy(() -> storage.resolve("2026/missing.pdf")).isInstanceOf(InvoicePdfNotFoundException.class);
        assertThatThrownBy(() -> storage.resolve(null)).isInstanceOf(InvoicePdfNotFoundException.class);
    }

    @Test void removesPublishedFileWhenSurroundingTransactionRollsBack() {
        InvoicePdfStorage storage = storage();
        TransactionSynchronizationManager.initSynchronization();
        String relative = null;
        try {
            relative = storage.store("FA-2027-000001", new byte[]{1, 2, 3});
            assertThat(storage.resolve(relative)).exists();
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        String rolledBackPath = relative;
        assertThatThrownBy(() -> storage.resolve(rolledBackPath)).isInstanceOf(InvoicePdfNotFoundException.class);
    }

    private InvoicePdfStorage storage() { return new InvoicePdfStorage(new InvoiceStorageProperties(temporaryDirectory)); }
}
