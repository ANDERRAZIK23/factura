import { EmptyState } from "../../../components/ui/EmptyState";
import { ListPagination } from "../../../components/ui/ListPagination";
import axios from "axios";
import { useState, useMemo } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { useUrlPagination } from "../../../utils/useUrlPagination";
import { useDebouncedSearch } from "../../../utils/useDebouncedSearch";
import { Skeleton } from "../../../components/ui/Skeleton";
import { ClientSearch } from "../components/ClientSearch";
import { ClientStatusDialog } from "../components/ClientStatusDialog";
import { ClientTable } from "../components/ClientTable";
import { useClients, useUpdateClientStatus } from "../hooks/useClients";
import { useInvoices } from "../../invoices/hooks/useInvoices";
import type { ClientSummary, ProblemDetails } from "../types/client";
import { Icon } from "../../../components/ui/Icon";
import { MetricCard } from "../../../components/ui/MetricCard";
import { PageHeader } from "../../../components/ui/PageHeader";

export function ClientListPage() {
  const { user } = useAuth();
  const canManage =
    user?.role === "APP_ADMIN" ||
    user?.role === "ADMIN" ||
    user?.role === "INVOICE_MANAGER";

  const { page, size, sort, updateParam, setPage, param, resetFilters } =
    useUrlPagination({
      sort: "clientName,asc",
    });
  const { search, setSearch, activeSearch } = useDebouncedSearch();
  const [statusClient, setStatusClient] = useState<ClientSummary | null>(null);
  const [statusError, setStatusError] = useState("");
  const activeValue = param("active");

  const query = useClients({
    page,
    size,
    sort,
    search: activeSearch,
    active: activeValue === "" ? undefined : activeValue === "true",
  });
  const hasFilters = Boolean(activeSearch || activeValue);

  const statusMutation = useUpdateClientStatus();

  // Fetch invoices to calculate KPIs and enrich the table
  const invoicesQuery = useInvoices({
    page: 0,
    size: 100,
    sort: "invoiceDate,desc",
  });

  const { retardCount, clientRevenue } = useMemo(() => {
    const clientsAvecRetard = new Set<string>();
    const revenueMap = new Map<string, number>();
    const now = new Date();

    if (invoicesQuery.data) {
      invoicesQuery.data.content.forEach((inv) => {
        // Calculate revenue (using totalIncludingTax of PAID or VALIDATED invoices)
        if (inv.status === "PAID" || inv.status === "VALIDATED") {
          const current = revenueMap.get(inv.client.id) ?? 0;
          revenueMap.set(inv.client.id, current + inv.totalIncludingTax);
        }

        // Overdue clients
        if (inv.status === "VALIDATED") {
          const diff =
            (now.getTime() - new Date(inv.invoiceDate).getTime()) / 86400000;
          if (diff > 30) clientsAvecRetard.add(inv.client.id);
        }
      });
    }
    return { retardCount: clientsAvecRetard.size, clientRevenue: revenueMap };
  }, [invoicesQuery.data]);

  const nouveauxCeMois = useMemo(() => {
    if (!query.data) return 0;
    const now = new Date();
    return query.data.content.filter((c) => {
      const d = new Date(c.createdAt);
      return (
        d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
      );
    }).length;
  }, [query.data]);

  const clientsMetricDetail = query.isPending
    ? "Chargement de l’indicateur…"
    : query.isError
      ? "Indicateur indisponible"
      : "Selon les filtres en cours";
  const invoiceMetricDetail = invoicesQuery.isPending
    ? "Chargement de l’indicateur…"
    : invoicesQuery.isError
      ? "Indicateur indisponible"
      : `Factures validées · sur les ${invoicesQuery.data?.content.length ?? 0} dernières factures`;

  async function confirmStatus() {
    if (!statusClient) return;
    setStatusError("");
    try {
      await statusMutation.mutateAsync({
        id: statusClient.id,
        active: !statusClient.active,
        version: statusClient.version,
      });
      setStatusClient(null);
    } catch (error) {
      const problem = axios.isAxiosError<ProblemDetails>(error)
        ? error.response?.data
        : undefined;
      setStatusError(
        problem?.detail ?? "Impossible de modifier le statut du client.",
      );
      setStatusClient(null);
    }
  }

  return (
    <section data-testid="clients-page" className="list-page">
      <PageHeader
        eyebrow="Gestion commerciale"
        title="Clients"
        description="Consultez et tenez à jour votre répertoire clients."
        action={
          canManage && (
            <Link
              className="button-link"
              data-testid="create-client"
              to="/clients/new"
            >
              <Icon name="plus" size={16} /> Nouveau client
            </Link>
          )
        }
      />

      <div className="metric-grid list-metrics">
        <MetricCard
          label="Clients correspondants"
          value={
            query.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : query.isError ? (
              "—"
            ) : (
              (query.data?.totalElements.toLocaleString("fr-FR") ?? "—")
            )
          }
          detail={clientsMetricDetail}
          icon="users"
          tone="accent"
        />
        <MetricCard
          label="Nouveaux ce mois"
          value={
            query.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : query.isError ? (
              "—"
            ) : (
              nouveauxCeMois.toLocaleString("fr-FR")
            )
          }
          detail={
            query.isPending || query.isError
              ? clientsMetricDetail
              : "Clients de la page courante"
          }
          icon="plus"
          tone="success"
        />
        <MetricCard
          label="Clients avec factures de +30 j"
          value={
            invoicesQuery.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : invoicesQuery.isError ? (
              "—"
            ) : (
              retardCount.toLocaleString("fr-FR")
            )
          }
          detail={invoiceMetricDetail}
          icon="status"
          tone="warning"
        />
      </div>

      {invoicesQuery.isError && (
        <div className="form-error" role="alert">
          Les indicateurs de facturation client sont indisponibles.
          <button
            type="button"
            className="link-button"
            disabled={invoicesQuery.isFetching}
            onClick={() => void invoicesQuery.refetch()}
          >
            Réessayer les indicateurs
          </button>
        </div>
      )}

      <div className="list-filters">
        <div className="list-filter-heading">
          <span>
            <Icon name="filter" size={16} /> Recherche et filtres
          </span>
          <button
            type="button"
            className="link-button"
            onClick={() => {
              setSearch("");
              resetFilters();
            }}
            disabled={!search && !hasFilters && sort === "clientName,asc"}
          >
            Réinitialiser
          </button>
        </div>
        <div className="list-filter-grid client-filter-grid">
          <ClientSearch value={search} onChange={setSearch} />
          <label>
            <span>Statut</span>
            <select
              id="client-status-filter"
              value={activeValue}
              onChange={(event) => updateParam("active", event.target.value)}
            >
              <option value="">Tous</option>
              <option value="true">Actifs</option>
              <option value="false">Inactifs</option>
            </select>
          </label>
          <label>
            <span>Trier par</span>
            <select
              id="client-sort"
              value={sort}
              onChange={(event) => updateParam("sort", event.target.value)}
            >
              <option value="clientName,asc">Nom A–Z</option>
              <option value="clientName,desc">Nom Z–A</option>
              <option value="clientCode,asc">Code croissant</option>
              <option value="clientCode,desc">Code décroissant</option>
              <option value="createdAt,desc">Plus récents</option>
              <option value="createdAt,asc">Plus anciens</option>
            </select>
          </label>
        </div>
      </div>

      {statusError && (
        <div className="form-error" role="alert">
          {statusError}
        </div>
      )}

      {query.isPending && (
        <div className="list-loading" role="status">
          <p className="muted">Chargement des clients…</p>
          <Skeleton width="100%" height="300px" radius="8px" />
        </div>
      )}

      {query.isError && (
        <div role="alert">
          <EmptyState
            icon="users"
            title="Impossible de charger les clients"
            description="Vérifiez votre connexion et réessayez."
            action={
              <button
                type="button"
                className="secondary"
                disabled={query.isFetching}
                onClick={() => void query.refetch()}
              >
                Réessayer
              </button>
            }
          />
        </div>
      )}
      {!query.isError && query.data?.content.length === 0 && (
        <EmptyState
          icon="users"
          title="Aucun client trouvé"
          description={
            page > 0
              ? "Aucun résultat sur cette page. Revenez à la première page."
              : hasFilters
                ? "Modifiez votre recherche ou réinitialisez les filtres."
                : "Créez votre premier client pour commencer."
          }
          action={
            hasFilters ? (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setSearch("");
                  resetFilters();
                }}
              >
                Effacer les filtres
              </button>
            ) : (
              canManage &&
              page === 0 && (
                <Link className="button-link" to="/clients/new">
                  <Icon name="plus" size={16} /> Ajouter un client
                </Link>
              )
            )
          }
        />
      )}

      {!query.isError && query.data && query.data.content.length > 0 && (
        <div className="list-results" aria-busy={query.isFetching}>
          <div className="result-summary">
            <strong>
              {query.data.totalElements.toLocaleString("fr-FR")} clients
            </strong>
            <span>
              {query.isFetching ? "Actualisation…" : "Répertoire clients"}
            </span>
          </div>
          <ClientTable
            clients={query.data.content}
            canManage={canManage}
            onStatus={setStatusClient}
            clientRevenue={
              invoicesQuery.data &&
              !invoicesQuery.isError &&
              invoicesQuery.data.totalElements <=
                invoicesQuery.data.content.length
                ? clientRevenue
                : undefined
            }
          />
        </div>
      )}

      {!query.isError && query.data && (
        <ListPagination
          page={query.data.page}
          size={query.data.size}
          count={query.data.content.length}
          totalElements={query.data.totalElements}
          totalPages={query.data.totalPages}
          pending={query.isFetching}
          label="clients"
          onPage={setPage}
          onSize={(value) => updateParam("size", value)}
        />
      )}

      <ClientStatusDialog
        client={statusClient}
        pending={statusMutation.isPending}
        onClose={() => setStatusClient(null)}
        onConfirm={confirmStatus}
      />
    </section>
  );
}
