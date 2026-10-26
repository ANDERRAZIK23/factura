package ma.facture.invoicing.user;

import ma.facture.invoicing.common.*;
import ma.facture.invoicing.security.PasswordService;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.company.TenantDefaults;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository repository; private final PasswordService passwords;
    public UserService(UserRepository repository, PasswordService passwords) { this.repository = repository; this.passwords = passwords; }
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    @Transactional(readOnly=true) public Page<UserDtos.Response> list(Pageable pageable) { return repository.findAllByCompanyId(TenantDefaults.DEFAULT_COMPANY_ID, pageable).map(UserDtos.Response::from); }
    @Transactional(readOnly=true) public Page<UserDtos.Response> list(Pageable pageable, AuthenticatedUser actor) { return repository.findAllByCompanyId(actor.companyId(), pageable).map(UserDtos.Response::from); }
    @Transactional(readOnly=true) public UserDtos.Response get(UUID id) { return UserDtos.Response.from(find(id)); }
    @Transactional(readOnly=true) public UserDtos.Response get(UUID id, AuthenticatedUser actor) { return UserDtos.Response.from(find(id, actor.companyId())); }
    @Transactional public UserDtos.Response create(UserDtos.CreateRequest request) { return createForCompany(request, TenantDefaults.DEFAULT_COMPANY_ID); }
    @Transactional public UserDtos.Response create(UserDtos.CreateRequest request, AuthenticatedUser actor) { return createForCompany(request, actor.companyId()); }
    private UserDtos.Response createForCompany(UserDtos.CreateRequest request, UUID companyId) {
        rejectAppAdminRole(request.role());
        String email = normalizeEmail(request.email()); if (repository.existsByEmailIgnoreCase(email)) throw new ConflictException("L’adresse e-mail est déjà utilisée");
        var saved = repository.save(new UserEntity(request.fullName().strip(), email, passwords.hash(request.password()), request.role(), companyId));
        log.info("User created id={} role={}", saved.getId(), saved.getRole()); return UserDtos.Response.from(saved);
    }
    @Transactional public UserDtos.Response update(UUID id, UserDtos.UpdateRequest request) {
        return updateUser(find(id), id, request);
    }
    @Transactional public UserDtos.Response update(UUID id, UserDtos.UpdateRequest request, AuthenticatedUser actor) {
        return updateUser(find(id, actor.companyId()), id, request);
    }
    private UserDtos.Response updateUser(UserEntity user, UUID id, UserDtos.UpdateRequest request) {
        rejectAppAdminRole(request.role()); String email = normalizeEmail(request.email());
        if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new ConflictException("L’adresse e-mail est déjà utilisée");
        String hash = request.password() == null || request.password().isBlank() ? null : passwords.hash(request.password());
        user.update(request.fullName().strip(), email, request.role(), hash); return UserDtos.Response.from(user);
    }
    @Transactional public UserDtos.Response setStatus(UUID id, boolean enabled) {
        var user = find(id);
        if (!enabled && user.isEnabled() && user.getRole() == UserRole.ADMIN && repository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1)
            throw new ConflictException("Le dernier administrateur actif ne peut pas être désactivé");
        user.setEnabled(enabled); return UserDtos.Response.from(user);
    }
    @Transactional public UserDtos.Response setStatus(UUID id, boolean enabled, AuthenticatedUser actor) {
        var user = find(id, actor.companyId());
        if (user.getRole() == UserRole.APP_ADMIN) throw new ConflictException("Les administrateurs de l’application sont gérés par la plateforme");
        if (!enabled && user.isEnabled() && user.getRole() == UserRole.ADMIN
                && repository.countByCompanyIdAndRoleAndEnabledTrue(actor.companyId(), UserRole.ADMIN) <= 1)
            throw new ConflictException("Le dernier administrateur actif de l’entreprise ne peut pas être désactivé");
        user.setEnabled(enabled); return UserDtos.Response.from(user);
    }
    private UserEntity find(UUID id) { return repository.findById(id).orElseThrow(() -> new NotFoundException("Utilisateur introuvable")); }
    private UserEntity find(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new NotFoundException("Utilisateur introuvable")); }
    private static void rejectAppAdminRole(UserRole role) { if (role == UserRole.APP_ADMIN) throw new ConflictException("Le rôle d’administrateur plateforme ne peut être attribué que par la plateforme"); }
}
