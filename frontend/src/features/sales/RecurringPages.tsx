import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { PageHeader } from "../../components/ui/PageHeader";
import { Icon } from "../../components/ui/Icon";
import { useAuth } from "../../auth/useAuth";
import { useInvoice } from "../invoices/hooks/useInvoices";
import { formatDate, formatMoney } from "../../utils/format";
import { salesApi } from "./salesApi";
import {
  ActionError,
  QueryState,
  SalesEmptyState,
  SalesPagination,
} from "./SalesUi";
import { today, useCompanyQuery, useSalesAction } from "./salesHooks";

function frequency(months: number) {
  if (months === 1) return "Chaque mois";
  if (months === 12) return "Chaque année";
  return `Tous les ${months} mois`;
}

export function RecurringListPage() {
  const [page, setPage] = useState(0);
  const { user } = useAuth();
  const navigate = useNavigate();
  const query = useCompanyQuery(["recurrences", page], () =>
    salesApi.recurrences(page),
  );
  const toggle = useSalesAction(
    (input: { id: string; version: number; enabled: boolean }) =>
      salesApi.toggleRecurrence(input.id, input.version, input.enabled),
  );
  const generate = useSalesAction((input: { id: string; version: number }) =>
    salesApi.generateRecurrence(input.id, input.version),
  );
  const busy = toggle.isPending || generate.isPending;
  return (
    <section className="sales-page">
      <PageHeader
        eyebrow="Ventes"
        title="Factures récurrentes"
        description="Préparation automatique de brouillons à partir des conditions enregistrées. La validation reste manuelle."
        action={
          user?.role !== "VIEWER" ? (
            <Link className="button-link" to="/invoices">
              <Icon name="plus" size={17} />
              Créer depuis une facture
            </Link>
          ) : undefined
        }
      />
      <div className="sales-notice">
        <Icon name="calendar" size={20} />
        <p>
          <strong>Des brouillons prêts à vérifier.</strong> Chaque occurrence
          reprend vos articles, prix et remises. Vous gardez la main sur la
          validation de la facture.
        </p>
      </div>
      <div className="sales-card sales-list-card">
        <header className="sales-section-heading">
          <div>
            <h2>Programmes de facturation</h2>
            <p>Consultez la prochaine date et gérez les programmes actifs.</p>
          </div>
          {query.data && (
            <span className="sales-count">
              {query.data.page.totalElements}{" "}
              {query.data.page.totalElements === 1 ? "programme" : "programmes"}
            </span>
          )}
        </header>
        <QueryState query={query} />
        <ActionError error={toggle.error ?? generate.error} />
        {query.data &&
          (query.data.content.length ? (
            <>
              <div className="sales-table sales-responsive-table">
                <table aria-label="Programmes de facturation récurrente">
                  <thead>
                    <tr>
                      <th>Récurrence</th>
                      <th>Client</th>
                      <th>Prochaine occurrence</th>
                      <th className="numeric">TTC</th>
                      <th>Statut</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {query.data.content.map((r) => (
                      <tr key={r.id}>
                        <td data-label="Récurrence" className="sales-cell-wide">
                          <strong>{r.title}</strong>
                          <small>
                            {frequency(r.intervalMonths)} · Règlement à{" "}
                            {r.paymentTermsDays}{" "}
                            {r.paymentTermsDays === 1 ? "jour" : "jours"}
                          </small>
                          {r.endDate && (
                            <small>Jusqu’au {formatDate(r.endDate)}</small>
                          )}
                          {r.lastError && (
                            <p className="sales-warning" role="alert">
                              {r.lastError}
                            </p>
                          )}
                        </td>
                        <td data-label="Client" className="sales-cell-wide">
                          {r.terms.client.name}
                        </td>
                        <td data-label="Prochaine occurrence">
                          {r.endDate && r.nextDate > r.endDate
                            ? "—"
                            : formatDate(r.nextDate)}
                          {r.enabled && r.nextDate <= today() && (
                            <small className="sales-warning">À préparer</small>
                          )}
                          {r.lastInvoiceId && (
                            <small>
                              <Link to={`/invoices/${r.lastInvoiceId}`}>
                                Dernier brouillon créé
                              </Link>
                            </small>
                          )}
                        </td>
                        <td className="numeric" data-label="Total TTC">
                          {formatMoney(r.terms.totals.totalIncludingTax)}
                        </td>
                        <td data-label="Statut">
                          <span
                            className={`sales-badge ${r.enabled ? "sales-badge-converted" : "sales-badge-cancelled"}`}
                          >
                            {r.enabled
                              ? "Active"
                              : r.endDate && r.nextDate > r.endDate
                                ? "Terminée"
                                : "En pause"}
                          </span>
                        </td>
                        <td
                          data-label="Actions"
                          className="sales-cell-wide sales-cell-actions"
                        >
                          {user?.role !== "VIEWER" && (
                            <div className="sales-actions sales-row-actions">
                              <button
                                className="secondary"
                                disabled={
                                  busy ||
                                  Boolean(
                                    !r.enabled &&
                                    r.endDate &&
                                    r.nextDate > r.endDate,
                                  )
                                }
                                onClick={() =>
                                  toggle.mutate({
                                    id: r.id,
                                    version: r.version,
                                    enabled: !r.enabled,
                                  })
                                }
                              >
                                {r.enabled ? "Mettre en pause" : "Reprendre"}
                              </button>
                              {r.enabled && r.nextDate <= today() && (
                                <button
                                  disabled={busy}
                                  onClick={() =>
                                    generate.mutate(
                                      { id: r.id, version: r.version },
                                      {
                                        onSuccess: (i) =>
                                          navigate(`/invoices/${i.id}`),
                                      },
                                    )
                                  }
                                >
                                  Préparer l’occurrence
                                </button>
                              )}
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <SalesPagination
                page={page}
                pages={query.data.page.totalPages}
                change={setPage}
              />
            </>
          ) : (
            <SalesEmptyState
              title="Planifiez votre facturation régulière"
              description="Ouvrez une facture existante, puis choisissez « Créer une récurrence » pour définir la fréquence et l’échéance de règlement."
              icon="calendar"
              action={
                user?.role !== "VIEWER" ? (
                  <Link className="button-link" to="/invoices">
                    Choisir une facture
                  </Link>
                ) : undefined
              }
            />
          ))}
      </div>
    </section>
  );
}

export function RecurringCreatePage() {
  const [params] = useSearchParams();
  const id = params.get("invoiceId") ?? "";
  const invoice = useInvoice(id);
  const navigate = useNavigate();
  const [title, setTitle] = useState("Facturation mensuelle");
  const [start, setStart] = useState(today());
  const [end, setEnd] = useState("");
  const [months, setMonths] = useState(1);
  const [days, setDays] = useState(30);
  const action = useSalesAction(salesApi.createRecurrence);
  return (
    <section className="sales-page">
      <PageHeader
        eyebrow="Ventes"
        title="Créer une récurrence"
        description="Les articles et les prix de la facture choisie seront repris pour chaque occurrence."
      />
      {!id ? (
        <div className="sales-card">
          <SalesEmptyState
            title="Choisissez une facture comme modèle"
            description="Ses articles, prix et remises seront repris à chaque occurrence."
            icon="calendar"
            action={
              <Link className="button-link" to="/invoices">
                Choisir une facture
              </Link>
            }
          />
        </div>
      ) : (
        <>
          <QueryState query={invoice} />
          {invoice.data && (
            <form
              className="sales-card sales-form"
              onSubmit={(event) => {
                event.preventDefault();
                action.mutate(
                  {
                    invoiceId: id,
                    title,
                    startDate: start,
                    endDate: end || null,
                    intervalMonths: months,
                    paymentTermsDays: days,
                  },
                  { onSuccess: () => navigate("/recurring-invoices") },
                );
              }}
            >
              <header className="sales-source-document">
                <span className="sales-source-icon">
                  <Icon name="invoice" size={22} />
                </span>
                <div>
                  <span className="sales-kicker">Facture modèle</span>
                  <h2>{invoice.data.client.clientName}</h2>
                  <p>
                    {invoice.data.invoiceNumber ?? "Brouillon de facture"} ·{" "}
                    {invoice.data.lines.length}{" "}
                    {invoice.data.lines.length === 1 ? "article" : "articles"}
                  </p>
                </div>
                <strong className="sales-source-total">
                  {formatMoney(invoice.data.totalIncludingTax)}
                  <small>TTC par occurrence</small>
                </strong>
              </header>
              <fieldset disabled={action.isPending} className="sales-fieldset">
                <div className="sales-fields">
                  <label>
                    Nom de la récurrence
                    <input
                      required
                      maxLength={200}
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                    />
                  </label>
                  <label>
                    Première occurrence
                    <input
                      type="date"
                      required
                      value={start}
                      onChange={(e) => setStart(e.target.value)}
                    />
                  </label>
                  <label>
                    Fréquence
                    <select
                      value={months}
                      onChange={(e) => setMonths(Number(e.target.value))}
                    >
                      <option value={1}>Chaque mois</option>
                      <option value={3}>Tous les 3 mois</option>
                      <option value={6}>Tous les 6 mois</option>
                      <option value={12}>Chaque année</option>
                    </select>
                  </label>
                  <label>
                    Délai de règlement en jours
                    <input
                      required
                      type="number"
                      min={0}
                      max={365}
                      value={days}
                      onChange={(e) => setDays(Number(e.target.value))}
                    />
                  </label>
                  <label>
                    Fin de la récurrence (facultatif)
                    <input
                      type="date"
                      min={start}
                      value={end}
                      onChange={(e) => setEnd(e.target.value)}
                    />
                  </label>
                </div>
              </fieldset>
              <div className="sales-schedule-summary">
                <Icon name="calendar" size={19} />
                <p>
                  <strong>
                    {frequency(months)} à partir du{" "}
                    {start ? formatDate(start) : "jour choisi"}.
                  </strong>{" "}
                  Le règlement sera attendu{" "}
                  {days === 0
                    ? "le jour de la facture"
                    : `${days} ${days === 1 ? "jour" : "jours"} après la date de chaque facture`}
                  {end ? `, jusqu’au ${formatDate(end)}` : ""}.
                </p>
              </div>
              <p className="sales-muted">
                Chaque occurrence prépare un brouillon à valider. Les prix
                restent ceux de ce modèle ; les clients et articles doivent
                rester actifs.
              </p>
              <ActionError error={action.error} />
              <div className="sales-actions sales-form-footer">
                <Link
                  className="button-link secondary-link"
                  to={`/invoices/${id}`}
                >
                  Retour à la facture
                </Link>
                <button disabled={action.isPending}>
                  {action.isPending ? "Enregistrement…" : "Créer la récurrence"}
                </button>
              </div>
            </form>
          )}
        </>
      )}
    </section>
  );
}
