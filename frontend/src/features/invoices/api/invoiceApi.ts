import { api } from "../../../api/client";
import type {
  Invoice,
  InvoiceInput,
  InvoiceListParams,
  InvoicePage,
} from "../types/invoice";

export async function getInvoices(
  params: InvoiceListParams,
): Promise<InvoicePage> {
  return (await api.get<InvoicePage>("/invoices", { params })).data;
}

export async function getInvoiceById(id: string): Promise<Invoice> {
  return (await api.get<Invoice>(`/invoices/${id}`)).data;
}

export async function createInvoice(input: InvoiceInput): Promise<Invoice> {
  return (await api.post<Invoice>("/invoices", input)).data;
}

export async function updateInvoice(
  id: string,
  input: InvoiceInput & { version: number },
): Promise<Invoice> {
  return (await api.put<Invoice>(`/invoices/${id}`, input)).data;
}

export async function validateInvoice(
  id: string,
  version: number,
): Promise<Invoice> {
  return (await api.post<Invoice>(`/invoices/${id}/validate`, { version }))
    .data;
}

export async function markInvoicePaid(
  id: string,
  version: number,
): Promise<Invoice> {
  return (await api.post<Invoice>(`/invoices/${id}/mark-paid`, { version }))
    .data;
}

export async function cancelInvoice(
  id: string,
  version: number,
): Promise<Invoice> {
  return (await api.post<Invoice>(`/invoices/${id}/cancel`, { version })).data;
}

export async function downloadInvoicePdf(
  id: string,
): Promise<{ blob: Blob; filename: string }> {
  const response = await api.get<Blob>(`/invoices/${id}/pdf`, {
    responseType: "blob",
  });
  const disposition = response.headers["content-disposition"] as
    string | undefined;
  const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plain = disposition?.match(/filename="?([^";]+)"?/i)?.[1];
  const filename = encoded
    ? decodeURIComponent(encoded)
    : (plain ?? `invoice-${id}.pdf`);
  return { blob: response.data, filename };
}
