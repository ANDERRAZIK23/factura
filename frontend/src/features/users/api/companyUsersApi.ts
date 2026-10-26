import { api } from "../../../api/client";
import { readAllPages } from "../../../api/readAllPages";
import type { CompanyUser, CompanyUserInput } from "../types";

const base = (companyId?: string) =>
  companyId ? `/companies/${companyId}/users` : "/users";

export const companyUsersApi = {
  async list(companyId?: string) {
    return readAllPages<CompanyUser>(
      async (page, size) =>
        (
          await api.get<{ content: CompanyUser[] }>(base(companyId), {
            params: { page, size, sort: "createdAt,desc" },
          })
        ).data,
    );
  },
  async create(input: CompanyUserInput, companyId?: string) {
    return (await api.post<CompanyUser>(base(companyId), input)).data;
  },
  async setStatus(user: CompanyUser, companyId?: string) {
    return (
      await api.patch<CompanyUser>(`${base(companyId)}/${user.id}/status`, {
        enabled: !user.enabled,
      })
    ).data;
  },
};
