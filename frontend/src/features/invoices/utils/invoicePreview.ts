export interface InvoiceLinePreview {
  grossAmountExcludingTax: number;
  discountAmount: number;
  netAmountExcludingTax: number;
  vatAmount: number;
  totalIncludingTax: number;
}

export interface InvoicePreview extends InvoiceLinePreview {
  lines: InvoiceLinePreview[];
}

const money = (value: number) =>
  Math.round((value + Number.EPSILON) * 100) / 100;

export function calculateLinePreview(
  quantity: unknown,
  price: unknown,
  vatRate: unknown,
  discountRate: unknown,
): InvoiceLinePreview {
  const values = [quantity, price, vatRate, discountRate].map(Number);
  if (
    values.some((value) => !Number.isFinite(value) || value < 0) ||
    values[0] <= 0
  ) {
    return {
      grossAmountExcludingTax: 0,
      discountAmount: 0,
      netAmountExcludingTax: 0,
      vatAmount: 0,
      totalIncludingTax: 0,
    };
  }
  const grossAmountExcludingTax = money(values[0] * values[1]);
  const discountAmount = money((grossAmountExcludingTax * values[3]) / 100);
  const netAmountExcludingTax = money(grossAmountExcludingTax - discountAmount);
  const vatAmount = money((netAmountExcludingTax * values[2]) / 100);
  return {
    grossAmountExcludingTax,
    discountAmount,
    netAmountExcludingTax,
    vatAmount,
    totalIncludingTax: money(netAmountExcludingTax + vatAmount),
  };
}

export function calculateInvoicePreview(
  lines: Array<{
    quantity: unknown;
    unitPriceExcludingTax: unknown;
    vatRate: unknown;
    discountRate: unknown;
  }>,
): InvoicePreview {
  const previews = lines.map((line) =>
    calculateLinePreview(
      line.quantity,
      line.unitPriceExcludingTax,
      line.vatRate,
      line.discountRate,
    ),
  );
  return previews.reduce<InvoicePreview>(
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
    {
      lines: [],
      grossAmountExcludingTax: 0,
      discountAmount: 0,
      netAmountExcludingTax: 0,
      vatAmount: 0,
      totalIncludingTax: 0,
    },
  );
}
