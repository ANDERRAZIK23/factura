package ma.facture.invoicing.contract.service;

import jakarta.persistence.criteria.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.exception.ClientNotFoundException;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.contract.dto.*;
import ma.facture.invoicing.contract.entity.*;
import ma.facture.invoicing.contract.exception.*;
import ma.facture.invoicing.contract.mapper.ContractMapper;
import ma.facture.invoicing.contract.repository.ContractRepository;
import ma.facture.invoicing.product.entity.Product;
import ma.facture.invoicing.product.exception.ProductNotFoundException;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import ma.facture.invoicing.company.TenantDefaults;

@Service
public class ContractService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("contractCode", "startDate", "endDate", "status", "paymentMethod", "createdAt", "updatedAt");
    private final ContractRepository contracts;
    private final ClientRepository clients;
    private final ProductRepository products;
    private final ContractCodeGenerator codes;
    private final ContractPricingService pricing;
    private final ContractValidationService validation;
    private final ContractMapper mapper;
    private final Clock clock;

    public ContractService(ContractRepository contracts, ClientRepository clients, ProductRepository products,
                           ContractCodeGenerator codes, ContractPricingService pricing,
                           ContractValidationService validation, ContractMapper mapper, Clock clock) {
        this.contracts = contracts; this.clients = clients; this.products = products; this.codes = codes;
        this.pricing = pricing; this.validation = validation; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public ContractResponse create(ContractCreateRequest request, AuthenticatedUser actor) {
        Client client = findClient(request.clientId(), actor.companyId()); validation.validateClientForDraft(client);
        validation.validateDates(request.startDate(), request.endDate());
        BigDecimal defaultDiscount = validation.defaultDiscount(request.defaultDiscountRate());
        Contract contract = new Contract(codes.generate(), client, request.startDate(), request.endDate(), request.paymentMethod(),
                defaultDiscount, ContractValidationService.notes(request.notes(), 2000, "Les notes du contrat"), actor.id());
        reconcileLines(contract, request.lines(), defaultDiscount, false);
        return mapper.toResponse(contracts.saveAndFlush(contract));
    }

    @Transactional(readOnly = true)
    public ContractResponse get(UUID id) { return mapper.toResponse(findDetailed(id, TenantDefaults.DEFAULT_COMPANY_ID)); }
    @Transactional(readOnly = true)
    public ContractResponse get(UUID id, AuthenticatedUser actor) { return mapper.toResponse(findDetailed(id, actor.companyId())); }

    @Transactional(readOnly = true)
    public ContractPageResponse list(int page, int size, String sort, String search, UUID clientId, String status,
                                     String paymentMethod, LocalDate startDateFrom, LocalDate startDateTo,
                                     LocalDate endDateFrom, LocalDate endDateTo, Boolean usable) {
        return list(page, size, sort, search, clientId, status, paymentMethod, startDateFrom, startDateTo,
                endDateFrom, endDateTo, usable, TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Transactional(readOnly = true)
    public ContractPageResponse list(int page, int size, String sort, String search, UUID clientId, String status,
                                     String paymentMethod, LocalDate startDateFrom, LocalDate startDateTo,
                                     LocalDate endDateFrom, LocalDate endDateTo, Boolean usable, AuthenticatedUser actor) {
        return list(page, size, sort, search, clientId, status, paymentMethod, startDateFrom, startDateTo,
                endDateFrom, endDateTo, usable, actor.companyId());
    }

    private ContractPageResponse list(int page, int size, String sort, String search, UUID clientId, String status,
                                      String paymentMethod, LocalDate startDateFrom, LocalDate startDateTo,
                                      LocalDate endDateFrom, LocalDate endDateTo, Boolean usable, UUID companyId) {
        if (page < 0) throw new InvalidContractQueryException("Le numéro de page ne doit pas être négatif");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new InvalidContractQueryException("La taille de page doit être comprise entre 1 et 100");
        validateRange(startDateFrom, startDateTo, "La période de date de début"); validateRange(endDateFrom, endDateTo, "La période de date de fin");
        ContractStatus parsedStatus = parseStatus(status); PaymentMethod parsedPayment = parsePaymentMethod(paymentMethod);
        Specification<Contract> specification = specification(normalizeSearch(search), clientId, parsedStatus, parsedPayment,
                startDateFrom, startDateTo, endDateFrom, endDateTo, usable, LocalDate.now(clock), companyId);
        return ContractPageResponse.from(contracts.findAll(specification, PageRequest.of(page, size, parseSort(sort))).map(mapper::toSummary));
    }

    @Transactional
    public ContractResponse update(UUID id, ContractUpdateRequest request, AuthenticatedUser actor) {
        Contract contract = findDetailed(id, actor.companyId()); requireDraft(contract); requireVersion(contract, request.version());
        Client client = findClient(request.clientId(), actor.companyId()); validation.validateClientForDraft(client);
        validation.validateDates(request.startDate(), request.endDate());
        BigDecimal defaultDiscount = validation.defaultDiscount(request.defaultDiscountRate());
        contract.updateDraft(client, request.startDate(), request.endDate(), request.paymentMethod(), defaultDiscount,
                ContractValidationService.notes(request.notes(), 2000, "Les notes du contrat"), actor.id());
        reconcileLines(contract, request.lines(), defaultDiscount, true);
        return mapper.toResponse(contracts.saveAndFlush(contract));
    }

    @Transactional
    public ContractResponse activate(UUID id, ContractActivationRequest request, AuthenticatedUser actor) {
        Contract contract = findDetailed(id, actor.companyId()); requireDraft(contract); requireVersion(contract, request.version());
        validation.validateDates(contract.getStartDate(), contract.getEndDate());
        validation.validateActivation(contract, LocalDate.now(clock));
        contract.activate(actor.id(), Instant.now(clock));
        return mapper.toResponse(contracts.saveAndFlush(contract));
    }

    @Transactional
    public ContractResponse cancel(UUID id, ContractCancellationRequest request, AuthenticatedUser actor) {
        Contract contract = findDetailed(id, actor.companyId());
        if (contract.getStatus() == ContractStatus.CANCELLED) return mapper.toResponse(contract);
        if (contract.getStatus() != ContractStatus.DRAFT && contract.getStatus() != ContractStatus.ACTIVE)
            throw new ContractConflictException("Seuls les contrats BROUILLON ou ACTIF peuvent être annulés");
        requireVersion(contract, request.version());
        String reason = ContractValidationService.notes(request.reason(), 500, "Le motif d’annulation");
        if (contract.getStatus() == ContractStatus.ACTIVE && reason == null)
            throw new ContractValidationException("Le motif d’annulation est requis pour un contrat ACTIF");
        contract.cancel(actor.id(), Instant.now(clock), reason);
        return mapper.toResponse(contracts.saveAndFlush(contract));
    }

    @Transactional(readOnly = true)
    public ContractPricingResponse pricing(UUID id) {
        return pricing(id, TenantDefaults.DEFAULT_COMPANY_ID);
    }
    @Transactional(readOnly = true)
    public ContractPricingResponse pricing(UUID id, AuthenticatedUser actor) { return pricing(id, actor.companyId()); }
    private ContractPricingResponse pricing(UUID id, UUID companyId) {
        Contract contract = findDetailed(id, companyId);
        if (!validation.usable(contract, LocalDate.now(clock))) throw new ContractConflictException("Le contrat ne peut pas être utilisé actuellement pour la tarification");
        return mapper.toPricing(contract);
    }

    private void reconcileLines(Contract contract, List<ContractLineRequest> requests, BigDecimal defaultDiscount, boolean updating) {
        List<ContractLineRequest> supplied = requests == null ? List.of() : requests;
        Set<UUID> productIds = new HashSet<>();
        for (ContractLineRequest request : supplied) if (!productIds.add(request.productId()))
            throw new ContractValidationException("Un produit ne peut apparaître qu’une seule fois dans un contrat");
        Map<UUID, ContractLine> existing = new HashMap<>();
        contract.getLines().forEach(line -> existing.put(line.getId(), line));
        for (ContractLineRequest request : supplied) {
            Product product = products.findByIdAndCompanyId(request.productId(), contract.getCompanyId()).orElseThrow(ProductNotFoundException::new);
            BigDecimal price = pricing.normalizePrice(request.negotiatedUnitPriceExcludingTax());
            BigDecimal vat = pricing.normalizeRate(request.vatRate(), "Le taux de TVA");
            BigDecimal discount = pricing.normalizeRate(request.discountRate() == null ? defaultDiscount : request.discountRate(), "Le taux de remise");
            BigDecimal minimum = pricing.normalizeQuantity(request.minimumQuantity());
            String notes = ContractValidationService.notes(request.notes(), 1000, "Les notes de la ligne");
            if (request.id() == null) contract.addLine(product, price, vat, discount, minimum, notes);
            else {
                if (!updating) throw new ContractValidationException("Les identifiants de ligne ne peuvent pas être fournis lors de la création d’un contrat");
                ContractLine line = existing.remove(request.id());
                if (line == null) throw new ContractConflictException("La ligne de contrat n’appartient pas à ce contrat");
                if (request.version() == null || line.getVersion() != request.version()) throw new ContractConcurrencyException();
                line.updateTerms(product, price, vat, discount, minimum, notes);
            }
        }
        existing.values().forEach(contract::removeLine);
    }

    private Specification<Contract> specification(String search, UUID clientId, ContractStatus status, PaymentMethod paymentMethod,
            LocalDate startFrom, LocalDate startTo, LocalDate endFrom, LocalDate endTo, Boolean usable, LocalDate today, UUID companyId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(); Join<Object, Object> client = root.join("client", JoinType.INNER);
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (clientId != null) predicates.add(cb.equal(client.get("id"), clientId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (paymentMethod != null) predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            if (startFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startFrom));
            if (startTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), startTo));
            if (endFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), endFrom));
            if (endTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endTo));
            if (search != null) {
                String pattern = "%" + escapeLike(search) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("contractCode")), pattern, '\\'),
                        cb.like(cb.lower(client.get("clientCode")), pattern, '\\'), cb.like(cb.lower(client.get("clientName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("notes")), pattern, '\\')));
            }
            if (usable != null) {
                Predicate current = cb.and(cb.equal(root.get("status"), ContractStatus.ACTIVE),
                        cb.lessThanOrEqualTo(root.get("startDate"), today),
                        cb.or(cb.isNull(root.get("endDate")), cb.greaterThanOrEqualTo(root.get("endDate"), today)));
                predicates.add(usable ? current : cb.not(current));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
    static Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "createdAt,desc" : sort.strip(); String[] parts = value.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) throw new InvalidContractQueryException("Champ de tri des contrats non pris en charge");
        try { return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]); }
        catch (IllegalArgumentException exception) { throw new InvalidContractQueryException("Le sens du tri doit être asc ou desc"); }
    }
    static String normalizeSearch(String value) { return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private static void validateRange(LocalDate from, LocalDate to, String field) { if (from != null && to != null && from.isAfter(to)) throw new InvalidContractQueryException(field + " est invalide"); }
    private static ContractStatus parseStatus(String value) { if (value == null || value.isBlank()) return null; try { return ContractStatus.valueOf(value.strip().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new InvalidContractQueryException("Statut de contrat non pris en charge"); } }
    private static PaymentMethod parsePaymentMethod(String value) { if (value == null || value.isBlank()) return null; try { return PaymentMethod.valueOf(value.strip().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new InvalidContractQueryException("Mode de paiement non pris en charge"); } }
    private Client findClient(UUID id, UUID companyId) { if (id == null) throw new ContractValidationException("Le client est requis"); return clients.findByIdAndCompanyId(id, companyId).orElseThrow(ClientNotFoundException::new); }
    private Contract findDetailed(UUID id, UUID companyId) { return contracts.findDetailedByIdAndCompanyId(id, companyId).orElseThrow(ContractNotFoundException::new); }
    private static void requireDraft(Contract contract) { if (contract.getStatus() != ContractStatus.DRAFT) throw new ContractConflictException("Seuls les contrats BROUILLON peuvent être modifiés"); }
    private static void requireVersion(Contract contract, long supplied) { if (contract.getVersion() != supplied) throw new ContractConcurrencyException(); }
}
