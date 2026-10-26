package ma.facture.invoicing.invoice.calculation;

import ma.facture.invoicing.invoice.exception.InvoiceCalculationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvoiceCalculationService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999999999999.99");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999999999999.999");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    public InvoiceCalculation calculate(List<InvoiceLineInput> inputs) {
        if (inputs == null || inputs.isEmpty())
            throw new InvoiceCalculationException("Une facture doit contenir au moins une ligne");

        List<InvoiceLineCalculation> lines = new ArrayList<>(inputs.size());
        BigDecimal subtotal = ZERO_MONEY;
        BigDecimal discount = ZERO_MONEY;
        BigDecimal taxable = ZERO_MONEY;
        BigDecimal vat = ZERO_MONEY;
        BigDecimal total = ZERO_MONEY;

        for (InvoiceLineInput input : inputs) {
            InvoiceLineCalculation line = calculateLine(input);
            lines.add(line);
            subtotal = addMoney(subtotal, line.grossAmountExcludingTax(), "Le sous-total de la facture");
            discount = addMoney(discount, line.discountAmount(), "Le total des remises de la facture");
            taxable = addMoney(taxable, line.netAmountExcludingTax(), "Le montant imposable de la facture");
            vat = addMoney(vat, line.vatAmount(), "Le total de TVA de la facture");
            total = addMoney(total, line.totalIncludingTax(), "Le total TTC de la facture");
        }
        return new InvoiceCalculation(lines, subtotal, discount, taxable, vat, total);
    }

    public InvoiceLineCalculation calculateLine(InvoiceLineInput input) {
        if (input == null) throw new InvoiceCalculationException("La ligne de facture est requise");
        BigDecimal quantity = normalizeQuantity(input.quantity());
        BigDecimal price = normalizeMoneyInput(input.unitPriceExcludingTax(), "Le prix unitaire HT");
        BigDecimal vatRate = normalizeRate(input.vatRate(), "Le taux de TVA");
        BigDecimal discountRate = normalizeRate(input.discountRate() == null ? BigDecimal.ZERO : input.discountRate(), "Le taux de remise");

        BigDecimal gross = money(quantity.multiply(price), "Le montant brut HT");
        BigDecimal discount = money(gross.multiply(discountRate).divide(HUNDRED), "Le montant de la remise");
        BigDecimal net = money(gross.subtract(discount), "Le montant net HT");
        BigDecimal vat = money(net.multiply(vatRate).divide(HUNDRED), "Le montant de la TVA");
        BigDecimal includingTax = money(net.add(vat), "Le total TTC de la ligne");
        return new InvoiceLineCalculation(quantity, price, vatRate, discountRate, gross, discount, net, vat, includingTax);
    }

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) throw new InvoiceCalculationException("La quantité est requise");
        BigDecimal normalized = value.setScale(3, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) throw new InvoiceCalculationException("La quantité doit être supérieure à zéro");
        if (normalized.compareTo(MAX_QUANTITY) > 0) throw new InvoiceCalculationException("La quantité dépasse la précision prise en charge");
        return normalized;
    }

    private static BigDecimal normalizeMoneyInput(BigDecimal value, String field) {
        if (value == null) throw new InvoiceCalculationException(field + " est requis");
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) throw new InvoiceCalculationException(field + " doit être positif ou nul");
        if (normalized.compareTo(MAX_MONEY) > 0) throw new InvoiceCalculationException(field + " dépasse la précision prise en charge");
        return normalized;
    }

    private static BigDecimal normalizeRate(BigDecimal value, String field) {
        if (value == null) throw new InvoiceCalculationException(field + " est requis");
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0 || normalized.compareTo(HUNDRED) > 0)
            throw new InvoiceCalculationException(field + " doit être compris entre 0 et 100");
        return normalized;
    }

    private static BigDecimal money(BigDecimal value, String field) {
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(MAX_MONEY) > 0)
            throw new InvoiceCalculationException(field + " dépasse la précision prise en charge");
        return normalized;
    }

    private static BigDecimal addMoney(BigDecimal left, BigDecimal right, String field) {
        return money(left.add(right), field);
    }
}
