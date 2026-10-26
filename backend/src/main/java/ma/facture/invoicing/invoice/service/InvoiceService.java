package ma.facture.invoicing.invoice.service;

import jakarta.persistence.criteria.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.exception.ClientNotFoundException;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.contract.entity.*;
import ma.facture.invoicing.contract.exception.ContractNotFoundException;
import ma.facture.invoicing.contract.repository.ContractRepository;
import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.entity.*;
import ma.facture.invoicing.invoice.exception.*;
import ma.facture.invoicing.invoice.mapper.InvoiceMapper;
import ma.facture.invoicing.invoice.numbering.InvoiceNumberGenerator;
import ma.facture.invoicing.invoice.pdf.*;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.invoice.settlement.InvoicePayment;
import ma.facture.invoicing.invoice.settlement.InvoicePaymentRepository;
import ma.facture.invoicing.product.entity.Product;
import ma.facture.invoicing.product.exception.ProductNotFoundException;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import ma.facture.invoicing.company.TenantDefaults;

@Service
public class InvoiceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("invoiceNumber", "invoiceDate", "status", "totalIncludingTax", "createdAt", "updatedAt");

    private final InvoiceRepository invoices;
    private final ClientRepository clients;
    private final ProductRepository products;
    private final ContractRepository contracts;
    private final InvoiceCalculationService calculations;
    private final InvoiceNumberGenerator numbers;
    private final InvoicePdfService pdf;
    private final InvoiceMapper mapper;
    private final Clock clock;
    private final InvoicePaymentRepository payments;

    public InvoiceService(InvoiceRepository invoices, ClientRepository clients, ProductRepository products,
                          ContractRepository contracts, InvoiceCalculationService calculations,
                          InvoiceNumberGenerator numbers, InvoicePdfService pdf, InvoiceMapper mapper, Clock clock,
                          InvoicePaymentRepository payments) {
        this.invoices = invoices;
        this.clients = clients;
        this.products = products;
        this.contracts = contracts;
        this.calculations = calculations;
        this.numbers = numbers;
        this.pdf = pdf;
        this.mapper = mapper;
        this.clock = clock;
        this.payments = payments;
    }

    @Transactional
    public InvoiceResponse create(InvoiceCreateRequest request, AuthenticatedUser actor) {
        Client client = findClient(request.clientId(), actor.companyId());
        Contract contract = findContract(request.contractId(), actor.companyId());
        PaymentMethod paymentMethod = resolveHeader(client, contract, request.invoiceDate(), request.paymentMethod());
        Invoice invoice = new Invoice(request.invoiceDate(), client, contract, paymentMethod,
                normalizeReference(request.customerOrderReference()), actor.id());
        replaceLines(invoice, request.lines(), contract);
        return mapper.toResponse(save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) { return mapper.toResponse(findDetailed(id, TenantDefaults.DEFAULT_COMPANY_ID)); }
    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id, AuthenticatedUser actor) { return mapper.toResponse(findDetailed(id, actor.companyId())); }

    @Transactional(readOnly = true)
    public InvoicePageResponse list(int page, int size, String sort, String search, UUID clientId, UUID contractId,
                                    String status, LocalDate dateFrom, LocalDate dateTo) {
        return list(page, size, sort, search, clientId, contractId, status, dateFrom, dateTo, TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Transactional(readOnly = true)
    public InvoicePageResponse list(int page, int size, String sort, String search, UUID clientId, UUID contractId,
                                    String status, LocalDate dateFrom, LocalDate dateTo, AuthenticatedUser actor) {
        return list(page, size, sort, search, clientId, contractId, status, dateFrom, dateTo, actor.companyId());
    }

    private InvoicePageResponse list(int page, int size, String sort, String search, UUID clientId, UUID contractId,
                                     String status, LocalDate dateFrom, LocalDate dateTo, UUID companyId) {
        if (page < 0) throw new InvalidInvoiceQueryException("Le numéro de page ne doit pas être négatif");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new InvalidInvoiceQueryException("La taille de page doit être comprise entre 1 et 100");
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))
            throw new InvalidInvoiceQueryException("La période des dates de facture est invalide");
        Specification<Invoice> specification = specification(normalizeSearch(search), clientId, contractId,
                parseStatus(status), dateFrom, dateTo, companyId);
        Page<InvoiceSummaryResponse> result = invoices.findAll(specification,
                PageRequest.of(page, size, parseSort(sort))).map(mapper::toSummary);
        return InvoicePageResponse.from(result);
    }

    @Transactional
    public InvoiceResponse update(UUID id, InvoiceUpdateRequest request, AuthenticatedUser actor) {
        Invoice invoice = findDetailed(id, actor.companyId());
        requireDraft(invoice);
        requireVersion(invoice, request.version());
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(request.invoiceDate()))
            throw new InvoiceValidationException("Modifiez d’abord l’échéance : elle ne peut pas précéder la nouvelle date de facture");
        Client client = findClient(request.clientId(), actor.companyId());
        Contract contract = findContract(request.contractId(), actor.companyId());
        PaymentMethod paymentMethod = resolveHeader(client, contract, request.invoiceDate(), request.paymentMethod());
        invoice.updateDraft(request.invoiceDate(), client, contract, paymentMethod,
                normalizeReference(request.customerOrderReference()), actor.id());
        replaceLines(invoice, request.lines(), contract);
        return mapper.toResponse(save(invoice));
    }

    @Transactional
    public InvoiceResponse validate(UUID id, InvoiceVersionRequest request, AuthenticatedUser actor) {
        Invoice invoice = findDetailed(id, actor.companyId());
        requireDraft(invoice);
        requireVersion(invoice, request.version());
        validateForFinalization(invoice);
        applyAuthoritativeCalculation(invoice, invoice.getContract());
        invoice.validate(numbers.generateForValidation(actor.companyId()), actor.id(), Instant.now(clock));
        try {
            GeneratedInvoicePdf generated = pdf.generate(invoice);
            invoice.attachPdf(generated.relativePath(), generated.amountInWords());
        } catch (InvoicePdfException exception) {
            LOGGER.error("Invoice PDF generation failed id={} number={}", invoice.getId(), invoice.getInvoiceNumber());
            throw exception;
        }
        Invoice saved = save(invoice);
        LOGGER.info("Invoice validated id={} number={} actorId={}", saved.getId(), saved.getInvoiceNumber(), actor.id());
        return mapper.toResponse(saved);
    }

    @Transactional
    public InvoiceResponse markPaid(UUID id, InvoiceVersionRequest request, AuthenticatedUser actor) {
        Invoice invoice = findDetailed(id, actor.companyId());
        if (invoice.getStatus() == InvoiceStatus.PAID) return mapper.toResponse(invoice);
        if (invoice.getStatus() != InvoiceStatus.VALIDATED)
            throw new InvoiceConflictException("Seule une facture VALIDÉE peut être marquée comme payée");
        requireVersion(invoice, request.version());
        if (invoice.getOutstandingAmount().signum() > 0)
            payments.save(new InvoicePayment(id, invoice.getOutstandingAmount(), LocalDate.now(clock), invoice.getPaymentMethod(),
                    "Règlement du solde", "SETTLEMENT", actor.id(), Instant.now(clock)));
        invoice.markPaid(actor.id());
        return mapper.toResponse(save(invoice));
    }

    @Transactional
    public InvoiceResponse cancel(UUID id, InvoiceVersionRequest request, AuthenticatedUser actor) {
        Invoice invoice = findDetailed(id, actor.companyId());
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) return mapper.toResponse(invoice);
        if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.VALIDATED)
            throw new InvoiceConflictException("Seule une facture BROUILLON ou VALIDÉE peut être annulée");
        requireVersion(invoice, request.version());
        if (invoice.getPaidAmount().signum() > 0)
            throw new InvoiceConflictException("La facture contient des règlements. Corrigez les saisies concernées avant de l’annuler");
        invoice.cancel(actor.id(), Instant.now(clock));
        Invoice saved = save(invoice);
        LOGGER.info("Invoice cancelled id={} number={} actorId={}", saved.getId(), saved.getInvoiceNumber(), actor.id());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoicePdfDownload downloadPdf(UUID id) { return pdf.download(findDetailed(id, TenantDefaults.DEFAULT_COMPANY_ID)); }
    @Transactional(readOnly = true)
    public InvoicePdfDownload downloadPdf(UUID id, AuthenticatedUser actor) { return pdf.download(findDetailed(id, actor.companyId())); }

    private void replaceLines(Invoice invoice, List<InvoiceLineRequest> requests, Contract contract) {
        invoice.clearLines();
        List<InvoiceLineRequest> supplied = requests == null ? List.of() : requests;
        Set<UUID> productIds = new HashSet<>();
        Map<UUID, ContractLine> contractLines = contractLines(contract);
        List<InvoiceLineInput> inputs = new ArrayList<>();
        for (InvoiceLineRequest request : supplied) {
            if (!productIds.add(request.productId())) throw new InvoiceValidationException("Un produit ne peut apparaître qu’une seule fois dans une facture");
            Product product = products.findByIdAndCompanyId(request.productId(), invoice.getCompanyId()).orElseThrow(ProductNotFoundException::new);
            ContractLine terms = contract == null ? null : contractLines.get(product.getId());
            if (contract != null && terms == null) throw new InvoiceValidationException("Le produit ne figure pas dans le contrat sélectionné");
            BigDecimal price = terms == null ? required(request.unitPriceExcludingTax(), "Le prix unitaire est requis en l’absence de contrat")
                    : terms.getNegotiatedUnitPriceExcludingTax();
            BigDecimal vat = terms == null ? required(request.vatRate(), "Le taux de TVA est requis en l’absence de contrat") : terms.getVatRate();
            BigDecimal discount = terms == null ? request.discountRate() : terms.getDiscountRate();
            InvoiceLineInput input = new InvoiceLineInput(request.quantity(), price, vat, discount);
            InvoiceLineCalculation calculated = calculations.calculateLine(input);
            if (terms != null && terms.getMinimumQuantity() != null
                    && calculated.quantity().compareTo(terms.getMinimumQuantity()) < 0)
                throw new InvoiceValidationException("La quantité est inférieure au minimum prévu par le contrat pour le produit " + product.getProductCode());
            invoice.addLine(product, calculated.quantity(), calculated.unitPriceExcludingTax(), calculated.vatRate(), calculated.discountRate());
            inputs.add(input);
        }
        if (!inputs.isEmpty()) invoice.applyCalculation(calculations.calculate(inputs));
    }

    private void validateForFinalization(Invoice invoice) {
        if (!invoice.getClient().isActive()) throw new InvoiceConflictException("Le client doit être actif avant la validation de la facture");
        if (invoice.getLines().isEmpty()) throw new InvoiceValidationException("Une facture doit contenir au moins une ligne");
        for (InvoiceLine line : invoice.getLines()) if (!line.getProduct().isActive())
            throw new InvoiceConflictException("Tous les produits de la facture doivent être actifs avant la validation");
        validateContract(invoice.getClient(), invoice.getContract(), invoice.getInvoiceDate());
    }

    private void applyAuthoritativeCalculation(Invoice invoice, Contract contract) {
        Map<UUID, ContractLine> contractLines = contractLines(contract);
        List<InvoiceLineInput> inputs = new ArrayList<>();
        for (InvoiceLine line : invoice.getLines()) {
            ContractLine terms = contract == null ? null : contractLines.get(line.getProduct().getId());
            if (contract != null && terms == null) throw new InvoiceConflictException("Le produit de la facture ne figure plus dans le contrat");
            BigDecimal price = terms == null ? line.getUnitPriceExcludingTax() : terms.getNegotiatedUnitPriceExcludingTax();
            BigDecimal vat = terms == null ? line.getVatRate() : terms.getVatRate();
            BigDecimal discount = terms == null ? line.getDiscountRate() : terms.getDiscountRate();
            if (terms != null && terms.getMinimumQuantity() != null && line.getQuantity().compareTo(terms.getMinimumQuantity()) < 0)
                throw new InvoiceConflictException("La quantité de la facture ne respecte plus le minimum prévu par le contrat");
            inputs.add(new InvoiceLineInput(line.getQuantity(), price, vat, discount));
        }
        invoice.applyCalculation(calculations.calculate(inputs));
    }

    private PaymentMethod resolveHeader(Client client, Contract contract, LocalDate invoiceDate, PaymentMethod supplied) {
        if (contract != null) {
            validateContract(client, contract, invoiceDate);
            return contract.getPaymentMethod();
        }
        if (supplied == null) throw new InvoiceValidationException("Le mode de paiement est requis en l’absence de contrat");
        if (supplied != PaymentMethod.CASH
                && supplied != PaymentMethod.CHECK
                && supplied != PaymentMethod.BANK_TRANSFER)
            throw new InvoiceValidationException("Le mode de paiement doit être Espèces, Chèque ou Virement bancaire");
        return supplied;
    }

    private static void validateContract(Client client, Contract contract, LocalDate invoiceDate) {
        if (contract == null) return;
        if (!contract.getClient().getId().equals(client.getId()))
            throw new InvoiceValidationException("Le contrat n’appartient pas au client sélectionné");
        if (contract.getStatus() != ContractStatus.ACTIVE)
            throw new InvoiceConflictException("Le contrat doit être ACTIF");
        if (invoiceDate.isBefore(contract.getStartDate())
                || contract.getEndDate() != null && invoiceDate.isAfter(contract.getEndDate()))
            throw new InvoiceConflictException("La date de facture est en dehors de la période de validité du contrat");
    }

    private static Map<UUID, ContractLine> contractLines(Contract contract) {
        if (contract == null) return Map.of();
        Map<UUID, ContractLine> result = new HashMap<>();
        contract.getLines().forEach(line -> result.put(line.getProduct().getId(), line));
        return result;
    }

    private Client findClient(UUID id, UUID companyId) { return clients.findByIdAndCompanyId(id, companyId).orElseThrow(ClientNotFoundException::new); }
    private Contract findContract(UUID id, UUID companyId) { return id == null ? null : contracts.findDetailedByIdAndCompanyId(id, companyId).orElseThrow(ContractNotFoundException::new); }
    private Invoice findDetailed(UUID id, UUID companyId) { return invoices.findDetailedByIdAndCompanyId(id, companyId).orElseThrow(InvoiceNotFoundException::new); }
    private Invoice save(Invoice invoice) {
        try { return invoices.saveAndFlush(invoice); }
        catch (ObjectOptimisticLockingFailureException exception) { throw new InvoiceConcurrencyException(); }
    }
    private static <T> T required(T value, String message) { if (value == null) throw new InvoiceValidationException(message); return value; }
    private static void requireDraft(Invoice invoice) { if (invoice.getStatus() != InvoiceStatus.DRAFT) throw new InvoiceConflictException("Seules les factures BROUILLON peuvent être modifiées"); }
    private static void requireVersion(Invoice invoice, long version) { if (invoice.getVersion() != version) throw new InvoiceConcurrencyException(); }
    private static String normalizeReference(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    static String normalizeSearch(String value) { return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    private static InvoiceStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return InvoiceStatus.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new InvalidInvoiceQueryException("Statut de facture non pris en charge"); }
    }

    static Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "createdAt,desc" : sort.strip();
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0]))
            throw new InvalidInvoiceQueryException("Champ de tri des factures non pris en charge");
        try { return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]); }
        catch (IllegalArgumentException exception) { throw new InvalidInvoiceQueryException("Le sens du tri doit être asc ou desc"); }
    }

    private static Specification<Invoice> specification(String search, UUID clientId, UUID contractId,
                                                         InvoiceStatus status, LocalDate dateFrom, LocalDate dateTo, UUID companyId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            Join<Object, Object> client = root.join("client", JoinType.INNER);
            if (clientId != null) predicates.add(cb.equal(client.get("id"), clientId));
            if (contractId != null) predicates.add(cb.equal(root.get("contract").get("id"), contractId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (dateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), dateFrom));
            if (dateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), dateTo));
            if (search != null) {
                String pattern = "%" + escapeLike(search) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("invoiceNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("customerOrderReference")), pattern, '\\'),
                        cb.like(cb.lower(root.get("clientCodeSnapshot")), pattern, '\\'),
                        cb.like(cb.lower(root.get("clientNameSnapshot")), pattern, '\\'),
                        cb.like(cb.lower(client.get("clientCode")), pattern, '\\'),
                        cb.like(cb.lower(client.get("clientName")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
