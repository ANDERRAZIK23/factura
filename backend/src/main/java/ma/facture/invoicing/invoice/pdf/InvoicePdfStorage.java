package ma.facture.invoicing.invoice.pdf;

import ma.facture.invoicing.config.InvoiceStorageProperties;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import ma.facture.invoicing.company.TenantDefaults;
import java.util.regex.Pattern;

@Component
public class InvoicePdfStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvoicePdfStorage.class);
    private static final Pattern NUMBER = Pattern.compile("FA-([0-9]{4})-[0-9]{6,}");
    private final Path configuredRoot;

    public InvoicePdfStorage(InvoiceStorageProperties properties) {
        configuredRoot = properties.path().toAbsolutePath().normalize();
    }

    public String store(String invoiceNumber, byte[] content) {
        return store(TenantDefaults.DEFAULT_COMPANY_ID, invoiceNumber, content);
    }

    public String store(UUID companyId, String invoiceNumber, byte[] content) {
        if (content == null || content.length == 0) throw new InvoicePdfException("Generated invoice PDF is empty");
        var matcher = NUMBER.matcher(invoiceNumber == null ? "" : invoiceNumber);
        if (!matcher.matches()) throw new InvoicePdfException("Invoice number is not safe for PDF storage");
        String year = matcher.group(1);
        String filename = invoiceNumber + ".pdf";
        Path temporary = null;
        try {
            Path root = realRoot();
            Path companyDirectory = secureDirectory(root, companyId.toString());
            Path directory = secureDirectory(companyDirectory, year);
            Path destination = directory.resolve(filename);
            temporary = directory.resolve("." + filename + "." + UUID.randomUUID() + ".tmp");
            Files.write(temporary, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.createLink(destination, temporary);
            Files.delete(temporary);
            temporary = null;
            registerRollbackCleanup(destination);
            return companyId + "/" + year + "/" + filename;
        } catch (FileAlreadyExistsException exception) {
            throw new InvoicePdfException("Invoice PDF already exists", exception);
        } catch (IOException exception) {
            throw new InvoicePdfException("Invoice PDF could not be stored", exception);
        } catch (UnsupportedOperationException | SecurityException exception) {
            throw new InvoicePdfException("Invoice PDF storage does not support secure publication", exception);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException exception) { LOGGER.warn("Could not clean temporary invoice PDF path={}", temporary); }
        }
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) throw new InvoicePdfNotFoundException();
        Path supplied;
        try { supplied = Path.of(relativePath); }
        catch (InvalidPathException exception) { throw new InvoicePdfNotFoundException(); }
        if (supplied.isAbsolute()) throw new InvoicePdfNotFoundException();
        try {
            Path root = realRoot();
            Path candidate = root.resolve(supplied).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                throw new InvoicePdfNotFoundException();
            Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(root)) throw new InvoicePdfNotFoundException();
            return real;
        } catch (InvoicePdfNotFoundException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvoicePdfNotFoundException();
        }
    }

    private Path realRoot() throws IOException {
        Files.createDirectories(configuredRoot);
        return configuredRoot.toRealPath();
    }

    private static Path secureDirectory(Path root, String year) throws IOException {
        Path directory = root.resolve(year).normalize();
        if (!directory.startsWith(root)) throw new InvoicePdfException("Invoice PDF directory escapes storage root");
        Files.createDirectories(directory);
        Path real = directory.toRealPath();
        if (!real.startsWith(root)) throw new InvoicePdfException("Invoice PDF directory escapes storage root");
        return real;
    }

    private static void registerRollbackCleanup(Path destination) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                try { Files.deleteIfExists(destination); }
                catch (IOException exception) { LOGGER.error("Could not remove rolled-back invoice PDF path={}", destination); }
            }
        });
    }
}
