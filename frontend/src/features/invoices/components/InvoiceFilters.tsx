import { Icon } from "../../../components/ui/Icon";
import type { ClientSummary } from "../../clients/types/client";
import { INVOICE_STATUSES } from "../types/invoice";

export function InvoiceFilters({
  search,
  values,
  clients,
  onSearch,
  onChange,
  onReset,
  hasFilters,
}: {
  onReset: () => void;
  hasFilters: boolean;
  search: string;
  values: Record<string, string>;
  clients: ClientSummary[];
  onSearch: (value: string) => void;
  onChange: (name: string, value: string) => void;
}) {
  return (
    <div className="list-filters invoice-filters">
      <div className="list-filter-heading">
        <span>
          <Icon name="filter" size={16} /> Recherche et filtres
        </span>
        <button
          type="button"
          className="link-button"
          onClick={onReset}
          disabled={!hasFilters}
        >
          Réinitialiser
        </button>
      </div>
      <div
        className="invoice-status-shortcuts"
        role="group"
        aria-label="Filtre rapide par statut de facture"
      >
        <button
          type="button"
          className={!values.status ? "active" : ""}
          aria-pressed={!values.status}
          onClick={() => onChange("status", "")}
        >
          Toutes
        </button>
        {INVOICE_STATUSES.map((status) => (
          <button
            type="button"
            key={status}
            className={values.status === status ? "active" : ""}
            aria-pressed={values.status === status}
            onClick={() => onChange("status", status)}
          >
            {status === "DRAFT"
              ? "Brouillons"
              : status === "VALIDATED"
                ? "À payer"
                : status === "PAID"
                  ? "Payées"
                  : "Annulées"}
          </button>
        ))}
      </div>
      <div className="list-filter-grid invoice-filter-primary">
        <div className="search-field list-search">
          <label htmlFor="invoiceSearch">Rechercher des factures</label>
          <div className="list-search-input">
            <Icon name="search" size={16} />
            <input
              id="invoiceSearch"
              type="search"
              value={search}
              placeholder="Numéro, client ou référence de commande"
              onChange={(event) => onSearch(event.target.value)}
            />
          </div>
        </div>
        <Filter label="Client">
          <select
            aria-label="Client de la facture"
            value={values.clientId}
            onChange={(event) => onChange("clientId", event.target.value)}
          >
            <option value="">Tous les clients</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.clientCode} — {client.clientName}
              </option>
            ))}
          </select>
        </Filter>
        <Filter label="Statut">
          <select
            aria-label="Statut de la facture"
            value={values.status}
            onChange={(event) => onChange("status", event.target.value)}
          >
            <option value="">Tous les statuts</option>
            {INVOICE_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status === "DRAFT"
                  ? "Brouillon"
                  : status === "VALIDATED"
                    ? "Validée"
                    : status === "PAID"
                      ? "Payée"
                      : "Annulée"}
              </option>
            ))}
          </select>
        </Filter>
      </div>
      <details className="advanced-filters">
        <summary>
          Plus de filtres <span>Période et tri</span>
        </summary>
        <div className="invoice-advanced-filter-grid">
          <Filter label="Du">
            <input
              aria-label="Date de facture à partir du"
              type="date"
              value={values.invoiceDateFrom}
              onChange={(event) =>
                onChange("invoiceDateFrom", event.target.value)
              }
            />
          </Filter>
          <Filter label="Au">
            <input
              aria-label="Date de facture jusqu’au"
              type="date"
              value={values.invoiceDateTo}
              onChange={(event) =>
                onChange("invoiceDateTo", event.target.value)
              }
            />
          </Filter>
          <Filter label="Tri">
            <select
              aria-label="Tri des factures"
              value={values.sort}
              onChange={(event) => onChange("sort", event.target.value)}
            >
              <option value="createdAt,desc">Création la plus récente</option>
              <option value="invoiceDate,desc">
                Date de facture la plus récente
              </option>
              <option value="invoiceDate,asc">
                Date de facture la plus ancienne
              </option>
              <option value="invoiceNumber,asc">Numéro de facture</option>
              <option value="totalIncludingTax,desc">
                Montant le plus élevé
              </option>
            </select>
          </Filter>
        </div>
      </details>
    </div>
  );
}

function Filter({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label>
      <span>{label}</span>
      {children}
    </label>
  );
}
