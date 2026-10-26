import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PageHeader } from "../../components/ui/PageHeader";
import { Icon } from "../../components/ui/Icon";
import { useAuth } from "../../auth/useAuth";
import { InvoiceCreateForm } from "../invoices/components/InvoiceCreateForm";
import type { InvoiceInput } from "../invoices/types/invoice";
import { formatDate, formatMoney } from "../../utils/format";
import { salesApi, savePdf } from "./salesApi";
import {
  ActionError,
  QueryState,
  SalesEmptyState,
  SalesPagination,
} from "./SalesUi";
import { today, useCompanyQuery, useSalesAction } from "./salesHooks";

const statusLabel = {
  OPEN: "Ouvert",
  CONVERTED: "Converti",
  CANCELLED: "Annulé",
};

export function QuotationListPage() {
  const [page, setPage] = useState(0);
  const { user } = useAuth();
  const query = useCompanyQuery(["quotations", page], () =>
    salesApi.quotes(page),
  );
  return (
    <section className="sales-page">
      <PageHeader
        eyebrow="Ventes"
        title="Devis"
        description="Préparez vos propositions et transformez-les en factures."
        action={
          user?.role !== "VIEWER" ? (
            <Link className="button-link" to="/quotes/new">
              <Icon name="plus" size={17} />
              Nouveau devis
            </Link>
          ) : undefined
        }
      />
      <div className="sales-card sales-list-card">
        <header className="sales-section-heading">
          <div>
            <h2>Vos propositions commerciales</h2>
            <p>Retrouvez les conditions proposées et leur validité.</p>
          </div>
          {query.data && (
            <span className="sales-count">
              {query.data.page.totalElements} devis
            </span>
          )}
        </header>
        <QueryState query={query} />
        {query.data &&
          (query.data.content.length ? (
            <>
              <div className="sales-table sales-responsive-table">
                <table aria-label="Liste des devis">
                  <thead>
                    <tr>
                      <th>Devis</th>
                      <th>Client</th>
                      <th>Validité</th>
                      <th className="numeric">Total TTC</th>
                      <th>Statut</th>
                    </tr>
                  </thead>
                  <tbody>
                    {query.data.content.map((q) => (
                      <tr key={q.id}>
                        <td data-label="Devis" className="sales-cell-wide">
                          <Link
                            className="sales-document-link"
                            to={`/quotes/${q.id}`}
                          >
                            {q.quoteNumber}
                          </Link>
                          <small>{formatDate(q.issueDate)}</small>
                        </td>
                        <td data-label="Client" className="sales-cell-wide">
                          {q.terms.client.name}
                        </td>
                        <td data-label="Validité">
                          {formatDate(q.validUntil)}
                          {q.status === "OPEN" && q.validUntil < today() && (
                            <small className="sales-warning">
                              Validité dépassée
                            </small>
                          )}
                        </td>
                        <td className="numeric" data-label="Total TTC">
                          {formatMoney(q.terms.totals.totalIncludingTax)}
                        </td>
                        <td data-label="Statut">
                          <span
                            className={`sales-badge sales-badge-${q.status.toLowerCase()}`}
                          >
                            {statusLabel[q.status]}
                          </span>
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
              title="Votre premier devis commence ici"
              description="Sélectionnez un client, ajoutez vos articles et conservez les mêmes prix lors de la création de la facture."
              action={
                user?.role !== "VIEWER" ? (
                  <Link className="button-link" to="/quotes/new">
                    Nouveau devis
                  </Link>
                ) : undefined
              }
            />
          ))}
      </div>
    </section>
  );
}

export function QuotationCreatePage() {
  const navigate = useNavigate();
  const [until, setUntil] = useState(() => {
    const date = new Date();
    date.setDate(date.getDate() + 30);
    return date.toLocaleDateString("en-CA");
  });
  const action = useSalesAction(salesApi.createQuote);
  async function submit(document: InvoiceInput) {
    const quote = await action.mutateAsync({ document, validUntil: until });
    navigate(`/quotes/${quote.id}`, { replace: true });
  }
  return (
    <section className="wide-form-page">
      <PageHeader
        eyebrow="Ventes"
        title="Nouveau devis"
        description="Les articles et les prix seront conservés lors de la conversion en facture."
      />
      <div className="sales-card sales-form sales-validity">
        <div>
          <span className="sales-kicker">Conditions du devis</span>
          <h2>Durée de validité</h2>
          <p className="sales-muted">
            Le devis pourra être converti en facture jusqu’à cette date.
          </p>
        </div>
        <label>
          Valable jusqu’au
          <input
            type="date"
            required
            min={today()}
            value={until}
            onChange={(e) => setUntil(e.target.value)}
            aria-invalid={Boolean(until && until < today())}
            aria-describedby={
              until && until < today() ? "quote-validity-error" : undefined
            }
          />
          {until && until < today() && (
            <span id="quote-validity-error" className="field-error">
              Choisissez une date à partir d’aujourd’hui.
            </span>
          )}
        </label>
      </div>
      <InvoiceCreateForm
        onSubmit={submit}
        documentKind="quotation"
        cancelTo="/quotes"
        submitDisabled={!until || until < today()}
      />
    </section>
  );
}

export function QuotationDetailsPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const query = useCompanyQuery(["quotation", id], () => salesApi.quote(id));
  const convert = useSalesAction((version: number) =>
    salesApi.convertQuote(id, version),
  );
  const cancel = useSalesAction((version: number) =>
    salesApi.cancelQuote(id, version),
  );
  const download = useSalesAction(async () => {
    const blob = await salesApi.quotePdf(id);
    savePdf(blob, `${query.data?.quoteNumber}.pdf`);
  });
  const q = query.data;
  const [confirm, setConfirm] = useState(false);
  const busy = convert.isPending || cancel.isPending || download.isPending;
  return (
    <section className="sales-page">
      <PageHeader
        eyebrow="Ventes"
        title={q?.quoteNumber ?? "Devis"}
        description={q?.terms.client.name ?? "Détail du devis"}
        action={
          <Link className="button-link secondary-link" to="/quotes">
            Retour aux devis
          </Link>
        }
      />
      <QueryState query={query} />
      {q && (
        <div className="sales-card">
          <div className="sales-document-heading">
            <div>
              <span className="sales-kicker">Proposition commerciale</span>
              <h2>{q.terms.client.name}</h2>
              <p className="sales-muted">
                Émis le {formatDate(q.issueDate)} · Valable jusqu’au{" "}
                {formatDate(q.validUntil)}
              </p>
            </div>
            <span
              className={`sales-badge sales-badge-${q.status.toLowerCase()}`}
            >
              {statusLabel[q.status]}
            </span>
          </div>
          <div className="sales-actions">
            <button
              className="secondary"
              disabled={busy}
              onClick={() => download.mutate(undefined)}
            >
              <Icon name="download" size={16} />
              Télécharger le devis PDF
            </button>
            {q.invoiceId && (
              <Link className="button-link" to={`/invoices/${q.invoiceId}`}>
                Voir la facture créée
              </Link>
            )}
            {user?.role !== "VIEWER" && q.status === "OPEN" && (
              <>
                <button
                  disabled={busy || q.validUntil < today()}
                  onClick={() =>
                    convert.mutate(q.version, {
                      onSuccess: (invoice) =>
                        navigate(`/invoices/${invoice.id}`),
                    })
                  }
                >
                  Créer la facture
                </button>
                <button
                  className="secondary sales-destructive-action"
                  disabled={busy}
                  onClick={() => setConfirm(true)}
                >
                  Annuler le devis
                </button>
              </>
            )}
          </div>
          {q.status === "OPEN" && q.validUntil < today() && (
            <p className="sales-warning">Ce devis a expiré.</p>
          )}
          {confirm && (
            <div
              className="sales-confirmation"
              role="group"
              aria-label="Confirmation d’annulation du devis"
            >
              <p>Annuler définitivement ce devis ?</p>
              <div className="sales-actions">
                <button
                  className="danger"
                  disabled={busy}
                  onClick={() => {
                    cancel.mutate(q.version, {
                      onSuccess: () => setConfirm(false),
                    });
                  }}
                >
                  Confirmer l’annulation
                </button>
                <button className="secondary" onClick={() => setConfirm(false)}>
                  Retour
                </button>
              </div>
            </div>
          )}
          <ActionError
            error={convert.error ?? cancel.error ?? download.error}
          />
          <div className="sales-table">
            <table aria-label="Articles du devis">
              <thead>
                <tr>
                  <th>Article</th>
                  <th className="numeric">Quantité</th>
                  <th className="numeric">Prix HT</th>
                  <th className="numeric">Remise</th>
                  <th className="numeric">TVA</th>
                  <th className="numeric">Total HT</th>
                </tr>
              </thead>
              <tbody>
                {q.terms.lines.map((l) => (
                  <tr key={l.productId}>
                    <td>
                      <strong>{l.productName}</strong>
                      <small>{l.productCode}</small>
                    </td>
                    <td className="numeric">{l.amounts.quantity}</td>
                    <td className="numeric">
                      {formatMoney(l.amounts.unitPriceExcludingTax)}
                    </td>
                    <td className="numeric">{l.amounts.discountRate} %</td>
                    <td className="numeric">{l.amounts.vatRate} %</td>
                    <td className="numeric">
                      {formatMoney(l.amounts.netAmountExcludingTax)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="sales-document-footer">
            <p className="sales-muted">
              Les prix et remises de ce devis sont conservés lors de sa
              conversion en facture.
            </p>
            <dl className="sales-quote-totals">
              <div>
                <dt>Net HT</dt>
                <dd>{formatMoney(q.terms.totals.taxableAmount)}</dd>
              </div>
              <div>
                <dt>TVA</dt>
                <dd>{formatMoney(q.terms.totals.totalVat)}</dd>
              </div>
              <div className="sales-quote-grand-total">
                <dt>Total TTC</dt>
                <dd>{formatMoney(q.terms.totals.totalIncludingTax)}</dd>
              </div>
            </dl>
          </div>
        </div>
      )}
    </section>
  );
}
