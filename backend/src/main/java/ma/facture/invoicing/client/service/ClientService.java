package ma.facture.invoicing.client.service;

import ma.facture.invoicing.client.dto.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.exception.*;
import ma.facture.invoicing.client.mapper.ClientMapper;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import ma.facture.invoicing.company.TenantDefaults;

@Service
public class ClientService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CLIENT_CODE_RETRIES = 3;
    private static final Set<String> SORT_FIELDS = Set.of("clientCode", "clientName", "createdAt", "updatedAt", "active");
    private final ClientRepository repository;
    private final ClientCodeGenerator codeGenerator;
    private final ClientMapper mapper;

    public ClientService(ClientRepository repository, ClientCodeGenerator codeGenerator, ClientMapper mapper) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.mapper = mapper;
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request, AuthenticatedUser actor) {
        NormalizedClient normalized = normalize(request.clientName(), request.phoneNumber(), request.address(), request.email(), request.ice());
        rejectDuplicateIce(normalized.ice(), null, actor.companyId());
        for (int attempt = 0; attempt < CLIENT_CODE_RETRIES; attempt++) {
            Client client = new Client(codeGenerator.generate(), normalized.name(), normalized.phone(), normalized.address(),
                    normalized.email(), normalized.ice(), actor.id(), actor.companyId());
            try {
                return mapper.toResponse(repository.saveAndFlush(client));
            } catch (DataIntegrityViolationException exception) {
                if (hasConstraint(exception, "uq_clients_company_ice_not_null")) throw new DuplicateClientIceException();
                if (!hasConstraint(exception, "clients_client_code_key") || attempt == CLIENT_CODE_RETRIES - 1) throw exception;
            }
        }
        throw new ClientCodeGenerationException();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) { return mapper.toResponse(find(id, TenantDefaults.DEFAULT_COMPANY_ID)); }
    @Transactional(readOnly = true)
    public ClientResponse get(UUID id, AuthenticatedUser actor) { return mapper.toResponse(find(id, actor.companyId())); }

    @Transactional(readOnly = true)
    public ClientPageResponse list(int page, int size, String sort, String search, Boolean active) {
        return list(page, size, sort, search, active, TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Transactional(readOnly = true)
    public ClientPageResponse list(int page, int size, String sort, String search, Boolean active, AuthenticatedUser actor) {
        return list(page, size, sort, search, active, actor.companyId());
    }

    private ClientPageResponse list(int page, int size, String sort, String search, Boolean active, UUID companyId) {
        if (page < 0) throw new InvalidClientQueryException("Le numéro de page ne doit pas être négatif");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new InvalidClientQueryException("La taille de page doit être comprise entre 1 et 100");
        Sort parsedSort = parseSort(sort);
        String normalizedSearch = normalizeSearch(search);
        Specification<Client> specification = searchSpecification(normalizedSearch, active, companyId);
        return ClientPageResponse.from(repository.findAll(specification, PageRequest.of(page, size, parsedSort)).map(mapper::toSummary));
    }

    @Transactional
    public ClientResponse update(UUID id, ClientUpdateRequest request, AuthenticatedUser actor) {
        Client client = find(id, actor.companyId());
        requireVersion(client, request.version());
        NormalizedClient normalized = normalize(request.clientName(), request.phoneNumber(), request.address(), request.email(), request.ice());
        rejectDuplicateIce(normalized.ice(), id, actor.companyId());
        client.update(normalized.name(), normalized.phone(), normalized.address(), normalized.email(), normalized.ice(), actor.id());
        try {
            return mapper.toResponse(repository.saveAndFlush(client));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_clients_company_ice_not_null")) throw new DuplicateClientIceException();
            throw exception;
        }
    }

    @Transactional
    public ClientResponse updateStatus(UUID id, ClientStatusRequest request, AuthenticatedUser actor) {
        Client client = find(id, actor.companyId());
        if (client.isActive() == request.active()) return mapper.toResponse(client);
        requireVersion(client, request.version());
        client.setStatus(request.active(), actor.id());
        return mapper.toResponse(repository.saveAndFlush(client));
    }

    static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        return search.strip().toLowerCase(Locale.ROOT);
    }

    static Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "clientName,asc" : sort.strip();
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) throw new InvalidClientQueryException("Champ de tri des clients non pris en charge");
        Sort.Direction direction;
        try { direction = Sort.Direction.fromString(parts[1]); }
        catch (IllegalArgumentException exception) { throw new InvalidClientQueryException("Le sens du tri doit être asc ou desc"); }
        return Sort.by(direction, parts[0]);
    }

    private Specification<Client> searchSpecification(String search, Boolean active, UUID companyId) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (search != null) {
                String pattern = "%" + escapeLike(search) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("clientCode")), pattern, '\\'),
                        cb.like(cb.lower(root.get("clientName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("phoneNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.get("ice")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    private NormalizedClient normalize(String name, String phone, String address, String email, String ice) {
        return new NormalizedClient(name.strip(), optional(phone, true), optional(address, false),
                optional(email, false) == null ? null : optional(email, false).toLowerCase(Locale.ROOT),
                optional(ice, false) == null ? null : ice.replaceAll("\\s", ""));
    }

    private static String optional(String value, boolean collapseSpaces) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        return collapseSpaces ? normalized.replaceAll("\\s+", " ") : normalized;
    }

    private void rejectDuplicateIce(String ice, UUID excludedId, UUID companyId) {
        if (ice == null) return;
        boolean exists = excludedId == null ? repository.existsByCompanyIdAndIce(companyId, ice)
                : repository.existsByCompanyIdAndIceAndIdNot(companyId, ice, excludedId);
        if (exists) throw new DuplicateClientIceException();
    }

    private Client find(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).orElseThrow(ClientNotFoundException::new); }
    private static void requireVersion(Client client, long suppliedVersion) {
        if (client.getVersion() != suppliedVersion) throw new ClientConcurrencyException();
    }
    private static boolean hasConstraint(Throwable exception, String constraint) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(constraint)) return true;
        }
        return false;
    }

    private record NormalizedClient(String name, String phone, String address, String email, String ice) {}
}
