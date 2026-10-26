import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createClient,
  getClientById,
  getClients,
  updateClient,
  updateClientStatus,
} from "../api/clientApi";
import type { ClientInput, ClientListParams } from "../types/client";
import { useAuth } from "../../../auth/useAuth";

export const clientKeys = {
  all: ["clients"] as const,
  lists: () => [...clientKeys.all, "list"] as const,
  list: (params: ClientListParams) => [...clientKeys.lists(), params] as const,
  details: () => [...clientKeys.all, "detail"] as const,
  detail: (id: string) => [...clientKeys.details(), id] as const,
};

export function useClients(params: ClientListParams) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...clientKeys.list(params), user?.companyId],
    queryFn: () => getClients(params),
    placeholderData: (previous) => previous,
  });
}

export function useClient(id: string) {
  const { user } = useAuth();
  return useQuery({
    queryKey: [...clientKeys.detail(id), user?.companyId],
    queryFn: () => getClientById(id),
    enabled: Boolean(id),
    retry: (count, error: { response?: { status?: number } }) =>
      error.response?.status !== 404 && count < 2,
  });
}

export function useCreateClient() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: createClient,
    onSuccess: () => cache.invalidateQueries({ queryKey: clientKeys.lists() }),
  });
}

export function useUpdateClient(id: string) {
  const cache = useQueryClient();
  const { user } = useAuth();
  return useMutation({
    mutationFn: (input: ClientInput & { version: number }) =>
      updateClient(id, input),
    onSuccess: (client) => {
      cache.setQueryData([...clientKeys.detail(id), user?.companyId], client);
      return cache.invalidateQueries({ queryKey: clientKeys.lists() });
    },
  });
}

export function useUpdateClientStatus() {
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
    }) => updateClientStatus(id, active, version),
    onSuccess: (client) => {
      cache.setQueryData(
        [...clientKeys.detail(client.id), user?.companyId],
        client,
      );
      return cache.invalidateQueries({ queryKey: clientKeys.lists() });
    },
  });
}
