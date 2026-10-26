export interface InvoiceCreateLinePreview {
  grossAmountExcludingTax: number;
  discountAmount: number;
  netAmountExcludingTax: number;
  vatAmount: number;
  totalIncludingTax: number;
}

export interface InvoiceCreatePreview extends InvoiceCreateLinePreview {
  lines: InvoiceCreateLinePreview[];
}

const money = (value: number) =>
  Math.round((value + Number.EPSILON) * 100) / 100;
const emptyLine = (): InvoiceCreateLinePreview => ({
  grossAmountExcludingTax: 0,
  discountAmount: 0,
  netAmountExcludingTax: 0,
  vatAmount: 0,
  totalIncludingTax: 0,
});

function calculateLine(
  quantity: unknown,
  price: unknown,
  discountRate: unknown,
  vatRate: unknown,
): InvoiceCreateLinePreview {
  const [parsedQuantity, parsedPrice, parsedDiscount, parsedVat] = [
    quantity,
    price,
    discountRate,
    vatRate,
  ].map(Number);
  if (
    ![parsedQuantity, parsedPrice, parsedDiscount, parsedVat].every(
      Number.isFinite,
    ) ||
    parsedQuantity <= 0 ||
    parsedPrice < 0 ||
    parsedDiscount < 0 ||
    parsedDiscount > 100 ||
    parsedVat < 0
  ) {
    return emptyLine();
  }

  const grossAmountExcludingTax = money(parsedQuantity * parsedPrice);
  const discountAmount = money(
    (grossAmountExcludingTax * parsedDiscount) / 100,
  );
  const netAmountExcludingTax = money(grossAmountExcludingTax - discountAmount);
  const vatAmount = money((netAmountExcludingTax * parsedVat) / 100);
  return {
    grossAmountExcludingTax,
    discountAmount,
    netAmountExcludingTax,
    vatAmount,
    totalIncludingTax: money(netAmountExcludingTax + vatAmount),
  };
}

export function calculateInvoiceCreatePreview(
  lines: Array<{
    quantity: unknown;
    unitPriceExcludingTax: unknown;
    discountRate: unknown;
  }>,
  vatRate: unknown,
): InvoiceCreatePreview {
  const previews = lines.map((line) =>
    calculateLine(
      line.quantity,
      line.unitPriceExcludingTax,
      line.discountRate,
      vatRate,
    ),
  );
  return previews.reduce<InvoiceCreatePreview>(
    (total, line) => ({
      lines: total.lines.concat(line),
      grossAmountExcludingTax: money(
        total.grossAmountExcludingTax + line.grossAmountExcludingTax,
      ),
      discountAmount: money(total.discountAmount + line.discountAmount),
      netAmountExcludingTax: money(
        total.netAmountExcludingTax + line.netAmountExcludingTax,
      ),
      vatAmount: money(total.vatAmount + line.vatAmount),
      totalIncludingTax: money(
        total.totalIncludingTax + line.totalIncludingTax,
      ),
    }),
    { lines: [], ...emptyLine() },
  );
}
