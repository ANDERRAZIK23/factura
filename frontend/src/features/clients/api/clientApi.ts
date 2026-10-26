import { api } from "../../../api/client";
import type {
  Client,
  ClientInput,
  ClientListParams,
  ClientPage,
} from "../types/client";

export async function getClients(
  params: ClientListParams,
): Promise<ClientPage> {
  const { data } = await api.get<ClientPage>("/clients", { params });
  return data;
}

export async function getClientById(id: string): Promise<Client> {
  const { data } = await api.get<Client>(`/clients/${id}`);
  return data;
}

export async function createClient(input: ClientInput): Promise<Client> {
  const { data } = await api.post<Client>("/clients", input);
  return data;
}

export async function updateClient(
  id: string,
  input: ClientInput & { version: number },
): Promise<Client> {
  const { data } = await api.put<Client>(`/clients/${id}`, input);
  return data;
}

export async function updateClientStatus(
  id: string,
  active: boolean,
  version: number,
): Promise<Client> {
  const { data } = await api.patch<Client>(`/clients/${id}/status`, {
    active,
    version,
  });
  return data;
}
