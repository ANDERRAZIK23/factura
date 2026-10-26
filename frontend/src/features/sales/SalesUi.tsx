import type { ReactNode } from "react";
import { Icon, type IconName } from "../../components/ui/Icon";
import { problem } from "./salesHooks";
import "./sales.css";

export function QueryState({
  query,
}: {
  query: { isPending: boolean; isError: boolean; refetch: () => unknown };
}) {
  return query.isPending ? (
    <p role="status" className="sales-state sales-loading">
      <span className="sales-loading-dot" aria-hidden="true" />
      Chargement des données…
    </p>
  ) : query.isError ? (
    <div className="sales-state" role="alert">
      <strong>Les données n’ont pas pu être chargées.</strong>
      <p>Réessayez pour afficher les informations à jour.</p>
      <button
        type="button"
        className="secondary"
        onClick={() => void query.refetch()}
      >
        Réessayer
      </button>
    </div>
  ) : null;
}

export function SalesEmptyState({
  title,
  description,
  icon = "invoice",
  action,
}: {
  title: string;
  description: string;
  icon?: IconName;
  action?: ReactNode;
}) {
  return (
    <div className="sales-state sales-empty">
      <span className="sales-empty-icon" aria-hidden="true">
        <Icon name={icon} size={24} />
      </span>
      <h2>{title}</h2>
      <p>{description}</p>
      {action && <div className="sales-actions">{action}</div>}
    </div>
  );
}
export function ActionError({ error }: { error: unknown }) {
  return error ? (
    <p className="form-error" role="alert">
      {problem(error)}
    </p>
  ) : null;
}
export function SalesPagination({
  page,
  pages,
  change,
}: {
  page: number;
  pages: number;
  change: (value: number) => void;
}) {
  return (
    <nav className="sales-pagination" aria-label="Pagination">
      <button
        type="button"
        className="secondary"
        disabled={page === 0}
        onClick={() => change(page - 1)}
      >
        Précédente
      </button>
      <span aria-live="polite">
        Page {page + 1} / {Math.max(1, pages)}
      </span>
      <button
        type="button"
        className="secondary"
        disabled={page + 1 >= pages}
        onClick={() => change(page + 1)}
      >
        Suivante
      </button>
    </nav>
  );
}
