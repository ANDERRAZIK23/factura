import { api } from "../../api/client";
import type {
  Invoice,
  InvoiceInput,
  InvoiceSummary,
  PaymentMethod,
} from "../invoices/types/invoice";

export interface Page<T> {
  content: T[];
  page: {
    number: number;
    totalPages: number;
    totalElements: number;
    size: number;
  };
}
export interface Amounts {
  quantity: number;
  unitPriceExcludingTax: number;
  vatRate: number;
  discountRate: number;
  netAmountExcludingTax: number;
  totalIncludingTax: number;
}
export interface Terms {
  clientId: string;
  client: { name: string; address: string | null; ice: string | null };
  lines: {
    productId: string;
    productCode: string;
    productName: string;
    amounts: Amounts;
  }[];
  totals: {
    taxableAmount: number;
    totalVat: number;
    totalIncludingTax: number;
  };
}
export interface Quotation {
  id: string;
  quoteNumber: string;
  issueDate: string;
  validUntil: string;
  terms: Terms;
  status: "OPEN" | "CONVERTED" | "CANCELLED";
  invoiceId: string | null;
  version: number;
}
export interface Recurrence {
  id: string;
  title: string;
  terms: Terms;
  anchorDate: string;
  nextDate: string;
  endDate: string | null;
  intervalMonths: number;
  paymentTermsDays: number;
  enabled: boolean;
  lastInvoiceId: string | null;
  lastError: string | null;
  version: number;
}
export interface Payment {
  id: string;
  amount: number;
  paymentDate: string | null;
  paymentMethod: PaymentMethod;
  reference: string;
  entryType: string;
  reversedAt: string | null;
  reversalReason: string | null;
}
export interface Reminder {
  id: string;
  recipient: string | null;
  subject: string;
  body: string;
  createdAt: string;
}
export interface PaymentInput {
  version: number;
  amount: number;
  paymentDate: string;
  paymentMethod: string;
  reference: string;
}
export interface RecurringInput {
  invoiceId: string;
  title: string;
  startDate: string;
  endDate: string | null;
  intervalMonths: number;
  paymentTermsDays: number;
}

export const salesApi = {
  quotes: async (page: number) =>
    (await api.get<Page<Quotation>>("/quotations", { params: { page } })).data,
  quote: async (id: string) =>
    (await api.get<Quotation>(`/quotations/${id}`)).data,
  createQuote: async (input: { document: InvoiceInput; validUntil: string }) =>
    (await api.post<Quotation>("/quotations", input)).data,
  convertQuote: async (id: string, version: number) =>
    (await api.post<Invoice>(`/quotations/${id}/convert`, { version })).data,
  cancelQuote: async (id: string, version: number) =>
    (await api.post<Quotation>(`/quotations/${id}/cancel`, { version })).data,
  quotePdf: async (id: string) =>
    (await api.get<Blob>(`/quotations/${id}/pdf`, { responseType: "blob" }))
      .data,
  recurrences: async (page: number) =>
    (
      await api.get<Page<Recurrence>>("/recurring-invoices", {
        params: { page },
      })
    ).data,
  createRecurrence: async (input: RecurringInput) =>
    (await api.post<Recurrence>("/recurring-invoices", input)).data,
  toggleRecurrence: async (id: string, version: number, enabled: boolean) =>
    (
      await api.put<Recurrence>(`/recurring-invoices/${id}/status`, {
        version,
        enabled,
      })
    ).data,
  generateRecurrence: async (id: string, version: number) =>
    (await api.post<Invoice>(`/recurring-invoices/${id}/generate`, { version }))
      .data,
  payments: async (id: string) =>
    (await api.get<Payment[]>(`/invoices/${id}/payments`)).data,
  pay: async (id: string, input: PaymentInput) =>
    (await api.post<Invoice>(`/invoices/${id}/payments`, input)).data,
  reversePayment: async (
    id: string,
    paymentId: string,
    version: number,
    reason: string,
  ) =>
    (
      await api.post<Invoice>(`/invoices/${id}/payments/${paymentId}/reverse`, {
        version,
        reason,
      })
    ).data,
  dueDate: async (id: string, version: number, dueDate: string | null) =>
    (await api.put<Invoice>(`/invoices/${id}/due-date`, { version, dueDate }))
      .data,
  overdue: async (page: number) =>
    (
      await api.get<Page<InvoiceSummary>>("/collections/overdue", {
        params: { page },
      })
    ).data,
  reminders: async (id: string) =>
    (await api.get<Reminder[]>(`/invoices/${id}/reminders`)).data,
  prepareReminder: async (id: string) =>
    (await api.post<Reminder>(`/invoices/${id}/reminders`)).data,
};

export function savePdf(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
