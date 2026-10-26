package ma.facture.invoicing.product.service;

import jakarta.persistence.criteria.Predicate;
import ma.facture.invoicing.product.dto.*;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.exception.*;
import ma.facture.invoicing.product.mapper.ProductMapper;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import ma.facture.invoicing.company.TenantDefaults;

@Service
public class ProductService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CODE_RETRIES = 3;
    private static final Set<String> SORT_FIELDS = Set.of("productCode", "productName", "unitPriceExcludingTax", "defaultVatRate", "unit", "active", "createdAt", "updatedAt");
    private final ProductRepository repository;
    private final ProductCodeGenerator codes;
    private final ProductPricingService pricing;
    private final ProductMapper mapper;

    public ProductService(ProductRepository repository, ProductCodeGenerator codes, ProductPricingService pricing, ProductMapper mapper) {
        this.repository = repository; this.codes = codes; this.pricing = pricing; this.mapper = mapper;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request, AuthenticatedUser actor) {
        NormalizedProduct normalized = normalize(request.productName(), request.description(), request.unitPriceExcludingTax(), request.defaultVatRate(), request.unit());
        for (int attempt = 0; attempt < CODE_RETRIES; attempt++) {
            Product product = new Product(codes.generate(), normalized.name(), normalized.description(), normalized.price(), normalized.vat(), normalized.unit(), actor.id(), actor.companyId());
            try { return mapper.toResponse(repository.saveAndFlush(product)); }
            catch (DataIntegrityViolationException exception) {
                if (!hasConstraint(exception, "products_product_code_key") || attempt == CODE_RETRIES - 1) throw exception;
            }
        }
        throw new ProductCodeGenerationException();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) { return mapper.toResponse(find(id, TenantDefaults.DEFAULT_COMPANY_ID)); }
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id, AuthenticatedUser actor) { return mapper.toResponse(find(id, actor.companyId())); }

    @Transactional(readOnly = true)
    public ProductPageResponse list(int page, int size, String sort, String search, Boolean active, String unit,
                                    BigDecimal minPrice, BigDecimal maxPrice) {
        return list(page, size, sort, search, active, unit, minPrice, maxPrice, TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse list(int page, int size, String sort, String search, Boolean active, String unit,
                                    BigDecimal minPrice, BigDecimal maxPrice, AuthenticatedUser actor) {
        return list(page, size, sort, search, active, unit, minPrice, maxPrice, actor.companyId());
    }

    private ProductPageResponse list(int page, int size, String sort, String search, Boolean active, String unit,
                                     BigDecimal minPrice, BigDecimal maxPrice, UUID companyId) {
        if (page < 0) throw new InvalidProductQueryException("Le numéro de page ne doit pas être négatif");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new InvalidProductQueryException("La taille de page doit être comprise entre 1 et 100");
        ProductUnit parsedUnit = parseUnit(unit);
        BigDecimal normalizedMin = minPrice == null ? null : pricing.normalizePrice(minPrice);
        BigDecimal normalizedMax = maxPrice == null ? null : pricing.normalizePrice(maxPrice);
        if (normalizedMin != null && normalizedMax != null && normalizedMin.compareTo(normalizedMax) > 0)
            throw new InvalidProductQueryException("Le prix minimum ne doit pas dépasser le prix maximum");
        Specification<Product> specification = specification(normalizeSearch(search), active, parsedUnit, normalizedMin, normalizedMax, companyId);
        return ProductPageResponse.from(repository.findAll(specification, PageRequest.of(page, size, parseSort(sort))).map(mapper::toSummary));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest request, AuthenticatedUser actor) {
        Product product = find(id, actor.companyId());
        requireVersion(product, request.version());
        NormalizedProduct normalized = normalize(request.productName(), request.description(), request.unitPriceExcludingTax(), request.defaultVatRate(), request.unit());
        product.update(normalized.name(), normalized.description(), normalized.price(), normalized.vat(), normalized.unit(), actor.id());
        return mapper.toResponse(repository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse updateStatus(UUID id, ProductStatusRequest request, AuthenticatedUser actor) {
        Product product = find(id, actor.companyId());
        if (product.isActive() == request.active()) return mapper.toResponse(product);
        requireVersion(product, request.version());
        product.setStatus(request.active(), actor.id());
        return mapper.toResponse(repository.saveAndFlush(product));
    }

    static String normalizeSearch(String search) { return search == null || search.isBlank() ? null : search.strip().toLowerCase(Locale.ROOT); }
    static Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "productName,asc" : sort.strip();
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) throw new InvalidProductQueryException("Champ de tri des produits non pris en charge");
        try { return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]); }
        catch (IllegalArgumentException exception) { throw new InvalidProductQueryException("Le sens du tri doit être asc ou desc"); }
    }
    static ProductUnit parseUnit(String unit) {
        if (unit == null || unit.isBlank()) return null;
        try { return ProductUnit.valueOf(unit.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new InvalidProductQueryException("Unité de produit non prise en charge"); }
    }

    private Specification<Product> specification(String search, Boolean active, ProductUnit unit, BigDecimal minPrice, BigDecimal maxPrice, UUID companyId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (unit != null) predicates.add(cb.equal(root.get("unit"), unit));
            if (minPrice != null) predicates.add(cb.greaterThanOrEqualTo(root.get("unitPriceExcludingTax"), minPrice));
            if (maxPrice != null) predicates.add(cb.lessThanOrEqualTo(root.get("unitPriceExcludingTax"), maxPrice));
            if (search != null) {
                String pattern = "%" + escapeLike(search) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("productCode")), pattern, '\\'), cb.like(cb.lower(root.get("productName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private NormalizedProduct normalize(String name, String description, BigDecimal price, BigDecimal vat, ProductUnit unit) {
        if (name == null || name.isBlank()) throw new ProductValidationException("Le nom du produit est requis");
        if (unit == null) throw new ProductValidationException("L’unité est requise");
        return new NormalizedProduct(name.strip(), optional(description), pricing.normalizePrice(price), pricing.normalizeVat(vat), unit);
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private Product find(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).orElseThrow(ProductNotFoundException::new); }
    private static void requireVersion(Product product, long supplied) { if (product.getVersion() != supplied) throw new ProductConcurrencyException(); }
    private static boolean hasConstraint(Throwable exception, String constraint) {
        for (Throwable current = exception; current != null; current = current.getCause()) if (current.getMessage() != null && current.getMessage().contains(constraint)) return true;
        return false;
    }
    private record NormalizedProduct(String name, String description, BigDecimal price, BigDecimal vat, ProductUnit unit) {}
}
