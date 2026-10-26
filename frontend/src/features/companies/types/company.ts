export interface Company {
  id: string;
  companyCode: string;
  companyName: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  ice: string | null;
  enabled: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyPage {
  content: Company[];
  totalElements: number;
}

export interface CompanyInput {
  companyName: string;
  address?: string;
  phone?: string;
  email?: string;
  ice?: string;
}

export interface CompanyCreateInput extends CompanyInput {
  adminFullName: string;
  adminEmail: string;
  adminPassword: string;
}

export interface CompanyBranding {
  companyName: string;
  logoDataUrl: string | null;
  updatedAt: string | null;
}
