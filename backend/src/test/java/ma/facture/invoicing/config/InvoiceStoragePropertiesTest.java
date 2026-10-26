package ma.facture.invoicing.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceStoragePropertiesTest {
    @Test
    void acceptsConfiguredPath() {
        var path = Path.of("storage", "invoices");
        assertThat(new InvoiceStorageProperties(path).path()).isEqualTo(path);
    }

    @Test
    void rejectsMissingPath() {
        assertThatThrownBy(() -> new InvoiceStorageProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le chemin de stockage des factures doit être configuré");
    }
}
