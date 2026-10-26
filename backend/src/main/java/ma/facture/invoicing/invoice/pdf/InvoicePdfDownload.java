package ma.facture.invoicing.invoice.pdf;

import java.nio.file.Path;

public record InvoicePdfDownload(Path path, String filename, long size) {}
