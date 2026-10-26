import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { TokenResponse } from "../auth/types";

export const API_URL =
  import.meta.env.VITE_API_URL ?? "http://127.0.0.1:8080/api/v1";
let accessToken: string | null = null;
let refreshSession: (() => Promise<TokenResponse>) | null = null;
let refreshFailure: (() => void) | null = null;
let refreshPromise: Promise<TokenResponse> | null = null;

export const api = axios.create({ baseURL: API_URL, withCredentials: true });

export function configureSession(
  refresh: () => Promise<TokenResponse>,
  onFailure: () => void,
) {
  refreshSession = refresh;
  refreshFailure = onFailure;
}

export function setAccessToken(token: string | null) {
  accessToken = token;
}
export function getAccessToken() {
  return accessToken;
}

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const request = error.config as
    (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  const authRequest = request?.url?.startsWith("/auth/") ?? false;
  if (
    error.response?.status !== 401 ||
    !request ||
    request._retried ||
    authRequest ||
    !refreshSession
  ) {
    return Promise.reject(error);
  }
  request._retried = true;
  try {
    refreshPromise ??= refreshSession().finally(() => {
      refreshPromise = null;
    });
    const session = await refreshPromise;
    request.headers.Authorization = `Bearer ${session.accessToken}`;
    return api(request);
  } catch (refreshError) {
    setAccessToken(null);
    refreshFailure?.();
    return Promise.reject(refreshError);
  }
});
