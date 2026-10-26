import { useMemo, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { Icon, type IconName } from "../components/ui/Icon";
import { StatusBadge } from "../components/ui/StatusBadge";
import { Skeleton } from "../components/ui/Skeleton";
import { useClients } from "../features/clients/hooks/useClients";
import { companyApi } from "../features/companies/api/companyApi";
import { useInvoices } from "../features/invoices/hooks/useInvoices";
import { InvoiceStatusBadge } from "../features/invoices/components/InvoiceStatusBadge";
import type {
  InvoicePage,
  InvoiceSummary,
} from "../features/invoices/types/invoice";
import { formatDate, formatMoney } from "../utils/format";
import "./dashboard.css";

function greeting(name: string) {
  const hour = new Date().getHours();
  const salutation =
    hour < 12 ? "Bonjour" : hour < 18 ? "Bon après-midi" : "Bonsoir";
  return `${salutation}, ${name}`;
}

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function monthRange(offset: number) {
  const date = new Date();
  const first = new Date(date.getFullYear(), date.getMonth() + offset, 1);
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0);
  return { from: localDate(first), to: localDate(last) };
}

function buildChartData(invoices: InvoiceSummary[]) {
  const totals: Record<string, number> = {};
  for (const invoice of invoices) {
    const key = invoice.invoiceDate.slice(0, 7);
    totals[key] = (totals[key] ?? 0) + invoice.totalIncludingTax;
  }
  const today = new Date();
  return Array.from({ length: 6 }, (_, index) => {
    const date = new Date(today.getFullYear(), today.getMonth() + index - 5, 1);
    const key = localDate(date).slice(0, 7);
    return {
      key,
      label: date.toLocaleDateString("fr-MA", { month: "short" }),
      fullLabel: date.toLocaleDateString("fr-MA", {
        month: "long",
        year: "numeric",
      }),
      value: totals[key] ?? 0,
    };
  });
}

function hasCompleteResults(data: InvoicePage | undefined) {
  return Boolean(data && data.content.length >= data.totalElements);
}

function sumInvoices(invoices: InvoiceSummary[] = []) {
  return invoices.reduce(
    (total, invoice) => total + invoice.totalIncludingTax,
    0,
  );
}

function MetricCard({
  label,
  icon,
  value,
  detail,
  isPending,
  isError,
  tone = "neutral",
}: {
  label: string;
  icon: IconName;
  value: string | number;
  detail: ReactNode;
  isPending?: boolean;
  isError?: boolean;
  tone?: "neutral" | "accent" | "warning" | "success";
}) {
  return (
    <article className={`ed-metric ed-metric--${tone}`} aria-busy={isPending}>
      <div className="ed-metric-heading">
        <h3>{label}</h3>
        <span className="ed-metric-icon">
          <Icon name={icon} size={18} />
        </span>
      </div>
      {isPending ? (
        <div
          className="ed-metric-loading"
          role="status"
          aria-label="Chargement de l’indicateur"
        >
          <Skeleton width="65%" height="2rem" />
          <Skeleton width="80%" height="0.8rem" />
        </div>
      ) : (
        <>
          <strong className="ed-metric-value">{isError ? "—" : value}</strong>
          <div className="ed-metric-detail">
            {isError ? "Données indisponibles" : detail}
          </div>
        </>
      )}
    </article>
  );
}

