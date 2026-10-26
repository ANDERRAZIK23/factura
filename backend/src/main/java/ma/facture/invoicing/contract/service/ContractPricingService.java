package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.exception.ContractValidationException;
import org.springframework.stereotype.Service;
import java.math.*;

@Service
public class ContractPricingService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999999999999.99");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999999999999.999");

    public BigDecimal normalizePrice(BigDecimal value) {
        if (value == null) throw new ContractValidationException("Le prix unitaire négocié est requis");
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) throw new ContractValidationException("Le prix unitaire négocié doit être positif ou nul");
        if (normalized.compareTo(MAX_PRICE) > 0) throw new ContractValidationException("Le prix unitaire négocié dépasse la précision prise en charge");
        return normalized;
    }
    public BigDecimal normalizeRate(BigDecimal value, String field) {
        if (value == null) throw new ContractValidationException(field + " est requis");
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0 || normalized.compareTo(HUNDRED) > 0)
            throw new ContractValidationException(field + " doit être compris entre 0 et 100");
        return normalized;
    }
    public BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) return null;
        BigDecimal normalized = value.setScale(3, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) throw new ContractValidationException("La quantité minimale doit être supérieure à zéro");
        if (normalized.compareTo(MAX_QUANTITY) > 0) throw new ContractValidationException("La quantité minimale dépasse la précision prise en charge");
        return normalized;
    }
    public BigDecimal includingTax(BigDecimal price, BigDecimal vat) {
        return price.multiply(BigDecimal.ONE.add(vat.divide(HUNDRED))).setScale(2, RoundingMode.HALF_UP);
    }
}
