import { useAuth } from "../../auth/useAuth";
import { useCurrentCompany } from "../../features/companies/hooks/useCompanies";

export function ImpersonationBanner() {
  const { originalUser, stopImpersonation } = useAuth();
  const company = useCurrentCompany();

  if (!originalUser) return null;

  return (
    <div className="impersonation-banner">
      <div className="impersonation-content">
        <span aria-hidden="true" className="impersonation-icon">⚠️</span>
        <p>
          Vous naviguez actuellement en tant qu'administrateur de <strong>{company.data?.companyName ?? "cette entreprise"}</strong>.
        </p>
      </div>
      <button 
        type="button" 
        className="impersonation-btn"
        onClick={stopImpersonation}
      >
        Quitter la session support
      </button>
    </div>
  );
}
