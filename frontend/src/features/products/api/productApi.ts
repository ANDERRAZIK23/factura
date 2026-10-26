import { api } from "../../../api/client";
import type {
  Product,
  ProductInput,
  ProductListParams,
  ProductPage,
} from "../types/product";

export async function getProducts(
  params: ProductListParams,
): Promise<ProductPage> {
  return (await api.get<ProductPage>("/products", { params })).data;
}
export async function getProductById(id: string): Promise<Product> {
  return (await api.get<Product>(`/products/${id}`)).data;
}
export async function createProduct(input: ProductInput): Promise<Product> {
  return (await api.post<Product>("/products", input)).data;
}
export async function updateProduct(
  id: string,
  input: ProductInput & { version: number },
): Promise<Product> {
  return (await api.put<Product>(`/products/${id}`, input)).data;
}
export async function updateProductStatus(
  id: string,
  active: boolean,
  version: number,
): Promise<Product> {
  return (
    await api.patch<Product>(`/products/${id}/status`, { active, version })
  ).data;
}
