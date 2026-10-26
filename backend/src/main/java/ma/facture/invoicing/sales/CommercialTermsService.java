package ma.facture.invoicing.sales;

import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.company.CompanyRepository;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.product.entity.Product;
import ma.facture.invoicing.common.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CommercialTermsService {
    private final ClientRepository clients;
    private final CompanyRepository companies;
    private final ProductRepository products;
    private final InvoiceCalculationService calculations;
    public CommercialTermsService(ClientRepository clients, CompanyRepository companies, ProductRepository products, InvoiceCalculationService calculations) {
        this.clients = clients; this.companies = companies; this.products = products; this.calculations = calculations;
    }
    public CommercialTerms prepare(InvoiceCreateRequest request, AuthenticatedUser actor) {
        if (request.contractId() != null) throw new ValidationException("Utilisez les prix explicites du devis ou du modèle de récurrence");
        var client = clients.findByIdAndCompanyId(request.clientId(), actor.companyId()).orElseThrow(() -> new NotFoundException("Client introuvable"));
        if (!client.isActive()) throw new ConflictException("Le client doit être actif");
        var company = companies.findById(actor.companyId()).filter(c -> c.isEnabled()).orElseThrow(() -> new NotFoundException("Entreprise indisponible"));
        if (request.paymentMethod() == null || !Set.of(PaymentMethod.CASH, PaymentMethod.CHECK, PaymentMethod.BANK_TRANSFER).contains(request.paymentMethod()))
            throw new ValidationException("Choisissez Espèces, Chèque ou Virement bancaire");
        if (request.lines() == null || request.lines().isEmpty() || request.lines().size() > 200)
            throw new ValidationException("Ajoutez entre 1 et 200 articles");
        List<Product> selected = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (var line : request.lines()) {
            if (!ids.add(line.productId())) throw new ValidationException("Un produit ne peut apparaître qu’une seule fois");
            Product product = products.findByIdAndCompanyId(line.productId(), actor.companyId()).orElseThrow(() -> new NotFoundException("Produit introuvable"));
            if (!product.isActive()) throw new ConflictException("Tous les articles doivent être actifs");
            if (line.unitPriceExcludingTax() == null || line.vatRate() == null) throw new ValidationException("Renseignez le prix et la TVA de chaque article");
            selected.add(product);
        }
        var totals = calculations.calculate(request.lines().stream().map(l -> new InvoiceLineInput(l.quantity(), l.unitPriceExcludingTax(), l.vatRate(), l.discountRate())).toList());
        List<CommercialTerms.Line> lines = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            Product p = selected.get(i);
            lines.add(new CommercialTerms.Line(p.getId(), p.getProductCode(), p.getProductName(), p.getDescription(), p.getUnit().name(), totals.lines().get(i)));
        }
        return new CommercialTerms(client.getId(), new CommercialTerms.Party(company.getCompanyName(), company.getAddress(), company.getPhone(), company.getEmail(), company.getIce()),
                new CommercialTerms.Party(client.getClientName(), client.getAddress(), client.getPhoneNumber(), client.getEmail(), client.getIce()),
                request.paymentMethod(), request.customerOrderReference(), lines, totals);
    }
}
