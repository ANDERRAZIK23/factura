import { createContext } from "react";
import type { SessionUser } from "./types";

export interface AuthValue {
  user: SessionUser | null;
  originalUser: SessionUser | null;
  loading: boolean;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  impersonate(companyId: string): Promise<void>;
  stopImpersonation(): void;
}

export const AuthContext = createContext<AuthValue | null>(null);
