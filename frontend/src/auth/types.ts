export type UserRole = "APP_ADMIN" | "ADMIN" | "INVOICE_MANAGER" | "VIEWER";

export interface SessionUser {
  id: string;
  companyId?: string;
  fullName: string;
  email: string;
  role: UserRole;
  enabled?: boolean;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: SessionUser;
}
