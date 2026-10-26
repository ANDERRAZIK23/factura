import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../../auth/useAuth";
import { companyApi } from "../api/companyApi";

export const companyKeys = {
  current: (companyId?: string) => ["companies", "current", companyId] as const,
};

export function useCurrentCompany() {
  const { user } = useAuth();
  return useQuery({
    queryKey: companyKeys.current(user?.companyId),
    queryFn: companyApi.current,
    enabled: Boolean(user?.companyId),
    retry: false,
  });
}

export function useCompanyBranding(companyId?: string, platform = false) {
  const { user } = useAuth();
  return useQuery({
    queryKey: ["companies", "branding", companyId ?? user?.companyId],
    queryFn: () => companyApi.branding(platform ? companyId : undefined),
    enabled: platform ? Boolean(companyId) : Boolean(user?.companyId),
    retry: false,
  });
}
