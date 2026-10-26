package ma.facture.invoicing.invoice.pdf;

import ma.facture.invoicing.company.TenantDefaults;
import ma.facture.invoicing.company.Company;
import ma.facture.invoicing.company.CompanyRepository;
import ma.facture.invoicing.company.CompanyBrandingRepository;
import ma.facture.invoicing.invoice.design.InvoiceDesignRepository;
import ma.facture.invoicing.invoice.entity.Invoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoicePdfServiceTest {
    private final FrenchAmountInWordsService words = mock(FrenchAmountInWordsService.class);
    private final InvoicePdfRenderer renderer = mock(InvoicePdfRenderer.class);
    private final InvoicePdfStorage storage = mock(InvoicePdfStorage.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final InvoiceDesignRepository designs = mock(InvoiceDesignRepository.class);
    private final DesignedInvoicePdfRenderer designedRenderer = mock(DesignedInvoicePdfRenderer.class);
    private final CompanyBrandingRepository branding = mock(CompanyBrandingRepository.class);
    private final InvoicePdfService service = new InvoicePdfService(words, renderer, storage, companies, designs, designedRenderer, branding);
    private final Invoice invoice = mock(Invoice.class);

    @Test void generatesUsingAuthoritativeTotalAndServerNumber() {
        when(invoice.getTotalIncludingTax()).thenReturn(new BigDecimal("31200.00"));
        when(invoice.getInvoiceNumber()).thenReturn("FA-2026-000097");
        when(invoice.getCompanyId()).thenReturn(TenantDefaults.DEFAULT_COMPANY_ID);
        Company company = new Company("ENT-000001", "Atlas SARL", null, null, null, "001527304000014");
        when(companies.findById(TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(java.util.Optional.of(company));
        when(words.convert(new BigDecimal("31200.00"))).thenReturn("Trente et un mille deux cents dirhams");
        when(renderer.render(invoice, "Trente et un mille deux cents dirhams", company)).thenReturn(new byte[]{1, 2});
        when(storage.store(eq(TenantDefaults.DEFAULT_COMPANY_ID), eq("FA-2026-000097"), any(byte[].class)))
                .thenReturn(TenantDefaults.DEFAULT_COMPANY_ID + "/2026/FA-2026-000097.pdf");

        assertThat(service.generate(invoice)).isEqualTo(new GeneratedInvoicePdf(
                TenantDefaults.DEFAULT_COMPANY_ID + "/2026/FA-2026-000097.pdf", "Trente et un mille deux cents dirhams"));
    }

    @Test void resolvesDownloadAndRejectsDraftWithoutStoredPath() throws Exception {
        assertThatThrownBy(() -> service.download(invoice)).isInstanceOf(InvoicePdfNotFoundException.class);
        Path file = Files.createTempFile("invoice-pdf-service", ".pdf");
        try {
            Files.write(file, new byte[]{1, 2, 3});
            when(invoice.getPdfFilePath()).thenReturn("2026/FA-2026-000097.pdf");
            when(invoice.getInvoiceNumber()).thenReturn("FA-2026-000097");
            when(storage.resolve("2026/FA-2026-000097.pdf")).thenReturn(file);
            InvoicePdfDownload download = service.download(invoice);
            assertThat(download.path()).isEqualTo(file);
            assertThat(download.filename()).isEqualTo("FA-2026-000097.pdf");
            assertThat(download.size()).isEqualTo(3);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
