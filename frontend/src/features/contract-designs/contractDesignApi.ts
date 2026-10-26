import axios from "axios";
import type { JSONContent } from "@tiptap/react";
import { api } from "../../api/client";

export interface ContractDesignSettings {
  title: string;
  paperFormat: "A4" | "LETTER";
  orientation: "PORTRAIT" | "LANDSCAPE";
  marginMm: number;
  showLogo: boolean;
  pageNumbers: boolean;
  content: JSONContent;
}

export interface ContractDesign {
  companyId: string;
  settings: ContractDesignSettings;
  version: number | null;
  updatedAt: string | null;
  customized: boolean;
}

const path = (companyId: string) => `/companies/${companyId}/contract-design`;
export const contractDesignApi = {
  async get(companyId: string) {
    return (await api.get<ContractDesign>(path(companyId))).data;
  },
  async save(
    companyId: string,
    settings: ContractDesignSettings,
    version: number | null,
  ) {
    return (
      await api.put<ContractDesign>(path(companyId), { settings, version })
    ).data;
  },
  async pdf(companyId: string, settings: ContractDesignSettings) {
    return (
      await api.post<Blob>(`${path(companyId)}/pdf`, settings, {
        responseType: "blob",
      })
    ).data;
  },
};

export async function contractDesignError(error: unknown, fallback: string) {
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
