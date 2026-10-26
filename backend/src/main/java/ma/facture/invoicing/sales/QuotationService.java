package ma.facture.invoicing.sales;
import ma.facture.invoicing.common.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.service.InvoiceService;
import ma.facture.invoicing.invoice.settlement.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
public class QuotationService {
    private final QuotationRepository quotes;
    private final CommercialTermsService terms;
    private final InvoiceService invoices;
    private final InvoiceSettlementService settlements;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public QuotationService(QuotationRepository quotes, CommercialTermsService terms, InvoiceService invoices,
            InvoiceSettlementService settlements, JdbcTemplate jdbc, Clock clock) {
        this.quotes = quotes; this.terms = terms; this.invoices = invoices; this.settlements = settlements; this.jdbc = jdbc; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public Page<Quotation> list(int page, AuthenticatedUser actor) {
        if (page < 0) throw new ValidationException("Page invalide");
        return quotes.findAllByCompanyIdOrderByCreatedAtDesc(actor.companyId(), PageRequest.of(page, 20));
    }
    @Transactional(readOnly = true)
    public Quotation get(UUID id, AuthenticatedUser actor) { return quotes.findByIdAndCompanyId(id, actor.companyId()).orElseThrow(() -> new NotFoundException("Devis introuvable")); }
    @Transactional
    public Quotation create(InvoiceCreateRequest request, LocalDate validUntil, AuthenticatedUser actor) {
        if (validUntil.isBefore(request.invoiceDate())) throw new ValidationException("La validité ne peut pas précéder la date du devis");
        CommercialTerms prepared = terms.prepare(request, actor);
        int year = request.invoiceDate().getYear();
        Long sequence = jdbc.queryForObject("""
            INSERT INTO quote_sequences(company_id, sequence_year, last_value) VALUES (?, ?, 1)
            ON CONFLICT(company_id, sequence_year) DO UPDATE SET last_value = quote_sequences.last_value + 1
            RETURNING last_value
            """, Long.class, actor.companyId(), year);
        if (sequence == null || sequence < 1) throw new IllegalStateException("Séquence des devis invalide");
        return quotes.saveAndFlush(new Quotation(actor.companyId(), "DV-%d-%06d".formatted(year, sequence), request.invoiceDate(), validUntil, prepared, actor.id(), Instant.now(clock)));
    }
    @Transactional
    public InvoiceResponse convert(UUID id, long version, LocalDate dueDate, AuthenticatedUser actor) {
        Quotation quote = quotes.lock(id, actor.companyId()).orElseThrow(() -> new NotFoundException("Devis introuvable"));
        if (quote.getInvoiceId() != null) return invoices.get(quote.getInvoiceId(), actor);
        check(quote, version);
        if (quote.getValidUntil().isBefore(LocalDate.now(clock))) throw new ConflictException("Ce devis a expiré. Créez un nouveau devis pour confirmer les conditions");
        InvoiceResponse invoice = invoices.create(quote.getTerms().invoice(LocalDate.now(clock)), actor);
        if (dueDate != null) invoice = settlements.dueDate(invoice.id(), new SettlementDtos.DueDateRequest(invoice.version(), dueDate), actor);
        quote.convert(invoice.id()); quotes.saveAndFlush(quote);
        return invoice;
    }
    @Transactional
    public Quotation cancel(UUID id, long version, AuthenticatedUser actor) {
        Quotation quote = quotes.lock(id, actor.companyId()).orElseThrow(() -> new NotFoundException("Devis introuvable"));
        check(quote, version); quote.cancel(); return quotes.saveAndFlush(quote);
    }
    private static void check(Quotation quote, long version) {
        if (quote.getVersion() != version) throw new ConflictException("Ce devis a été modifié. Rechargez la page");
        if (!quote.getStatus().equals("OPEN")) throw new ConflictException("Ce devis n’est plus ouvert");
    }
}
