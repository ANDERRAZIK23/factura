package ma.facture.invoicing.contract.design;

import ma.facture.invoicing.common.*;
import ma.facture.invoicing.company.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserRole;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ContractDesignService {
    private final ContractDesignRepository designs;
    private final CompanyRepository companies;
    private final CompanyBrandingRepository branding;
    private final ContractDocumentValidator validator;
    private final ContractDesignPdfRenderer renderer;
    private final Clock clock;
    public ContractDesignService(ContractDesignRepository designs, CompanyRepository companies, CompanyBrandingRepository branding,
            ContractDocumentValidator validator, ContractDesignPdfRenderer renderer, Clock clock) {
        this.designs = designs; this.companies = companies; this.branding = branding;
        this.validator = validator; this.renderer = renderer; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public ContractDesignDtos.Response get(UUID companyId, AuthenticatedUser actor) {
        requireAdmin(actor);
        company(companyId);
        return designs.findById(companyId).map(this::response)
                .orElseGet(() -> new ContractDesignDtos.Response(companyId, ContractDesignSettings.defaults(), null, null, false));
    }
    @Transactional
    public ContractDesignDtos.Response save(UUID companyId, ContractDesignDtos.Request request, AuthenticatedUser actor) {
        requireAdmin(actor);
        validator.validate(request.settings().content());
        // Lock the parent to serialize first saves, before a design row exists.
        companies.findByIdForUpdate(companyId).orElseThrow(() -> new NotFoundException("Entreprise introuvable"));
        ContractDesign design = designs.findById(companyId).orElse(null);
        if (!Objects.equals(design == null ? null : design.getVersion(), request.version())) throw concurrentChange();
        if (design == null) design = new ContractDesign(companyId, request.settings(), actor.id(), Instant.now(clock));
        else design.update(request.settings(), actor.id(), Instant.now(clock));
        try { return response(designs.saveAndFlush(design)); }
        catch (ObjectOptimisticLockingFailureException exception) { throw concurrentChange(); }
    }
    @Transactional(readOnly = true)
    public byte[] pdf(UUID companyId, ContractDesignSettings settings, AuthenticatedUser actor) {
        requireAdmin(actor);
        validator.validate(settings.content());
        Company company = company(companyId);
        byte[] logo = settings.showLogo() ? branding.findById(companyId).map(CompanyBranding::getLogoData).orElse(null) : null;
        return renderer.render(settings, company, logo);
    }
    private Company company(UUID id) { return companies.findById(id).orElseThrow(() -> new NotFoundException("Entreprise introuvable")); }
    private static void requireAdmin(AuthenticatedUser actor) {
        if (actor.role() != UserRole.APP_ADMIN) throw new AccessDeniedException("Accès réservé à l’administrateur de l’application");
    }
    private static ConflictException concurrentChange() {
        return new ConflictException("Ce modèle a été modifié par un autre administrateur. Rechargez la version enregistrée avant de réessayer.");
    }
    private ContractDesignDtos.Response response(ContractDesign value) {
        return new ContractDesignDtos.Response(value.getCompanyId(), value.getSettings(), value.getVersion(), value.getUpdatedAt(), true);
    }
}
