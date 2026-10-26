import type { ProductUnit } from "../../products/types/product";

export const INVOICE_STATUSES = [
  "DRAFT",
  "VALIDATED",
  "PAID",
  "CANCELLED",
] as const;
export const PAYMENT_METHODS = ["CASH", "CHECK", "BANK_TRANSFER"] as const;
export type InvoiceStatus = (typeof INVOICE_STATUSES)[number];
export type EditablePaymentMethod = (typeof PAYMENT_METHODS)[number];
export type PaymentMethod = EditablePaymentMethod | "CARD" | "CREDIT" | "OTHER";

export interface InvoiceClient {
  id: string;
  clientCode: string;
  clientName: string;
  address: string | null;
  phoneNumber: string | null;
  ice: string | null;
  active: boolean;
}

export interface InvoiceLine {
  id: string;
  productId: string;
  productCode: string;
  productName: string;
  productDescription: string | null;
  unit: ProductUnit;
  quantity: number;
  unitPriceExcludingTax: number;
  vatRate: number;
  discountRate: number;
  grossAmountExcludingTax: number;
  discountAmount: number;
  netAmountExcludingTax: number;
  vatAmount: number;
  totalIncludingTax: number;
}

export interface InvoiceSummary {
  id: string;
  invoiceNumber: string | null;
  invoiceDate: string;
  client: InvoiceClient;
  status: InvoiceStatus;
  subtotalExcludingTax: number;
  totalDiscount: number;
  taxableAmount: number;
  totalVat: number;
  totalIncludingTax: number;
  lineCount: number;
  paidAmount: number;
  outstandingAmount: number;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Invoice extends InvoiceSummary {
  paymentMethod: PaymentMethod;
  customerOrderReference: string | null;
  subtotalExcludingTax: number;
  totalDiscount: number;
  taxableAmount: number;
  totalVat: number;
  amountInWords: string | null;
  lines: InvoiceLine[];
  createdBy: string;
  updatedBy: string;
  createdByName: string | null;
  updatedByName: string | null;
  validatorName: string | null;
  validatorUserId: string | null;
  validatedAt: string | null;
  cancelledAt: string | null;
  pdfAvailable: boolean;
  pdfDownloadUrl: string | null;
}

export interface InvoiceLineInput {
  productId: string;
  quantity: number;
  unitPriceExcludingTax?: number;
  vatRate?: number;
  discountRate?: number;
}

export interface InvoiceInput {
  invoiceDate: string;
  clientId: string;
  paymentMethod: EditablePaymentMethod;
  customerOrderReference?: string;
  lines: InvoiceLineInput[];
}

export interface InvoicePage {
  content: InvoiceSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface InvoiceListParams {
  page: number;
  size: number;
  sort: string;
  search?: string;
  clientId?: string;
  status?: InvoiceStatus;
  invoiceDateFrom?: string;
  invoiceDateTo?: string;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  fieldErrors?: Record<string, string>;
  errors?: Record<string, string>;
}
