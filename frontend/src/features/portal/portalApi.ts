import axios from "axios";
import { api, API_URL } from "../../api/client";
import type { InvoiceStatus } from "../invoices/types/invoice";

const portal = axios.create({ baseURL: API_URL });
export interface PortalInvoice {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string | null;
  status: InvoiceStatus;
  totalIncludingTax: number;
  paidAmount: number;
  outstandingAmount: number;
  pdfAvailable: boolean;
}
export interface PortalPage {
  companyName: string;
  clientName: string;
  invoices: PortalInvoice[];
  page: number;
  hasNext: boolean;
  totalOutstanding: number;
}
export interface PortalPayment {
  amount: number;
  paymentDate: string | null;
  paymentMethod: string;
  reversedAt: string | null;
}
export const portalApi = {
  info: async (id: string) =>
    (
      await api.get<{ expiresAt: string | null; active: boolean }>(
        `/clients/${id}/portal-access`,
      )
    ).data,
  create: async (id: string, days: number) =>
    (
      await api.post<{ token: string; expiresAt: string }>(
        `/clients/${id}/portal-access`,
        { days },
      )
    ).data,
  revoke: async (id: string) => {
    await api.delete(`/clients/${id}/portal-access`);
  },
  list: async (token: string, page: number) =>
    (
      await portal.get<PortalPage>("/portal/invoices", {
        headers: { Authorization: `Portal ${token}` },
        params: { page },
      })
    ).data,
  pdf: async (token: string, id: string) =>
    (
      await portal.get<Blob>(`/portal/invoices/${id}/pdf`, {
        headers: { Authorization: `Portal ${token}` },
        responseType: "blob",
      })
    ).data,
  payments: async (token: string, id: string) =>
    (
      await portal.get<PortalPayment[]>(`/portal/invoices/${id}/payments`, {
        headers: { Authorization: `Portal ${token}` },
      })
    ).data,
};
