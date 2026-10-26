package ma.facture.invoicing.company;

import ma.facture.invoicing.common.*;
import ma.facture.invoicing.security.*;
import ma.facture.invoicing.user.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CompanyService {
    private final CompanyRepository companies;
    private final CompanyCodeGenerator codes;
    private final UserRepository users;
    private final PasswordService passwords;

    public CompanyService(CompanyRepository companies, CompanyCodeGenerator codes, UserRepository users, PasswordService passwords) {
        this.companies = companies; this.codes = codes; this.users = users; this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public Page<CompanyDtos.Response> list(Pageable pageable) { return companies.findAll(pageable).map(CompanyDtos.Response::from); }

    @Transactional(readOnly = true)
    public CompanyDtos.Response get(UUID id) { return CompanyDtos.Response.from(find(id)); }

    @Transactional(readOnly = true)
    public CompanyDtos.Response current(AuthenticatedUser actor) { return get(actor.companyId()); }

    @Transactional(readOnly = true)
    public Page<UserDtos.Response> listUsers(UUID companyId, Pageable pageable) {
        find(companyId);
        return users.findAllByCompanyId(companyId, pageable).map(UserDtos.Response::from);
    }

    @Transactional
    public CompanyDtos.Response create(CompanyDtos.CreateRequest request) {
        String adminEmail = UserService.normalizeEmail(request.adminEmail());
        if (users.existsByEmailIgnoreCase(adminEmail)) throw new ConflictException("L’adresse e-mail de l’administrateur est déjà utilisée");
        String ice = normalizeIce(request.ice());
        if (ice != null && companies.existsByIce(ice)) throw new ConflictException("L’ICE de l’entreprise est déjà utilisé");
        Company company = companies.saveAndFlush(new Company(codes.generate(), request.companyName().strip(), optional(request.address()),
                optional(request.phone()), normalizeEmail(request.email()), ice));
        users.save(new UserEntity(request.adminFullName().strip(), adminEmail, passwords.hash(request.adminPassword()), UserRole.ADMIN, company.getId()));
        return CompanyDtos.Response.from(company);
    }

    @Transactional
    public UserDtos.Response createUser(UUID companyId, UserDtos.CreateRequest request) {
        find(companyId);
        if (request.role() == UserRole.APP_ADMIN) throw new ConflictException("Le rôle d’administrateur plateforme ne peut être attribué que par le processus d’initialisation de la plateforme");
        String email = UserService.normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("L’adresse e-mail est déjà utilisée");
        UserEntity user = users.save(new UserEntity(request.fullName().strip(), email,
                passwords.hash(request.password()), request.role(), companyId));
        return UserDtos.Response.from(user);
    }

    @Transactional
    public UserDtos.Response setUserStatus(UUID companyId, UUID userId, UserDtos.StatusRequest request) {
        find(companyId);
        UserEntity user = users.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        if (user.getRole() == UserRole.APP_ADMIN) throw new ConflictException("Les administrateurs de l’application sont gérés par le processus d’initialisation de la plateforme");
        if (!request.enabled() && user.isEnabled() && user.getRole() == UserRole.ADMIN
                && users.countByCompanyIdAndRoleAndEnabledTrue(companyId, UserRole.ADMIN) <= 1)
            throw new ConflictException("Le dernier administrateur actif de l’entreprise ne peut pas être désactivé");
        user.setEnabled(request.enabled());
        return UserDtos.Response.from(user);
    }

    @Transactional
    public CompanyDtos.Response updateCurrent(CompanyDtos.UpdateRequest request, AuthenticatedUser actor) {
        return update(actor.companyId(), request);
    }

    @Transactional
    public CompanyDtos.Response update(UUID id, CompanyDtos.UpdateRequest request) {
        Company company = find(id);
        requireVersion(company, request.version());
        String ice = normalizeIce(request.ice());
        if (ice != null && companies.existsByIceAndIdNot(ice, id)) throw new ConflictException("L’ICE de l’entreprise est déjà utilisé");
        company.update(request.companyName().strip(), optional(request.address()), optional(request.phone()), normalizeEmail(request.email()), ice);
        return CompanyDtos.Response.from(companies.saveAndFlush(company));
    }

    @Transactional
    public CompanyDtos.Response setStatus(UUID id, CompanyDtos.StatusRequest request, AuthenticatedUser actor) {
        Company company = find(id);
        if (company.getId().equals(actor.companyId()) && !request.enabled())
            throw new ConflictException("L’entreprise utilisée par l’administrateur actuel de l’application ne peut pas être désactivée");
        if (company.isEnabled() == request.enabled()) return CompanyDtos.Response.from(company);
        requireVersion(company, request.version());
        company.setEnabled(request.enabled());
        return CompanyDtos.Response.from(companies.saveAndFlush(company));
    }

    private Company find(UUID id) { return companies.findById(id).orElseThrow(() -> new NotFoundException("Entreprise introuvable")); }
    private static void requireVersion(Company company, long version) { if (company.getVersion() != version) throw new ConflictException("L’entreprise a été modifiée par un autre utilisateur"); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String normalizeEmail(String value) { String normalized = optional(value); return normalized == null ? null : normalized.toLowerCase(Locale.ROOT); }
    private static String normalizeIce(String value) { return value == null || value.isBlank() ? null : value.replaceAll("\\s", ""); }
}
