package ma.facture.invoicing.invoice.pdf;

import ma.facture.invoicing.company.CompanyRepository;
import ma.facture.invoicing.company.CompanyBranding;
import ma.facture.invoicing.company.CompanyBrandingRepository;
import ma.facture.invoicing.invoice.design.InvoiceDesignRepository;
import ma.facture.invoicing.invoice.entity.Invoice;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;

@Service
public class InvoicePdfService {
    private final FrenchAmountInWordsService words;
    private final InvoicePdfRenderer renderer;
    private final InvoicePdfStorage storage;
    private final CompanyRepository companies;
    private final InvoiceDesignRepository designs;
    private final DesignedInvoicePdfRenderer designedRenderer;
    private final CompanyBrandingRepository branding;

    public InvoicePdfService(FrenchAmountInWordsService words, InvoicePdfRenderer renderer, InvoicePdfStorage storage,
                             CompanyRepository companies, InvoiceDesignRepository designs,
                             DesignedInvoicePdfRenderer designedRenderer, CompanyBrandingRepository branding) {
        this.words = words;
        this.renderer = renderer;
        this.storage = storage;
        this.companies = companies;
        this.designs = designs;
        this.designedRenderer = designedRenderer;
        this.branding = branding;
    }

    public GeneratedInvoicePdf generate(Invoice invoice) {
        String amountInWords = words.convert(invoice.getTotalIncludingTax());
        var company = companies.findById(invoice.getCompanyId())
                .orElseThrow(() -> new InvoicePdfException("L’entreprise associée à la facture est introuvable"));
        var design = designs.findById(invoice.getCompanyId());
        byte[] content;
        if (design.isPresent()) {
            var settings = design.get().getSettings();
            byte[] logo = settings.showLogo() ? branding.findById(invoice.getCompanyId()).map(CompanyBranding::getLogoData).orElse(null) : null;
            content = designedRenderer.render(invoice, amountInWords, company, settings, logo, false);
        } else {
            // Companies without a saved design retain the existing invoice format.
            content = renderer.render(invoice, amountInWords, company);
        }
        return new GeneratedInvoicePdf(storage.store(invoice.getCompanyId(), invoice.getInvoiceNumber(), content), amountInWords);
    }

    public InvoicePdfDownload download(Invoice invoice) {
        if (invoice.getPdfFilePath() == null || invoice.getInvoiceNumber() == null) throw new InvoicePdfNotFoundException();
        var path = storage.resolve(invoice.getPdfFilePath());
        try { return new InvoicePdfDownload(path, invoice.getInvoiceNumber() + ".pdf", Files.size(path)); }
        catch (IOException exception) { throw new InvoicePdfNotFoundException(); }
    }
}
