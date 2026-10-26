package ma.facture.invoicing.invoice.design;

import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.common.*;
import ma.facture.invoicing.company.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.entity.Invoice;
import ma.facture.invoicing.invoice.pdf.*;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserRole;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class InvoiceDesignService {
    private final InvoiceDesignRepository designs;
    private final CompanyRepository companies;
    private final CompanyBrandingRepository branding;
    private final DesignedInvoicePdfRenderer renderer;
    private final InvoiceCalculationService calculation;
    private final FrenchAmountInWordsService words;
    private final Clock clock;
    public InvoiceDesignService(InvoiceDesignRepository designs, CompanyRepository companies,
            CompanyBrandingRepository branding, DesignedInvoicePdfRenderer renderer, InvoiceCalculationService calculation,
            FrenchAmountInWordsService words, Clock clock) {
        this.designs = designs; this.companies = companies; this.branding = branding;
        this.renderer = renderer; this.calculation = calculation; this.words = words; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvoiceDesignDtos.Response get(UUID companyId, AuthenticatedUser actor) {
        UUID scope = scope(companyId, actor);
        return designs.findById(scope).map(this::response)
                .orElseGet(() -> new InvoiceDesignDtos.Response(scope, InvoiceDesignSettings.defaults(), null, null, false));
    }

    @Transactional
    public InvoiceDesignDtos.Response save(UUID companyId, InvoiceDesignDtos.Request request, AuthenticatedUser actor) {
        UUID scope = scope(companyId, actor);
        // Lock the company even when no design exists yet, so first saves cannot race.
        companies.findByIdForUpdate(scope)
                .orElseThrow(() -> new NotFoundException("Entreprise introuvable"));
        InvoiceDesign design = designs.findById(scope).orElse(null);
        if (!Objects.equals(design == null ? null : design.getVersion(), request.version()))
            throw new ConflictException("Le modèle a été modifié. Rechargez la version enregistrée avant de réessayer.");
        if (request.settings().layout() != null) preview(scope, request.settings(), actor);
        if (design == null) design = new InvoiceDesign(scope, request.settings(), actor.id(), Instant.now(clock));
        else design.update(request.settings(), actor.id(), Instant.now(clock));
        try { return response(designs.saveAndFlush(design)); }
        catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException("Le modèle a été modifié. Rechargez la version enregistrée avant de réessayer.");
        }
    }

    @Transactional(readOnly = true)
    public byte[] preview(UUID companyId, InvoiceDesignSettings settings, AuthenticatedUser actor) {
        UUID scope = scope(companyId, actor);
        Client client = new Client("CLI-EXEMPLE", "Client de démonstration", "+212 600 00 00 00",
                "25, rue des Orangers, Rabat", "client@example.com", "002233445566778", actor.id(), scope);
        Company company = companies.findById(scope).orElseThrow(() -> new NotFoundException("Entreprise introuvable"));
        Invoice sample = new Invoice(LocalDate.now(clock), client, null, PaymentMethod.BANK_TRANSFER, "BC-EXEMPLE", actor.id());
        List<InvoiceLineInput> inputs = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            BigDecimal quantity = BigDecimal.valueOf(index + 1);
            BigDecimal price = BigDecimal.valueOf(150 + index * 75);
            BigDecimal vat = BigDecimal.valueOf(20);
            BigDecimal discount = index == 1 ? BigDecimal.TEN : BigDecimal.ZERO;
            Product product = new Product("PRD-EXEMPLE-" + (index + 1), "Article de démonstration " + (index + 1),
                    "Description de l’article ou de la prestation", price, vat, ProductUnit.PIECE, actor.id(), scope);
            sample.addLine(product, quantity, price, vat, discount);
            inputs.add(new InvoiceLineInput(quantity, price, vat, discount));
        }
        sample.applyCalculation(calculation.calculate(inputs));
        sample.validate("APERÇU", actor.id(), Instant.now(clock));
        byte[] logo = settings.showLogo() ? branding.findById(scope).map(CompanyBranding::getLogoData).orElse(null) : null;
        return renderer.render(sample, words.convert(sample.getTotalIncludingTax()), company, settings, logo, true);
    }

    private UUID scope(UUID companyId, AuthenticatedUser actor) {
        if (actor.role() != UserRole.APP_ADMIN && actor.role() != UserRole.ADMIN) throw new AccessDeniedException("Accès réservé aux administrateurs");
        UUID scope = companyId == null ? actor.companyId() : companyId;
        if (scope == null) throw new ValidationException("Choisissez une entreprise pour son modèle de facture");
        if (actor.role() != UserRole.APP_ADMIN && !scope.equals(actor.companyId())) throw new AccessDeniedException("Accès à cette entreprise refusé");
        if (!companies.existsById(scope)) throw new NotFoundException("Entreprise introuvable");
        return scope;
    }
    private InvoiceDesignDtos.Response response(InvoiceDesign value) {
        return new InvoiceDesignDtos.Response(value.getCompanyId(), value.getSettings(), value.getVersion(), value.getUpdatedAt(), true);
    }
}
