package ma.facture.invoicing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.invoice.storage")
public record InvoiceStorageProperties(Path path) {
    public InvoiceStorageProperties {
        if (path == null) {
            throw new IllegalArgumentException("Le chemin de stockage des factures doit être configuré");
        }
    }
}
