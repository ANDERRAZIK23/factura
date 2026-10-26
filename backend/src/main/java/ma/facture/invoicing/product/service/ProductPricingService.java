package ma.facture.invoicing.product.service;

import ma.facture.invoicing.product.exception.ProductValidationException;
import org.springframework.stereotype.Service;
import java.math.*;

@Service
public class ProductPricingService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    public BigDecimal normalizePrice(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new ProductValidationException("Le prix unitaire HT doit être positif ou nul");
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.precision() - normalized.scale() > 17) throw new ProductValidationException("Le prix unitaire HT dépasse la précision prise en charge");
        return normalized;
    }
    public BigDecimal normalizeVat(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) throw new ProductValidationException("Le taux de TVA par défaut doit être compris entre 0 et 100");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    public BigDecimal includingTax(BigDecimal price, BigDecimal vat) {
        return price.multiply(BigDecimal.ONE.add(vat.divide(HUNDRED))).setScale(2, RoundingMode.HALF_UP);
    }
}
