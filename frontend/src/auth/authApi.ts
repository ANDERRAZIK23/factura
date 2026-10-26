import { api } from "../api/client";
import type { TokenResponse } from "./types";

export const authApi = {
  async login(email: string, password: string) {
    return (await api.post<TokenResponse>("/auth/login", { email, password }))
      .data;
  },
  async refresh() {
    const refresh = async () =>
      (await api.post<TokenResponse>("/auth/refresh")).data;
    return typeof navigator !== "undefined" && navigator.locks
      ? navigator.locks.request("invoice-session-refresh", refresh)
      : refresh();
  },
  async logout() {
    await api.post("/auth/logout");
  },
  async impersonate(companyId: string) {
    return (await api.post<TokenResponse>(`/auth/impersonate`, { companyId })).data;
  },
};
