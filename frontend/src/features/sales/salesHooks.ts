import axios from "axios";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../auth/useAuth";

export const today = () => new Date().toLocaleDateString("en-CA");
export function problem(error: unknown) {
  return axios.isAxiosError<{ detail?: string }>(error)
    ? (error.response?.data?.detail ??
        "L’opération a échoué. Veuillez réessayer.")
    : "L’opération a échoué. Veuillez réessayer.";
}
export function useCompanyQuery<T>(
  key: readonly unknown[],
  load: () => Promise<T>,
  enabled = true,
) {
  const { user } = useAuth();
  return useQuery({
    queryKey: ["sales", user?.companyId, ...key],
    queryFn: load,
    enabled,
    retry: false,
  });
}
export function useSalesAction<T, R>(action: (input: T) => Promise<R>) {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: ["sales"] }),
        cache.invalidateQueries({ queryKey: ["invoices", "detail"] }),
        cache.invalidateQueries({ queryKey: ["invoices", "list"] }),
      ]);
    },
  });
}
