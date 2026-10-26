import axios from "axios";
import { api } from "../../api/client";

export type InvoiceBlockKind =
  | "LOGO"
  | "COMPANY"
  | "TITLE"
  | "CLIENT"
  | "METADATA"
  | "ITEMS"
  | "TOTALS"
  | "AMOUNT_WORDS"
  | "FOOTER"
  | "TEXT"
  | "RECTANGLE";
export interface InvoiceBlock {
  id: string;
  kind: InvoiceBlockKind;
  x: number;
  y: number;
  width: number;
  height: number;
  backgroundColor: string | null;
  borderColor: string;
  borderWidth: number;
  textColor: string;
  fontSize: number;
  textAlignment: "LEFT" | "CENTER" | "RIGHT";
  text: string;
}
export interface InvoiceFreeLayout {
  blocks: InvoiceBlock[];
}
export interface InvoiceDesignSettings {
  template: "CLASSIC" | "MODERN" | "MINIMAL";
  accentColor: string;
  fontFamily: "SANS" | "SERIF" | "MONO";
  fontSize: number;
  paperFormat: "A4" | "LETTER";
  showLogo: boolean;
  logoPosition: "LEFT" | "RIGHT";
  clientPosition: "LEFT" | "RIGHT";
  showProductCode: boolean;
  showDescription: boolean;
  showUnit: boolean;
  showDiscount: boolean;
  showVat: boolean;
  showAmountInWords: boolean;
  footerText: string;
  layout?: InvoiceFreeLayout | null;
}
export interface InvoiceDesign {
  companyId: string;
  settings: InvoiceDesignSettings;
  version: number | null;
  updatedAt: string | null;
  customized: boolean;
}
const path = "/invoice-designs";
export const invoiceDesignApi = {
  async get(companyId: string) {
    return (await api.get<InvoiceDesign>(path, { params: { companyId } })).data;
  },
  async save(
    companyId: string,
    settings: InvoiceDesignSettings,
    version: number | null,
  ) {
    return (
      await api.put<InvoiceDesign>(
        path,
        { settings, version },
        { params: { companyId } },
      )
    ).data;
  },
  async preview(
    companyId: string,
    settings: InvoiceDesignSettings,
    signal: AbortSignal,
  ) {
    return (
      await api.post<Blob>(`${path}/preview`, settings, {
        params: { companyId },
        responseType: "blob",
        signal,
      })
    ).data;
  },
};
export async function designError(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return fallback;
  let body = error.response?.data;
  if (body instanceof Blob) {
    try {
      body = JSON.parse(await body.text());
    } catch {
      return fallback;
    }
  }
  return body?.fieldErrors
    ? Object.values(body.fieldErrors).join(" · ")
    : (body?.detail ?? fallback);
}
