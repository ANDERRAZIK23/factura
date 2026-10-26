import { ListPagination } from "../../../components/ui/ListPagination";
import { Link } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { EmptyState } from "../../../components/ui/EmptyState";
import { Icon } from "../../../components/ui/Icon";
import { MetricCard } from "../../../components/ui/MetricCard";
import { PageHeader } from "../../../components/ui/PageHeader";
import { Skeleton } from "../../../components/ui/Skeleton";
import { formatMoney } from "../../../utils/format";
import { useDebouncedSearch } from "../../../utils/useDebouncedSearch";
import { useUrlPagination } from "../../../utils/useUrlPagination";
import { useClients } from "../../clients/hooks/useClients";
import { InvoiceFilters } from "../components/InvoiceFilters";
import { InvoiceTable } from "../components/InvoiceTable";
import { useInvoices } from "../hooks/useInvoices";
import { INVOICE_STATUSES, type InvoiceStatus } from "../types/invoice";

export function InvoiceListPage() {
  const { user } = useAuth();
  const canManage =
    user?.role === "APP_ADMIN" ||
    user?.role === "ADMIN" ||
    user?.role === "INVOICE_MANAGER";
  const { page, size, sort, updateParam, setPage, param, resetFilters } =
    useUrlPagination({
      sort: "createdAt,desc",
    });
  const { search, setSearch, activeSearch } = useDebouncedSearch();
  const values = {
    clientId: param("clientId"),
    status: param("status"),
    invoiceDateFrom: param("invoiceDateFrom"),
    invoiceDateTo: param("invoiceDateTo"),
    sort,
  };
  const hasFilters = Boolean(
    activeSearch ||
    values.clientId ||
    values.status ||
    values.invoiceDateFrom ||
    values.invoiceDateTo,
  );

  const query = useInvoices({
    page,
    size,
    sort: values.sort,
    search: activeSearch,
    clientId: values.clientId || undefined,
    status: INVOICE_STATUSES.includes(values.status as InvoiceStatus)
      ? (values.status as InvoiceStatus)
      : undefined,
    invoiceDateFrom: values.invoiceDateFrom || undefined,
    invoiceDateTo: values.invoiceDateTo || undefined,
  });
  const clients = useClients({ page: 0, size: 100, sort: "clientName,asc" });
  const draftQuery = useInvoices({
    page: 0,
    size: 1,
    sort: "createdAt,desc",
    status: "DRAFT",
  });
  const validatedQuery = useInvoices({
    page: 0,
    size: 100,
    sort: "createdAt,desc",
    status: "VALIDATED",
  });

  const validatedInvoices = validatedQuery.data?.content;
  const validatedAmount = validatedInvoices?.reduce(
    (total, invoice) => total + invoice.outstandingAmount,
    0,
  );
  const agedValidatedCount = validatedInvoices?.filter(
    (invoice) =>
      invoice.dueDate !== null &&
      invoice.dueDate < new Date().toLocaleDateString("en-CA"),
  ).length;
  const partialMetrics = Boolean(
    validatedQuery.data &&
    validatedQuery.data.totalElements > validatedQuery.data.content.length,
  );
  const validatedScope = partialMetrics
    ? `Total partiel · ${validatedInvoices?.length} dernières factures validées`
    : "Toutes les factures validées";
  const validatedMetricDetail = validatedQuery.isPending
    ? "Chargement de l’indicateur…"
    : validatedQuery.isError
      ? "Indicateur indisponible"
      : validatedScope;

  return (
    <section data-testid="invoices-page" className="list-page">
      <PageHeader
        eyebrow="Gestion commerciale"
        title="Factures"
        description="Préparez, validez et suivez les règlements de vos factures clients."
        action={
          canManage && (
            <Link
              className="button-link"
              data-testid="create-invoice"
              to="/invoices/new"
            >
              <Icon name="plus" size={16} />
              Créer une facture
            </Link>
          )
        }
      />

      <div className="metric-grid list-metrics">
        <MetricCard
          label="Montant validé TTC"
          value={
            validatedQuery.isPending ? (
              <Skeleton width="9rem" height="1.8rem" />
            ) : validatedQuery.isError || validatedAmount === undefined ? (
              "—"
            ) : (
              formatMoney(validatedAmount)
            )
          }
          detail={validatedMetricDetail}
          tone="accent"
          icon="invoice"
        />
        <MetricCard
          label="Brouillons à valider"
          value={
            draftQuery.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : draftQuery.isError ? (
              "—"
            ) : (
              (draftQuery.data?.totalElements.toLocaleString("fr-FR") ?? "—")
            )
          }
          detail={
            draftQuery.isPending
              ? "Chargement de l’indicateur…"
              : draftQuery.isError
                ? "Indicateur indisponible"
                : "Factures en cours de préparation"
          }
          icon="edit"
        />
        <MetricCard
          label="Échéances dépassées"
          value={
            validatedQuery.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : validatedQuery.isError ? (
              "—"
            ) : (
              (agedValidatedCount?.toLocaleString("fr-FR") ?? "—")
            )
          }
          detail={
            validatedQuery.isPending || validatedQuery.isError
              ? validatedMetricDetail
              : partialMetrics
                ? `Échéances renseignées · sur les ${validatedInvoices?.length} dernières validées`
                : "Factures validées avec une échéance dépassée"
          }
          tone="warning"
          icon="status"
        />
      </div>

      {(draftQuery.isError || validatedQuery.isError) && (
        <div className="form-error" role="alert">
          Certains indicateurs n’ont pas pu être chargés.
          <button
            type="button"
            className="link-button"
            disabled={draftQuery.isFetching || validatedQuery.isFetching}
            onClick={() => {
              if (draftQuery.isError) void draftQuery.refetch();
              if (validatedQuery.isError) void validatedQuery.refetch();
            }}
          >
            Recharger les indicateurs
          </button>
        </div>
      )}

      <InvoiceFilters
        search={search}
        values={values}
        clients={clients.data?.content ?? []}
        onReset={() => {
          setSearch("");
          resetFilters();
        }}
        hasFilters={Boolean(search) || hasFilters || sort !== "createdAt,desc"}
        onSearch={setSearch}
        onChange={updateParam}
      />

      {clients.isError && (
        <div className="form-error" role="alert">
          La liste des clients du filtre est indisponible.
          <button
            type="button"
            className="link-button"
            disabled={clients.isFetching}
            onClick={() => void clients.refetch()}
          >
            Recharger les clients
          </button>
        </div>
      )}

      {query.isPending && (
        <div role="status" className="list-loading">
          <p className="muted">Chargement des factures…</p>
          <Skeleton.Table rows={5} cols={6} />
        </div>
      )}

      {query.isError && (
        <EmptyState
          icon="invoice"
          title="Impossible de charger les factures"
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
      )}

      {!query.isError && query.data?.content.length === 0 && (
        <EmptyState
          icon="invoice"
          title="Aucune facture trouvée"
          description={
            page > 0
              ? "Aucune facture sur cette page. Revenez à la première page."
              : hasFilters
                ? "Modifiez votre recherche ou vos filtres pour retrouver une facture."
                : "Préparez votre première facture pour démarrer la facturation."
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
                <Link className="button-link" to="/invoices/new">
                  <Icon name="plus" size={16} />
                  Créer la première facture
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
              {query.data.totalElements.toLocaleString("fr-FR")} facture
              {query.data.totalElements > 1 ? "s" : ""}
              {hasFilters ? " trouvée" : " au total"}
              {hasFilters && query.data.totalElements > 1 ? "s" : ""}
            </strong>
            <span>
              {query.isFetching ? "Actualisation…" : "Montants exprimés en MAD"}
            </span>
          </div>
          <InvoiceTable invoices={query.data.content} canManage={canManage} />
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
          label="factures"
          onPage={setPage}
          onSize={(value) => updateParam("size", value)}
        />
      )}
    </section>
  );
}
