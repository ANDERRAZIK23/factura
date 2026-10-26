package ma.facture.invoicing.invoice.settlement;

import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.entity.*;
import ma.facture.invoicing.invoice.exception.*;
import ma.facture.invoicing.invoice.mapper.InvoiceMapper;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.common.ValidationException;
import org.springframework.data.domain.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class InvoiceSettlementService {
    private final InvoiceRepository invoices;
    private final InvoicePaymentRepository payments;
    private final InvoiceReminderRepository reminders;
    private final InvoiceMapper mapper;
    private final Clock clock;
    public InvoiceSettlementService(InvoiceRepository invoices, InvoicePaymentRepository payments,
            InvoiceReminderRepository reminders, InvoiceMapper mapper, Clock clock) {
        this.invoices = invoices; this.payments = payments; this.reminders = reminders; this.mapper = mapper; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<SettlementDtos.Payment> payments(UUID id, AuthenticatedUser actor) {
        find(id, actor);
        return payments.findAllByInvoiceIdOrderByCreatedAtDesc(id).stream().map(SettlementDtos.Payment::from).toList();
    }
    @Transactional
    public InvoiceResponse pay(UUID id, SettlementDtos.PaymentRequest request, AuthenticatedUser actor) {
        Invoice invoice = find(id, actor); version(invoice, request.version());
        if (invoice.getStatus() != InvoiceStatus.VALIDATED) throw new InvoiceConflictException("Seule une facture validée avec un solde restant peut recevoir un règlement");
        if (request.paymentDate().isAfter(LocalDate.now(clock))) throw new ValidationException("La date de règlement ne peut pas être future");
        if (!Set.of(PaymentMethod.CASH, PaymentMethod.CHECK, PaymentMethod.BANK_TRANSFER).contains(request.paymentMethod()))
            throw new ValidationException("Choisissez Espèces, Chèque ou Virement bancaire");
        BigDecimal amount = request.amount().setScale(2);
        if (amount.compareTo(invoice.getOutstandingAmount()) > 0) throw new InvoiceConflictException("Le montant dépasse le solde restant");
        invoice.recordPayment(amount, actor.id());
        payments.save(new InvoicePayment(id, amount, request.paymentDate(), request.paymentMethod(), request.reference().strip(), "PAYMENT", actor.id(), Instant.now(clock)));
        return save(invoice);
    }
    @Transactional
    public InvoiceResponse reverse(UUID id, UUID paymentId, SettlementDtos.ReversalRequest request, AuthenticatedUser actor) {
        Invoice invoice = find(id, actor); version(invoice, request.version());
        if (invoice.getStatus() != InvoiceStatus.VALIDATED && invoice.getStatus() != InvoiceStatus.PAID)
            throw new InvoiceConflictException("Cette facture ne permet pas la correction d’un règlement");
        InvoicePayment payment = payments.findByIdAndInvoiceId(paymentId, id).orElseThrow(InvoiceNotFoundException::new);
        if (payment.getReversedAt() != null) throw new InvoiceConflictException("Cette saisie a déjà été annulée");
        payment.reverse(actor.id(), request.reason().strip(), Instant.now(clock));
        invoice.recordPayment(payment.getAmount().negate(), actor.id());
        return save(invoice);
    }
    @Transactional
    public InvoiceResponse dueDate(UUID id, SettlementDtos.DueDateRequest request, AuthenticatedUser actor) {
        Invoice invoice = find(id, actor); version(invoice, request.version());
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) throw new InvoiceConflictException("Une facture annulée ne peut plus être modifiée");
        if (request.dueDate() != null && request.dueDate().isBefore(invoice.getInvoiceDate())) throw new ValidationException("L’échéance ne peut pas précéder la date de facture");
        invoice.setDueDate(request.dueDate(), actor.id());
        return save(invoice);
    }
    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> overdue(int page, AuthenticatedUser actor) {
        if (page < 0) throw new ValidationException("Page invalide");
        return invoices.findAll((root, query, cb) -> cb.and(cb.equal(root.get("companyId"), actor.companyId()),
                cb.equal(root.get("status"), InvoiceStatus.VALIDATED), cb.lessThan(root.get("dueDate"), LocalDate.now(clock)),
                cb.lessThan(root.get("paidAmount"), root.get("totalIncludingTax"))),
                PageRequest.of(page, 20, Sort.by("dueDate").ascending().and(Sort.by("id")))).map(mapper::toSummary);
    }
    @Transactional(readOnly = true)
    public List<SettlementDtos.Reminder> reminders(UUID id, AuthenticatedUser actor) {
        find(id, actor);
        return reminders.findAllByInvoiceIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20)).map(SettlementDtos.Reminder::from).getContent();
    }
    @Transactional
    public SettlementDtos.Reminder prepare(UUID id, AuthenticatedUser actor) {
        Invoice invoice = find(id, actor);
        if (invoice.getStatus() != InvoiceStatus.VALIDATED || invoice.getOutstandingAmount().signum() <= 0)
            throw new InvoiceConflictException("Seules les factures validées non soldées peuvent être relancées");
        if (invoice.getDueDate() == null || !invoice.getDueDate().isBefore(LocalDate.now(clock)))
            throw new InvoiceConflictException("Renseignez une échéance dépassée avant de préparer une relance");
        String subject = "Rappel de règlement — facture " + invoice.getInvoiceNumber();
        String body = "Bonjour " + invoice.getClientNameSnapshot() + ",\n\nSauf erreur de notre part, la facture "
                + invoice.getInvoiceNumber() + ", arrivée à échéance le " + invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ", présente un solde restant de " + invoice.getOutstandingAmount().toPlainString() + " MAD.\n\n"
                + "Merci de nous confirmer la date prévue de règlement. Si votre paiement a déjà été effectué, merci de nous transmettre sa référence.\n\nCordialement.";
        return SettlementDtos.Reminder.from(reminders.save(new InvoiceReminder(id, invoice.getClient().getEmail(), subject, body, actor.id(), Instant.now(clock))));
    }
    private Invoice find(UUID id, AuthenticatedUser actor) { return invoices.findDetailedByIdAndCompanyId(id, actor.companyId()).orElseThrow(InvoiceNotFoundException::new); }
    private static void version(Invoice invoice, long version) { if (invoice.getVersion() != version) throw new InvoiceConcurrencyException(); }
    private InvoiceResponse save(Invoice invoice) {
        try { return mapper.toResponse(invoices.saveAndFlush(invoice)); }
        catch (ObjectOptimisticLockingFailureException exception) { throw new InvoiceConcurrencyException(); }
    }
}