function PanelState({
  icon = "invoice",
  title,
  children,
}: {
  icon?: IconName;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="ed-state">
      <span className="ed-state-icon">
        <Icon name={icon} size={24} />
      </span>
      <h3>{title}</h3>
      <div>{children}</div>
    </div>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  return user?.role === "APP_ADMIN" ? (
    <PlatformDashboard name={user.fullName} />
  ) : (
    <CompanyDashboard
      name={user?.fullName ?? "Utilisateur"}
      canManage={user?.role !== "VIEWER"}
      canAdmin={user?.role === "ADMIN"}
    />
  );
}

function CompanyDashboard({
  name,
  canManage,
  canAdmin,
}: {
  name: string;
  canManage: boolean;
  canAdmin: boolean;
}) {
  const clients = useClients({ page: 0, size: 1, sort: "createdAt,desc" });
  const thisMonth = monthRange(0);
  const lastMonth = monthRange(-1);
  const sixMonthsAgo = monthRange(-5);
  const paidThisMonth = useInvoices({
    page: 0,
    size: 100,
    sort: "invoiceDate,desc",
    status: "PAID",
    invoiceDateFrom: thisMonth.from,
    invoiceDateTo: thisMonth.to,
  });
  const paidLastMonth = useInvoices({
    page: 0,
    size: 100,
    sort: "invoiceDate,desc",
    status: "PAID",
    invoiceDateFrom: lastMonth.from,
    invoiceDateTo: lastMonth.to,
  });
  const pendingInvoices = useInvoices({
    page: 0,
    size: 100,
    sort: "invoiceDate,asc",
    status: "VALIDATED",
  });
  const paidSixMonths = useInvoices({
    page: 0,
    size: 100,
    sort: "invoiceDate,asc",
    status: "PAID",
    invoiceDateFrom: sixMonthsAgo.from,
    invoiceDateTo: thisMonth.to,
  });
  const recent = useInvoices({ page: 0, size: 5, sort: "updatedAt,desc" });

  const paidMonthComplete = hasCompleteResults(paidThisMonth.data);
  const previousMonthComplete = hasCompleteResults(paidLastMonth.data);
  const pendingComplete = hasCompleteResults(pendingInvoices.data);
  const chartComplete = hasCompleteResults(paidSixMonths.data);
  const caThisMonth = sumInvoices(paidThisMonth.data?.content);
  const caLastMonth = sumInvoices(paidLastMonth.data?.content);
  const trend =
    caLastMonth > 0 ? ((caThisMonth - caLastMonth) / caLastMonth) * 100 : null;
  const pendingTotal = (pendingInvoices.data?.content ?? []).reduce(
    (total, invoice) => total + invoice.outstandingAmount,
    0,
  );
  const pendingCount = pendingInvoices.data?.totalElements ?? 0;
  const olderThanThirtyDays = (invoice: InvoiceSummary) =>
    invoice.status === "VALIDATED" &&
    invoice.dueDate !== null &&
    invoice.dueDate < localDate(new Date());
  const olderInvoices = (pendingInvoices.data?.content ?? []).filter(
    olderThanThirtyDays,
  );
  const olderTotal = olderInvoices.reduce(
    (total, invoice) => total + invoice.outstandingAmount,
    0,
  );
  const chartBars = useMemo(
    () => buildChartData(paidSixMonths.data?.content ?? []),
    [paidSixMonths.data],
  );
  const chartMax = Math.max(...chartBars.map((bar) => bar.value), 1);
  const chartTotal = sumInvoices(paidSixMonths.data?.content);
  const monthLabel = new Date().toLocaleDateString("fr-MA", {
    month: "long",
    year: "numeric",
  });
  const financialQueries = [
    paidThisMonth,
    paidLastMonth,
    pendingInvoices,
    paidSixMonths,
  ];
  const hasFinancialError = financialQueries.some((query) => query.isError);
  const hasPartialFinancialData = financialQueries.some(
    (query) => query.isSuccess && !hasCompleteResults(query.data),
  );

  let trendDetail: ReactNode = "Comparaison en cours…";
  if (paidLastMonth.isError) {
    trendDetail = "Comparaison indisponible";
  } else if (!paidLastMonth.isPending) {
    trendDetail =
      !paidMonthComplete || !previousMonthComplete ? (
        "Total complet indisponible"
      ) : trend === null ? (
        "Aucun montant le mois précédent"
      ) : (
        <>
          <span className={`ed-trend ${trend < 0 ? "ed-trend--down" : ""}`}>
            <Icon name={trend < 0 ? "trend-down" : "trend-up"} size={13} />
            {trend > 0 ? "+" : ""}
            {trend.toFixed(1)} %
          </span>
          <span>vs mois précédent</span>
        </>
      );
  }

  return (
    <div className="enterprise-dashboard">
      <header className="ed-header">
        <div>
          <p className="ed-eyebrow">Vue d’ensemble</p>
          <h1>Tableau de bord</h1>
          <p className="ed-subtitle">
            {greeting(name)}. Retrouvez l’essentiel de votre activité.
          </p>
        </div>
        {canManage && (
          <div className="ed-actions">
            <Link to="/clients/new" className="ed-button ed-button--secondary">
              <Icon name="client" size={16} /> Nouveau client
            </Link>
            <Link to="/invoices/new" className="ed-button ed-button--primary">
              <Icon name="plus" size={17} /> Créer une facture
            </Link>
          </div>
        )}
      </header>

      <div className="ed-section-label">
        <h3>Votre activité en chiffres</h3>
        <span className="ed-period">
          <Icon name="calendar" size={14} />
          {monthLabel}
        </span>
      </div>
      {hasFinancialError && (
        <div className="ed-notice" role="alert">
          <Icon name="status" size={17} />
          <span>Certains indicateurs n’ont pas pu être chargés.</span>
          <button
            type="button"
            onClick={() => {
              financialQueries
                .filter((query) => query.isError)
                .forEach((query) => {
                  void query.refetch();
                });
            }}
          >
            Réessayer
          </button>
        </div>
      )}
      {hasPartialFinancialData && (
        <div className="ed-notice">
          <Icon name="status" size={17} />
          <span>
            Les totaux complets ne sont pas disponibles pour certaines périodes.
            Retrouvez le détail dans le registre des factures.
          </span>
          <Link to="/invoices">Voir le registre</Link>
        </div>
      )}
      <section className="ed-metrics" aria-label="Indicateurs de facturation">
        <MetricCard
          label="Factures réglées · ce mois"
          icon="trend-up"
          value={paidMonthComplete ? formatMoney(caThisMonth) : "—"}
          detail={trendDetail}
          isPending={paidThisMonth.isPending}
          isError={paidThisMonth.isError}
          tone="accent"
        />
        <MetricCard
          label="En attente de paiement"
          icon="status"
          value={pendingComplete ? formatMoney(pendingTotal) : "—"}
          detail={
            pendingComplete
              ? `${pendingCount} facture${pendingCount !== 1 ? "s" : ""} validée${pendingCount !== 1 ? "s" : ""}`
              : "Total complet indisponible"
          }
          isPending={pendingInvoices.isPending}
          isError={pendingInvoices.isError}
        />
        <MetricCard
          label="Échéances dépassées"
          icon="calendar"
          value={pendingComplete ? formatMoney(olderTotal) : "—"}
          detail={
            pendingComplete
              ? `${olderInvoices.length} facture${olderInvoices.length !== 1 ? "s" : ""} à suivre`
              : "Total complet indisponible"
          }
          isPending={pendingInvoices.isPending}
          isError={pendingInvoices.isError}
          tone={olderInvoices.length > 0 ? "warning" : "neutral"}
        />
        <MetricCard
          label="Répertoire clients"
          icon="client"
          value={clients.data?.totalElements ?? "—"}
          detail={
            <Link to="/clients">
              Consulter les clients <span aria-hidden="true">↗</span>
            </Link>
          }
          isPending={clients.isPending}
          isError={clients.isError}
        />
      </section>

      <div className="ed-overview-grid">
        <section
          className="ed-panel ed-chart-panel"
          aria-labelledby="revenue-heading"
        >
          <div className="ed-panel-heading">
            <div>
              <h3 id="revenue-heading">Évolution de la facturation réglée</h3>
              <p>Montants TTC par mois de facturation</p>
            </div>
            <span className="ed-tag">6 derniers mois</span>
          </div>
          {paidSixMonths.isPending ? (
            <div
              className="ed-chart-loading"
              role="status"
              aria-label="Chargement de l’historique"
            >
              <Skeleton width="35%" height="2rem" />
              <Skeleton width="100%" height="160px" radius="6px" />
            </div>
          ) : paidSixMonths.isError ? (
            <PanelState icon="trend-up" title="Historique indisponible">
              <p>Réessayez pour retrouver l’évolution de votre activité.</p>
            </PanelState>
          ) : !chartComplete ? (
            <PanelState icon="trend-up" title="Historique incomplet">
              <p>
                Consultez le registre pour retrouver l’ensemble des factures de
                cette période.
              </p>
              <Link to="/invoices" className="ed-text-link">
                Consulter les factures <span aria-hidden="true">→</span>
              </Link>
            </PanelState>
          ) : (
            <>
              <div className="ed-chart-summary">
                <strong>{formatMoney(chartTotal)}</strong>
                <span>Total sur la période</span>
              </div>
              <div
                className="ed-chart"
                role="img"
                aria-label={`Facturation réglée : ${chartBars.map((bar) => `${bar.fullLabel}, ${formatMoney(bar.value)}`).join(" ; ")}`}
              >
                <div className="ed-chart-grid" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </div>
                <div className="ed-chart-columns">
                  {chartBars.map((bar, index) => (
                    <div
                      className={`ed-chart-column ${index === chartBars.length - 1 ? "ed-chart-column--current" : ""}`}
                      key={bar.key}
                    >
                      <div
                        className="ed-chart-track"
                        title={`${bar.fullLabel} : ${formatMoney(bar.value)}`}
                      >
                        <div
                          className="ed-chart-bar"
                          style={{
                            height:
                              bar.value > 0
                                ? `${Math.max((bar.value / chartMax) * 100, 1)}%`
                                : "0",
                          }}
                        />
                      </div>
                      <span>{bar.label}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="ed-chart-caption">
                <span>
                  <i aria-hidden="true" /> Factures réglées
                </span>
                <span>
                  {chartTotal === 0
                    ? "Aucun règlement sur la période"
                    : "Devise : MAD"}
                </span>
              </div>
            </>
          )}
        </section>

        <section
          className="ed-panel ed-followup"
          aria-labelledby="followup-heading"
        >
          <div className="ed-panel-heading">
            <div>
              <h3 id="followup-heading">Suivi des paiements</h3>
              <p>Les points d’attention de votre activité</p>
            </div>
            <span className="ed-followup-icon">
              <Icon name="invoice" size={19} />
            </span>
          </div>
          {pendingInvoices.isPending ? (
            <div className="ed-followup-loading">
              <Skeleton.Table rows={2} cols={2} />
            </div>
          ) : pendingInvoices.isError ? (
            <PanelState title="Suivi indisponible">
              <p>Les informations de paiement n’ont pas pu être chargées.</p>
            </PanelState>
          ) : (
            <div className="ed-followup-body">
              <div className="ed-followup-item">
                <span className="ed-followup-dot" />
                <div>
                  <strong>À encaisser</strong>
                  <p>Factures validées en attente de paiement</p>
                </div>
                <span className="ed-followup-count">{pendingCount}</span>
              </div>
              <div className="ed-followup-item">
                <span className="ed-followup-dot ed-followup-dot--warning" />
                <div>
                  <strong>À examiner</strong>
                  <p>Factures dont l’échéance est dépassée</p>
                </div>
                <span className="ed-followup-count">
                  {pendingComplete ? olderInvoices.length : "—"}
                </span>
              </div>
              <p className="ed-followup-note">
                Les retards sont calculés à partir des échéances renseignées.
              </p>
              <Link
                to="/collections"
                className="ed-button ed-button--secondary"
              >
                Suivre les relances <span aria-hidden="true">→</span>
              </Link>
            </div>
          )}
          {canAdmin && (
            <Link to="/users" className="ed-access-link">
              <Icon name="users" size={16} />
              <span>Gérer les accès de l’équipe</span>
              <span aria-hidden="true">↗</span>
            </Link>
          )}
        </section>
      </div>

      <section
        className="ed-panel ed-recent"
        aria-labelledby="recent-invoices-heading"
      >
        <div className="ed-panel-heading">
          <div>
            <h3 id="recent-invoices-heading">Factures récentes</h3>
            <p>Vos dernières factures créées ou mises à jour</p>
          </div>
          <Link to="/invoices" className="ed-text-link">
            Toutes les factures <span aria-hidden="true">→</span>
          </Link>
        </div>
        {recent.isPending ? (
          <div className="ed-table-loading">
            <Skeleton.Table rows={4} cols={5} />
          </div>
        ) : recent.isError ? (
          <PanelState title="Factures indisponibles">
            <p>Les factures récentes n’ont pas pu être chargées.</p>
            <button
              type="button"
              className="ed-button ed-button--secondary"
              onClick={() => {
                void recent.refetch();
              }}
            >
              Réessayer
            </button>
          </PanelState>
        ) : !recent.data?.content.length ? (
          <PanelState title="Votre première facture commence ici">
            <p>
              Retrouvez ici vos factures et suivez leur statut en un coup d’œil.
            </p>
            {canManage && (
              <Link to="/invoices/new" className="ed-button ed-button--primary">
                <Icon name="plus" size={16} />
                Créer une facture
              </Link>
            )}
          </PanelState>
        ) : (
          <div
            className="ed-table-scroll"
            tabIndex={0}
            role="region"
            aria-label="Factures récentes"
          >
            <table className="ed-table">
              <thead>
                <tr>
                  <th scope="col">Facture</th>
                  <th scope="col">Client</th>
                  <th scope="col" className="ed-amount">
                    Montant TTC
                  </th>
                  <th scope="col">Statut</th>
                  <th scope="col">
                    <span className="sr-only">Consulter</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {recent.data.content.map((invoice) => (
                  <tr key={invoice.id}>
                    <td>
                      <Link
                        className="ed-record-link"
                        to={`/invoices/${invoice.id}`}
                      >
                        {invoice.invoiceNumber ?? "Brouillon"}
                      </Link>
                      <span className="ed-cell-secondary">
                        {formatDate(invoice.invoiceDate)}
                      </span>
                    </td>
                    <td>
                      <div className="ed-company-cell">
                        <span className="ed-avatar" aria-hidden="true">
                          {invoice.client.clientName
                            .trim()
                            .slice(0, 2)
                            .toUpperCase()}
                        </span>
                        <span>{invoice.client.clientName}</span>
                      </div>
                    </td>
                    <td className="ed-amount">
                      {formatMoney(invoice.totalIncludingTax)}
                    </td>
                    <td>
                      <InvoiceStatusBadge status={invoice.status} />
                      {olderThanThirtyDays(invoice) && (
                        <span className="ed-cell-secondary ed-age-warning">
                          Échéance dépassée
                        </span>
                      )}
                    </td>
                    <td>
                      <Link
                        className="ed-record-action"
                        to={`/invoices/${invoice.id}`}
                        aria-label={`Voir la facture ${invoice.invoiceNumber ?? "brouillon"} de ${invoice.client.clientName}`}
                      >
                        <Icon name="eye" size={17} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {recent.data && recent.data.content.length > 0 && (
          <div className="ed-table-footer">
            <span>
              {recent.data.content.length} dernières factures sur{" "}
              {recent.data.totalElements}
            </span>
            <span>Montants en dirhams marocains (MAD)</span>
          </div>
        )}
      </section>
    </div>
  );
}

function PlatformDashboard({ name }: { name: string }) {
  const companies = useQuery({
    queryKey: ["companies", "platform-dashboard"],
    queryFn: companyApi.list,
    retry: false,
  });
  const items = companies.data?.content ?? [];
  const active = items.filter((company) => company.enabled).length;
  const inactive = items.filter((company) => !company.enabled).length;
  const complete = Boolean(
    companies.data && items.length >= companies.data.totalElements,
  );

  return (
    <div className="enterprise-dashboard">
      <header className="ed-header">
        <div>
          <p className="ed-eyebrow">Administration de la plateforme</p>
          <h1>Tableau de bord</h1>
          <p className="ed-subtitle">
            {greeting(name)}. Suivez vos entreprises et leurs espaces de
            travail.
          </p>
        </div>
        <Link to="/companies" className="ed-button ed-button--primary">
          <Icon name="companies" size={17} />
          Gérer les entreprises
        </Link>
      </header>

      <div className="ed-platform-context">
        <span className="ed-platform-icon">
          <Icon name="building" size={22} />
        </span>
        <div>
          <strong>Votre espace d’administration</strong>
          <p>
            Gérez les entreprises et leurs accès. Les données métier restent
            propres à chaque entreprise.
          </p>
        </div>
        <span className="ed-tag">Plateforme</span>
      </div>

      <section
        className="ed-metrics ed-metrics--platform"
        aria-label="Indicateurs de la plateforme"
      >
        <MetricCard
          label="Total entreprises"
          icon="companies"
          value={companies.data?.totalElements ?? "—"}
          detail="Entreprises enregistrées"
          isPending={companies.isPending}
          isError={companies.isError}
          tone="accent"
        />
        <MetricCard
          label="Entreprises actives"
          icon="building"
          value={active}
          detail={
            complete
              ? "Espaces de travail ouverts"
              : `Parmi les ${items.length} entreprises chargées`
          }
          isPending={companies.isPending}
          isError={companies.isError}
          tone="success"
        />
        <MetricCard
          label="Entreprises inactives"
          icon="status"
          value={inactive}
          detail={
            complete
              ? "Espaces de travail désactivés"
              : `Parmi les ${items.length} entreprises chargées`
          }
          isPending={companies.isPending}
          isError={companies.isError}
          tone="warning"
        />
      </section>

      <section
        className="ed-panel ed-recent"
        aria-labelledby="recent-companies-heading"
      >
        <div className="ed-panel-heading">
          <div>
            <h3 id="recent-companies-heading">Entreprises récentes</h3>
            <p>Les derniers espaces de travail enregistrés</p>
          </div>
          <Link to="/companies" className="ed-text-link">
            Toutes les entreprises <span aria-hidden="true">→</span>
          </Link>
        </div>
        {companies.isPending ? (
          <div className="ed-table-loading">
            <Skeleton.Table rows={4} cols={5} />
          </div>
        ) : companies.isError ? (
          <PanelState icon="companies" title="Entreprises indisponibles">
            <p>Les entreprises n’ont pas pu être chargées.</p>
            <button
              className="ed-button ed-button--secondary"
              type="button"
              onClick={() => {
                void companies.refetch();
              }}
            >
              Réessayer
            </button>
          </PanelState>
        ) : items.length === 0 ? (
          <PanelState
            icon="companies"
            title="Accueillez votre première entreprise"
          >
            <p>
              Créez un espace de travail pour commencer à gérer son activité.
            </p>
            <Link to="/companies" className="ed-button ed-button--primary">
              Gérer les entreprises
            </Link>
          </PanelState>
        ) : (
          <div
            className="ed-table-scroll"
            tabIndex={0}
            role="region"
            aria-label="Entreprises récentes"
          >
            <table className="ed-table">
              <thead>
                <tr>
                  <th scope="col">Entreprise</th>
                  <th scope="col">Identifiant ICE</th>
                  <th scope="col">Créée le</th>
                  <th scope="col">Statut</th>
                  <th scope="col">
                    <span className="sr-only">Consulter</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {items.slice(0, 6).map((company) => (
                  <tr key={company.id}>
                    <td>
                      <div className="ed-company-cell">
                        <span className="ed-avatar" aria-hidden="true">
                          {company.companyName.trim().slice(0, 2).toUpperCase()}
                        </span>
                        <div>
                          <Link
                            className="ed-record-link"
                            to={`/companies/${company.id}`}
                          >
                            {company.companyName}
                          </Link>
                          <span className="ed-cell-secondary">
                            {company.companyCode}
                          </span>
                        </div>
                      </div>
                    </td>
                    <td>{company.ice || "—"}</td>
                    <td>{formatDate(company.createdAt)}</td>
                    <td>
                      <StatusBadge
                        label={company.enabled ? "Active" : "Inactive"}
                        tone={company.enabled ? "success" : "neutral"}
                      />
                    </td>
                    <td>
                      <Link
                        to={`/companies/${company.id}`}
                        className="ed-record-action"
                        aria-label={`Voir l’entreprise ${company.companyName}`}
                      >
                        <Icon name="eye" size={17} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {companies.data && items.length > 0 && (
          <div className="ed-table-footer">
            <span>
              {Math.min(items.length, 6)} dernières entreprises sur{" "}
              {companies.data.totalElements}
            </span>
            <span>Administration des espaces de travail</span>
          </div>
        )}
      </section>
    </div>
  );
}
