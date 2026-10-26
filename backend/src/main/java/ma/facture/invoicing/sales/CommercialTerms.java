package ma.facture.invoicing.sales;

import ma.facture.invoicing.invoice.calculation.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import java.time.LocalDate;
import java.util.*;

/** Commercial snapshots shared by quotes and recurring draft preparation. */
public record CommercialTerms(UUID clientId, Party company, Party client, PaymentMethod paymentMethod,
        String customerOrderReference, List<Line> lines, InvoiceCalculation totals) {
    public CommercialTerms { lines = List.copyOf(lines); }
    public record Party(String name, String address, String phone, String email, String ice) {}
    public record Line(UUID productId, String productCode, String productName, String description, String unit,
                       InvoiceLineCalculation amounts) {}
    public InvoiceCreateRequest invoice(LocalDate date) {
        return new InvoiceCreateRequest(date, clientId, null, paymentMethod, customerOrderReference,
                lines.stream().map(l -> new InvoiceLineRequest(l.productId(), l.amounts().quantity(),
                        l.amounts().unitPriceExcludingTax(), l.amounts().vatRate(), l.amounts().discountRate())).toList());
    }
}
