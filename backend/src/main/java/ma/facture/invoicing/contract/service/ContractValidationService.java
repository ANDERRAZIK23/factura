package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.entity.Contract;
import ma.facture.invoicing.contract.exception.*;
import ma.facture.invoicing.client.entity.Client;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class ContractValidationService {
    private final ContractPricingService pricing;
    public ContractValidationService(ContractPricingService pricing) { this.pricing = pricing; }

    public void validateDates(LocalDate start, LocalDate end) {
        if (start == null) throw new ContractValidationException("La date de début est requise");
        if (end != null && end.isBefore(start)) throw new ContractValidationException("La date de fin ne peut pas précéder la date de début");
    }
    public void validateClientForDraft(Client client) {
        if (client == null) throw new ContractValidationException("Le client est requis");
        if (!client.isActive()) throw new ContractValidationException("Un client actif est requis pour créer un contrat");
    }
    public BigDecimal defaultDiscount(BigDecimal value) {
        return pricing.normalizeRate(value == null ? BigDecimal.ZERO : value, "Le taux de remise par défaut");
    }
    public void validateActivation(Contract contract, LocalDate today) {
        if (!contract.getClient().isActive()) throw new ContractConflictException("Le client du contrat doit être actif avant l’activation");
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) throw new ContractConflictException("Un contrat déjà expiré ne peut pas être activé");
        if (contract.getLines().isEmpty()) throw new ContractConflictException("Un contrat doit contenir au moins un produit avant l’activation");
        if (contract.getLines().stream().anyMatch(line -> !line.getProduct().isActive()))
            throw new ContractConflictException("Tous les produits du contrat doivent être actifs avant l’activation");
    }
    public boolean usable(Contract contract, LocalDate today) {
        return contract.getStatus() == ma.facture.invoicing.contract.entity.ContractStatus.ACTIVE
                && !contract.getStartDate().isAfter(today)
                && (contract.getEndDate() == null || !contract.getEndDate().isBefore(today));
    }
    public static String notes(String value, int maximum, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maximum) throw new ContractValidationException(field + " ne doit pas dépasser " + maximum + " caractères");
        return normalized;
    }
}
