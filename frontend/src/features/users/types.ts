import type { SessionUser, UserRole } from "../../auth/types";

export type CompanyUser = SessionUser & {
  enabled: boolean;
  version: number;
  createdAt?: string;
  updatedAt?: string;
};
export type CompanyUserInput = {
  fullName: string;
  email: string;
  password: string;
  role: Exclude<UserRole, "APP_ADMIN">;
};
