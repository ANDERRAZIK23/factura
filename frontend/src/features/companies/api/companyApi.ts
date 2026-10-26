import { api } from "../../../api/client";
import { readAllPages } from "../../../api/readAllPages";
import type {
  Company,
  CompanyBranding,
  CompanyCreateInput,
  CompanyInput,
  CompanyPage,
} from "../types/company";

export const companyApi = {
  async list() {
    const content = await readAllPages<Company>(
      async (page, size) =>
        (
          await api.get<CompanyPage>("/companies", {
            params: { page, size, sort: "createdAt,desc" },
          })
        ).data,
    );
    return { content, totalElements: content.length };
  },
  async get(id: string) {
    return (await api.get<Company>(`/companies/${id}`)).data;
  },
  async create(input: CompanyCreateInput) {
    return (await api.post<Company>("/companies", input)).data;
  },
  async update(id: string, input: CompanyInput & { version: number }) {
    return (await api.put<Company>(`/companies/${id}`, input)).data;
  },
  async setStatus(company: Company) {
    return (
      await api.patch<Company>(`/companies/${company.id}/status`, {
        enabled: !company.enabled,
        version: company.version,
      })
    ).data;
  },
  async current() {
    return (await api.get<Company>("/company")).data;
  },
  async updateCurrent(input: CompanyInput & { version: number }) {
    return (await api.put<Company>("/company", input)).data;
  },
  async branding(companyId?: string) {
    return (
      await api.get<CompanyBranding>(
        companyId ? `/companies/${companyId}/branding` : "/company/branding",
      )
    ).data;
  },
  async uploadLogo(file: File, companyId?: string) {
    const form = new FormData();
    form.append("logo", file);
    return (
      await api.put<CompanyBranding>(
        companyId ? `/companies/${companyId}/branding` : "/company/branding",
        form,
      )
    ).data;
  },
  async removeLogo(companyId?: string) {
    await api.delete(
      companyId ? `/companies/${companyId}/branding` : "/company/branding",
    );
  },
};
