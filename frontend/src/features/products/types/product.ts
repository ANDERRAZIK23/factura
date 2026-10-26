export const PRODUCT_UNITS = [
  "PIECE",
  "KG",
  "METER",
  "ROLL",
  "LITER",
  "SERVICE",
  "OTHER",
] as const;
export type ProductUnit = (typeof PRODUCT_UNITS)[number];

export interface ProductSummary {
  id: string;
  productCode: string;
  productName: string;
  unitPriceExcludingTax: number;
  defaultVatRate: number;
  unitPriceIncludingTax: number;
  unit: ProductUnit;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface Product extends ProductSummary {
  description: string | null;
  createdBy: string;
  updatedBy: string;
}
export interface ProductPage {
  content: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
export interface ProductInput {
  productName: string;
  description?: string;
  unitPriceExcludingTax: number;
  defaultVatRate: number;
  unit: ProductUnit;
}
export interface ProductListParams {
  page: number;
  size: number;
  sort: string;
  search?: string;
  active?: boolean;
  unit?: ProductUnit;
  minPrice?: number;
  maxPrice?: number;
}
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  fieldErrors?: Record<string, string>;
  errors?: Record<string, string>;
}
