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

export const LINE_VAT_RATE = "line";

export function effectiveVatRate<T>(
  commonRate: T | typeof LINE_VAT_RATE,
  lineRate: T,
) {
  return commonRate === LINE_VAT_RATE ? lineRate : commonRate;
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
  const parsedQuantity = scaledInteger(quantity, 3);
  const parsedPrice = scaledInteger(price, 2);
  const parsedDiscount = scaledInteger(
    discountRate === "" ? "0" : discountRate,
    2,
  );
  const parsedVat = scaledInteger(vatRate, 2);
  if (
    parsedQuantity === null ||
    parsedQuantity <= 0n ||
    parsedPrice === null ||
    parsedPrice < 0n ||
    parsedDiscount === null ||
    parsedDiscount < 0n ||
    parsedDiscount > 10_000n ||
    parsedVat === null ||
    parsedVat < 0n ||
    parsedVat > 10_000n
  ) {
    return emptyLine();
  }

  // Match the server's half-up rounding at each monetary step.
  const gross = (parsedQuantity * parsedPrice + 500n) / 1_000n;
  const discount = (gross * parsedDiscount + 5_000n) / 10_000n;
  const net = gross - discount;
  const vat = (net * parsedVat + 5_000n) / 10_000n;
  return {
    grossAmountExcludingTax: Number(gross) / 100,
    discountAmount: Number(discount) / 100,
    netAmountExcludingTax: Number(net) / 100,
    vatAmount: Number(vat) / 100,
    totalIncludingTax: Number(net + vat) / 100,
  };
}

function scaledInteger(value: unknown, decimals: number): bigint | null {
  const text = String(value).trim();
  if (!/^-?(?:\d+\.?\d*|\.\d+)$/.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  if (fraction.length > decimals) return null;
  return BigInt(whole + fraction.padEnd(decimals, "0"));
}

export function calculateInvoiceCreatePreview(
  lines: Array<{
    productId?: string;
    quantity: unknown;
    unitPriceExcludingTax: unknown;
    discountRate: unknown;
    vatRate?: unknown;
  }>,
  vatRate: unknown,
): InvoiceCreatePreview {
  const previews = lines.map((line) =>
    line.productId === ""
      ? emptyLine()
      : calculateLine(
          line.quantity,
          line.unitPriceExcludingTax,
          line.discountRate,
          effectiveVatRate(vatRate, line.vatRate),
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
