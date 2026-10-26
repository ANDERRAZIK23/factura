import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { authApi } from "./authApi";
import type { SessionUser } from "./types";
import { configureSession, setAccessToken } from "../api/client";
import { AuthContext, type AuthValue } from "./authStore";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [originalUser, setOriginalUser] = useState<SessionUser | null>(null);
  const [originalToken, setOriginalToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const restorationStarted = useRef(false);

  const acceptSession = useCallback(
    (session: Awaited<ReturnType<typeof authApi.refresh>>) => {
      setAccessToken(session.accessToken);
      setUser(session.user);
      return session;
    },
    [],
  );

  const refresh = useCallback(
    async () => {
      const session = await authApi.refresh();
      // If we were impersonating, we would lose it on page reload unless we persist it.
      // We will assume the backend's refresh token just returns the original admin token.
      // So if refresh is called, it clears impersonation.
      setOriginalUser(null);
      setOriginalToken(null);
      return acceptSession(session);
    },
    [acceptSession],
  );
  
  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setOriginalUser(null);
    setOriginalToken(null);
  }, []);

  useEffect(() => {
    configureSession(refresh, clearSession);
    if (restorationStarted.current) return;
    restorationStarted.current = true;
    refresh()
      .catch(clearSession)
      .finally(() => setLoading(false));
  }, [clearSession, refresh]);

  const value = useMemo<AuthValue>(
    () => ({
      user,
      originalUser,
      loading,
      login: async (email, password) => {
        acceptSession(await authApi.login(email, password));
      },
      logout: async () => {
        try {
          await authApi.logout();
        } finally {
          clearSession();
        }
      },
      impersonate: async (companyId: string) => {
        const session = await authApi.impersonate(companyId);
        // Save current user/token as original
        if (!originalUser) {
            import("../api/client").then(m => setOriginalToken(m.getAccessToken()));
            setOriginalUser(user);
        }
        acceptSession(session);
      },
      stopImpersonation: () => {
        if (originalUser && originalToken) {
          setAccessToken(originalToken);
          setUser(originalUser);
          setOriginalUser(null);
          setOriginalToken(null);
        }
      },
    }),
    [acceptSession, clearSession, loading, user, originalUser, originalToken],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
