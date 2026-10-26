package ma.facture.invoicing.invoice.entity;

import jakarta.persistence.*;
import ma.facture.invoicing.invoice.calculation.InvoiceLineCalculation;
import ma.facture.invoicing.product.entity.Product;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id", nullable = false) private Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "product_code_snapshot", length = 30) private String productCodeSnapshot;
    @Column(name = "product_name_snapshot", length = 200) private String productNameSnapshot;
    @Column(name = "product_description_snapshot") private String productDescriptionSnapshot;
    @Column(name = "unit_snapshot", length = 20) private String unitSnapshot;
    @Column(nullable = false, precision = 19, scale = 3) private BigDecimal quantity;
    @Column(name = "unit_price_excluding_tax", nullable = false, precision = 19, scale = 2) private BigDecimal unitPriceExcludingTax;
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2) private BigDecimal vatRate;
    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2) private BigDecimal discountRate;
    @Column(name = "gross_amount_excluding_tax", nullable = false, precision = 19, scale = 2) private BigDecimal grossAmountExcludingTax;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2) private BigDecimal discountAmount;
    @Column(name = "net_amount_excluding_tax", nullable = false, precision = 19, scale = 2) private BigDecimal netAmountExcludingTax;
    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 2) private BigDecimal vatAmount;
    @Column(name = "total_including_tax", nullable = false, precision = 19, scale = 2) private BigDecimal totalIncludingTax;

    protected InvoiceLine() {}

    InvoiceLine(Invoice invoice, Product product, BigDecimal quantity, BigDecimal price, BigDecimal vatRate,
                BigDecimal discountRate) {
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.product = product;
        this.quantity = quantity;
        this.unitPriceExcludingTax = price;
        this.vatRate = vatRate;
        this.discountRate = discountRate;
    }

    void applyCalculation(InvoiceLineCalculation calculation) {
        quantity = calculation.quantity();
        unitPriceExcludingTax = calculation.unitPriceExcludingTax();
        vatRate = calculation.vatRate();
        discountRate = calculation.discountRate();
        grossAmountExcludingTax = calculation.grossAmountExcludingTax();
        discountAmount = calculation.discountAmount();
        netAmountExcludingTax = calculation.netAmountExcludingTax();
        vatAmount = calculation.vatAmount();
        totalIncludingTax = calculation.totalIncludingTax();
    }

    void captureProductSnapshot() {
        productCodeSnapshot = product.getProductCode();
        productNameSnapshot = product.getProductName();
        productDescriptionSnapshot = product.getDescription();
        unitSnapshot = product.getUnit().name();
    }

    public UUID getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public Product getProduct() { return product; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getProductDescriptionSnapshot() { return productDescriptionSnapshot; }
    public String getUnitSnapshot() { return unitSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPriceExcludingTax() { return unitPriceExcludingTax; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public BigDecimal getGrossAmountExcludingTax() { return grossAmountExcludingTax; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getNetAmountExcludingTax() { return netAmountExcludingTax; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getTotalIncludingTax() { return totalIncludingTax; }
}
