import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./useAuth";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading)
    return (
      <main className="center-card" aria-live="polite">
        Restauration de votre session…
      </main>
    );
  if (!user)
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname, authenticationRequired: true }}
      />
    );
  return children;
}
