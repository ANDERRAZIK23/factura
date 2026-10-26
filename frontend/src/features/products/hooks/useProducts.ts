import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createProduct,
  getProductById,
  getProducts,
  updateProduct,
  updateProductStatus,
} from "../api/productApi";
import type { ProductInput, ProductListParams } from "../types/product";
import { useAuth } from "../../../auth/useAuth";

export const productKeys = {
  all: ["products"] as const,
  lists: () => ["products", "list"] as const,
  list: (params: ProductListParams) => ["products", "list", params] as const,
  details: () => ["products", "detail"] as const,
  detail: (id: string) => ["products", "detail", id] as const,
};
export function useProducts(params: ProductListParams) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...productKeys.list(params), user?.companyId],
    queryFn: () => getProducts(params),
    placeholderData: (previous) => previous,
  });
}
export function useProduct(id: string) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...productKeys.detail(id), user?.companyId],
    queryFn: () => getProductById(id),
    enabled: Boolean(id),
    retry: (count, error: { response?: { status?: number } }) =>
      error.response?.status !== 404 && count < 2,
  });
}
export function useCreateProduct() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: createProduct,
    onSuccess: () => cache.invalidateQueries({ queryKey: productKeys.lists() }),
  });
}
export function useUpdateProduct(id: string) {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: (input: ProductInput & { version: number }) =>
      updateProduct(id, input),
    onSuccess: (product) => {
      cache.setQueryData([...productKeys.detail(id), user?.companyId], product);
      return cache.invalidateQueries({ queryKey: productKeys.lists() });
    },
  });
}
export function useUpdateProductStatus() {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: ({
      id,
      active,
      version,
    }: {
      id: string;
      active: boolean;
      version: number;
    }) => updateProductStatus(id, active, version),
    onSuccess: (product) => {
      cache.setQueryData(
        [...productKeys.detail(product.id), user?.companyId],
        product,
      );
      return cache.invalidateQueries({ queryKey: productKeys.lists() });
    },
  });
}
