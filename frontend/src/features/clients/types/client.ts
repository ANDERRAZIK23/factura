export interface ClientSummary {
  id: string;
  clientCode: string;
  clientName: string;
  phoneNumber: string | null;
  email: string | null;
  ice: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Client extends ClientSummary {
  address: string | null;
  createdBy: string;
  updatedBy: string;
}

export interface ClientPage {
  content: ClientSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ClientInput {
  clientName: string;
  phoneNumber?: string;
  address?: string;
  email?: string;
  ice?: string;
}

export interface ClientListParams {
  page: number;
  size: number;
  sort: string;
  search?: string;
  active?: boolean;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  fieldErrors?: Record<string, string>;
  errors?: Record<string, string>;
}
