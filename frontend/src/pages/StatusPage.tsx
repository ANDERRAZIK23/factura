import { Link } from "react-router-dom";
import { Icon } from "../components/ui/Icon";

export function ForbiddenPage() {
  return (
    <main className="center-card status-page">
      <div className="status-page-icon status-page-forbidden" aria-hidden="true">
        <Icon name="status" size={32} />
      </div>
      <span className="status-code">403</span>
      <h1>Accès refusé</h1>
      <p className="muted">Vous n'avez pas l'autorisation d'ouvrir cette page.</p>
      <Link className="button-link" to="/dashboard">
        Retour au tableau de bord
      </Link>
    </main>
  );
}
export function NotFoundPage() {
  return (
    <main className="center-card status-page">
      <div className="status-page-icon status-page-notfound" aria-hidden="true">
        <Icon name="search" size={32} />
      </div>
      <span className="status-code">404</span>
      <h1>Page introuvable</h1>
      <p className="muted">La page que vous recherchez n'existe pas ou a été déplacée.</p>
      <Link className="button-link" to="/dashboard">
        Retour au tableau de bord
      </Link>
    </main>
  );
}
