package ma.facture.invoicing.sales;
import ma.facture.invoicing.common.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.entity.InvoiceStatus;
import ma.facture.invoicing.invoice.service.InvoiceService;
import ma.facture.invoicing.invoice.settlement.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.*;
import ma.facture.invoicing.company.CompanyRepository;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
public class RecurringInvoiceService {
    private final RecurringInvoiceRepository schedules;
    private final CommercialTermsService terms;
    private final InvoiceService invoices;
    private final InvoiceSettlementService settlements;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public RecurringInvoiceService(RecurringInvoiceRepository schedules, CommercialTermsService terms, InvoiceService invoices,
            InvoiceSettlementService settlements, UserRepository users, CompanyRepository companies, JdbcTemplate jdbc, Clock clock) {
        this.schedules = schedules; this.terms = terms; this.invoices = invoices; this.settlements = settlements;
        this.users = users; this.companies = companies; this.jdbc = jdbc; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public Page<RecurringInvoice> list(int page, AuthenticatedUser actor) {
        if (page < 0) throw new ValidationException("Page invalide");
        return schedules.findAllByCompanyIdOrderByCreatedAtDesc(actor.companyId(), PageRequest.of(page, 20));
    }
    @Transactional
    public RecurringInvoice create(RecurringInvoiceController.Create request, AuthenticatedUser actor) {
        InvoiceResponse source = invoices.get(request.invoiceId(), actor);
        if (source.status() == InvoiceStatus.CANCELLED) throw new ConflictException("Une facture annulée ne peut pas servir de modèle");
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) throw new ValidationException("La fin doit suivre la première occurrence");
        InvoiceCreateRequest document = new InvoiceCreateRequest(request.startDate(), source.client().id(), null,
                source.paymentMethod(), source.customerOrderReference(), source.lines().stream().map(l -> new InvoiceLineRequest(l.productId(), l.quantity(), l.unitPriceExcludingTax(), l.vatRate(), l.discountRate())).toList());
        return schedules.saveAndFlush(new RecurringInvoice(actor.companyId(), request.title().strip(), terms.prepare(document, actor),
                request.startDate(), request.endDate(), request.intervalMonths(), request.paymentTermsDays(), actor.id(), Instant.now(clock)));
    }
    @Transactional
    public RecurringInvoice toggle(UUID id, long version, boolean enabled, AuthenticatedUser actor) {
        RecurringInvoice schedule = owned(id, actor);
        if (schedule.getVersion() != version) throw new ConflictException("La récurrence a été modifiée. Rechargez la page");
        if (enabled && schedule.getEndDate() != null && schedule.getNextDate().isAfter(schedule.getEndDate())) throw new ConflictException("La période de récurrence est terminée");
        schedule.setEnabled(enabled, actor.id()); return schedules.saveAndFlush(schedule);
    }
    @Transactional
    public InvoiceResponse generate(UUID id, long version, AuthenticatedUser actor) {
        RecurringInvoice schedule = owned(id, actor);
        if (schedule.getVersion() != version) throw new ConflictException("La récurrence a été modifiée. Rechargez la page");
        return generate(schedule, actor);
    }
    @Transactional
    public void process(UUID id, long version) {
        RecurringInvoice schedule = schedules.lock(id).orElseThrow(() -> new NotFoundException("Récurrence introuvable"));
        if (schedule.getVersion() != version) return;
        if (!schedule.isEnabled() || schedule.getNextDate().isAfter(LocalDate.now(clock))) return;
        var user = users.findByIdAndCompanyId(schedule.getExecutionUserId(), schedule.getCompanyId())
                .filter(u -> u.isEnabled() && u.getRole() != UserRole.VIEWER).orElseThrow(() -> new ConflictException("Le responsable de la récurrence ne dispose plus des droits nécessaires"));
        if (companies.findById(schedule.getCompanyId()).filter(c -> c.isEnabled()).isEmpty()) throw new ConflictException("L’entreprise est désactivée");
        generate(schedule, new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), user.getCompanyId()));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID id, long version, String message) {
        var schedule = schedules.lock(id).orElseThrow(() -> new NotFoundException("Récurrence introuvable"));
        // Preserve any correction or generation committed after this job selected the schedule.
        if (schedule.getVersion() != version) return;
        schedule.fail(message.length() > 500 ? message.substring(0, 500) : message);
        schedules.saveAndFlush(schedule);
    }
    private InvoiceResponse generate(RecurringInvoice schedule, AuthenticatedUser actor) {
        if (!schedule.isEnabled()) throw new ConflictException("Cette récurrence est en pause ou terminée");
        if (schedule.getNextDate().isAfter(LocalDate.now(clock))) throw new ConflictException("La prochaine occurrence n’est pas encore arrivée");
        if (schedule.getEndDate() != null && schedule.getNextDate().isAfter(schedule.getEndDate())) throw new ConflictException("La période de récurrence est terminée");
        // Recheck active clients and products before preparing a monthly draft.
        terms.prepare(schedule.getTerms().invoice(schedule.getNextDate()), actor);
        LocalDate period = schedule.getNextDate();
        InvoiceResponse invoice = invoices.create(schedule.getTerms().invoice(period), actor);
        invoice = settlements.dueDate(invoice.id(), new SettlementDtos.DueDateRequest(invoice.version(), period.plusDays(schedule.getPaymentTermsDays())), actor);
        jdbc.update("insert into recurring_invoice_runs(schedule_id, scheduled_date, invoice_id) values (?, ?, ?)", schedule.getId(), period, invoice.id());
        schedule.generated(invoice.id()); schedules.saveAndFlush(schedule);
        return invoice;
    }
    private RecurringInvoice owned(UUID id, AuthenticatedUser actor) {
        return schedules.lockOwned(id, actor.companyId()).orElseThrow(() -> new NotFoundException("Récurrence introuvable"));
    }
}
