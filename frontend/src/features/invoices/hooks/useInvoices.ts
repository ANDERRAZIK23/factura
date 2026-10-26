import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  cancelInvoice,
  createInvoice,
  downloadInvoicePdf,
  getInvoiceById,
  getInvoices,
  markInvoicePaid,
  updateInvoice,
  validateInvoice,
} from "../api/invoiceApi";
import type { InvoiceInput, InvoiceListParams } from "../types/invoice";
import { useAuth } from "../../../auth/useAuth";

export const invoiceKeys = {
  all: ["invoices"] as const,
  lists: () => ["invoices", "list"] as const,
  list: (params: InvoiceListParams) => ["invoices", "list", params] as const,
  details: () => ["invoices", "detail"] as const,
  detail: (id: string) => ["invoices", "detail", id] as const,
  pdf: (id: string) => ["invoices", "pdf", id] as const,
};

export function useInvoices(params: InvoiceListParams) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...invoiceKeys.list(params), user?.companyId],
    queryFn: () => getInvoices(params),
    placeholderData: (previous) => previous,
  });
}

export function useInvoice(id: string) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...invoiceKeys.detail(id), user?.companyId],
    queryFn: () => getInvoiceById(id),
    enabled: Boolean(id),
    retry: false,
  });
}

export function useInvoicePdf(id: string) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...invoiceKeys.pdf(id), user?.companyId],
    queryFn: () => downloadInvoicePdf(id),
    // Validation stores an immutable PDF; payment and design changes do not replace it.
    staleTime: Infinity,
    retry: false,
  });
}

function refresh(
  cache: ReturnType<typeof useQueryClient>,
  id: string,
  companyId?: string,
  invoice?: Awaited<ReturnType<typeof getInvoiceById>>,
) {
  if (invoice)
    cache.setQueryData([...invoiceKeys.detail(id), companyId], invoice);
  cache.invalidateQueries({ queryKey: invoiceKeys.lists() });
  cache.invalidateQueries({ queryKey: ["sales", companyId] });
}

export function useCreateInvoice() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: createInvoice,
    onSuccess: () => cache.invalidateQueries({ queryKey: invoiceKeys.lists() }),
  });
}

export function useUpdateInvoice(id: string) {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: (input: InvoiceInput & { version: number }) =>
      updateInvoice(id, input),
    onSuccess: (invoice) => refresh(cache, id, user?.companyId, invoice),
  });
}

export function useValidateInvoice() {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      validateInvoice(id, version),
    onSuccess: (invoice) =>
      refresh(cache, invoice.id, user?.companyId, invoice),
  });
}

export function useMarkInvoicePaid() {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      markInvoicePaid(id, version),
    onSuccess: (invoice) =>
      refresh(cache, invoice.id, user?.companyId, invoice),
  });
}

export function useCancelInvoice() {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      cancelInvoice(id, version),
    onSuccess: (invoice) =>
      refresh(cache, invoice.id, user?.companyId, invoice),
  });
}

export function useDownloadInvoicePdf() {
  return useMutation({ mutationFn: downloadInvoicePdf });
}
