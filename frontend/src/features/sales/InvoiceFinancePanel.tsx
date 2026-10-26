import { useState } from "react";
import { Link } from "react-router-dom";
import {
  formatDate,
  formatMoney,
  formatPaymentMethod,
} from "../../utils/format";
import type { Invoice } from "../invoices/types/invoice";
import { salesApi, type PaymentInput, type Reminder } from "./salesApi";
import { ActionError, QueryState } from "./SalesUi";
import { today, useCompanyQuery, useSalesAction } from "./salesHooks";
import { ReminderDraft } from "./ReminderDraft";
import "./invoice-finance.css";

export function InvoiceFinancePanel({
  invoice,
  canManage,
}: {
  invoice: Invoice;
  canManage: boolean;
}) {
  const query = useCompanyQuery(
    ["payments", invoice.id],
    () => salesApi.payments(invoice.id),
    invoice.status !== "DRAFT",
  );
  const [editingDue, setEditingDue] = useState(false);
  const [due, setDue] = useState(invoice.dueDate ?? "");
  const [adding, setAdding] = useState(false);
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(today());
  const [method, setMethod] = useState("");
  const [reference, setReference] = useState("");
  const [reversing, setReversing] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [reminder, setReminder] = useState<Reminder | null>(null);
  const [history, setHistory] = useState(false);
  const reminders = useCompanyQuery(
    ["reminders", invoice.id],
    () => salesApi.reminders(invoice.id),
    history,
  );
  const dueAction = useSalesAction((value: string) =>
    salesApi.dueDate(invoice.id, invoice.version, value || null),
  );
  const pay = useSalesAction((input: PaymentInput) =>
    salesApi.pay(invoice.id, input),
  );
  const reverse = useSalesAction((paymentId: string) =>
    salesApi.reversePayment(invoice.id, paymentId, invoice.version, reason),
  );
  const prepare = useSalesAction(() => salesApi.prepareReminder(invoice.id));
  const busy =
    dueAction.isPending ||
    pay.isPending ||
    reverse.isPending ||
    prepare.isPending;
  const overdue =
    invoice.status === "VALIDATED" &&
    invoice.outstandingAmount > 0 &&
    invoice.dueDate !== null &&
    invoice.dueDate < today();
  return (
    <section
      className="sales-card sales-form invoice-finance"
      aria-labelledby="invoice-finance-title"
    >
      <div className="finance-heading">
        <span className="finance-eyebrow">Suivi financier</span>
        <h2 id="invoice-finance-title">Échéance et règlements</h2>
      </div>
      <dl className="sales-totals">
        <div>
          <dt>Total TTC</dt>
          <dd>{formatMoney(invoice.totalIncludingTax)}</dd>
        </div>
        <div>
          <dt>Montant reçu</dt>
          <dd>{formatMoney(invoice.paidAmount)}</dd>
        </div>
        <div className="finance-balance">
          <dt>Solde restant</dt>
          <dd>{formatMoney(invoice.outstandingAmount)}</dd>
        </div>
      </dl>
      <div className={`finance-due${overdue ? " is-overdue" : ""}`}>
        <span>Échéance</span>
        <strong>
          {invoice.dueDate ? formatDate(invoice.dueDate) : "Non renseignée"}
        </strong>
        {overdue && <span className="finance-overdue">En retard</span>}
      </div>
      {canManage && invoice.status !== "CANCELLED" && (
        <button
          type="button"
          className="secondary"
          disabled={busy}
          onClick={() => {
            dueAction.reset();
            setDue(invoice.dueDate ?? "");
            setEditingDue(!editingDue);
          }}
        >
          Modifier l’échéance
        </button>
      )}
      {editingDue && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            dueAction.mutate(due, { onSuccess: () => setEditingDue(false) });
          }}
        >
          <label>
            Échéance de règlement
            <input
              disabled={busy}
              type="date"
              min={invoice.invoiceDate}
              value={due}
              onChange={(e) => setDue(e.target.value)}
            />
          </label>
          <div className="sales-actions">
            <button disabled={busy}>Enregistrer l’échéance</button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => setEditingDue(false)}
            >
              Fermer
            </button>
          </div>
        </form>
      )}
      {canManage &&
        invoice.status === "VALIDATED" &&
        invoice.outstandingAmount > 0 && (
          <div className="sales-actions">
            <button
              disabled={busy}
              type="button"
              onClick={() => {
                pay.reset();
                setAmount(invoice.outstandingAmount.toFixed(2));
                setAdding(!adding);
              }}
            >
              Enregistrer un règlement
            </button>
          </div>
        )}
      {adding && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            pay.mutate(
              {
                version: invoice.version,
                amount: Number(amount),
                paymentDate: date,
                paymentMethod: method,
                reference,
              },
              {
                onSuccess: () => {
                  setAdding(false);
                  setReference("");
                },
              },
            );
          }}
        >
          <label>
            Montant reçu (MAD)
            <input
              disabled={busy}
              type="number"
              required
              min="0.01"
              step="0.01"
              max={invoice.outstandingAmount}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
          <label>
            Date du règlement
            <input
              disabled={busy}
              type="date"
              required
              max={today()}
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </label>
          <label>
            Mode de règlement
            <select
              disabled={busy}
              required
              value={method}
              onChange={(e) => setMethod(e.target.value)}
            >
              <option value="">Choisir…</option>
              <option value="CASH">Espèces</option>
              <option value="CHECK">Chèque</option>
              <option value="BANK_TRANSFER">Virement bancaire</option>
            </select>
          </label>
          <label>
            Référence du règlement
            <input
              disabled={busy}
              maxLength={200}
              value={reference}
              onChange={(e) => setReference(e.target.value)}
            />
          </label>
          <div className="sales-actions">
            <button disabled={busy}>Confirmer le règlement</button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => setAdding(false)}
            >
              Retour
            </button>
          </div>
        </form>
      )}
      <ActionError
        error={dueAction.error ?? pay.error ?? reverse.error ?? prepare.error}
      />
      {invoice.status !== "DRAFT" && (
        <>
          <div className="finance-section-heading">
            <h3>Historique des règlements</h3>
            {query.data && (
              <span className="finance-count">{query.data.length}</span>
            )}
          </div>
          <QueryState query={query} />
          {query.data?.length === 0 && (
            <p className="sales-muted">Aucun règlement enregistré.</p>
          )}
          {query.data?.map((payment) => (
            <div
              key={payment.id}
              className={`sales-payment${payment.reversedAt ? " is-reversed" : ""}`}
            >
              <div className="finance-payment-heading">
                <strong>{formatMoney(payment.amount)}</strong>
                <span>
                  {payment.reversedAt ? "Saisie annulée" : "Enregistré"}
                </span>
              </div>
              <p className="finance-payment-meta">
                {formatPaymentMethod(payment.paymentMethod)} ·{" "}
                {payment.paymentDate
                  ? formatDate(payment.paymentDate)
                  : "Date non renseignée · reprise de l’historique"}
              </p>
              {payment.reference && (
                <p className="finance-reference">Réf. {payment.reference}</p>
              )}
              {payment.reversedAt ? (
                <p>Saisie annulée : {payment.reversalReason}</p>
              ) : (
                canManage &&
                invoice.status !== "CANCELLED" && (
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => {
                      reverse.reset();
                      setReason("");
                      setReversing(payment.id);
                    }}
                  >
                    Corriger cette saisie
                  </button>
                )
              )}
              {reversing === payment.id && (
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    reverse.mutate(payment.id, {
                      onSuccess: () => setReversing(null),
                    });
                  }}
                >
                  <p className="sales-muted">
                    Cette correction retire le montant du suivi des paiements.
                    Elle ne déclenche aucun remboursement.
                  </p>
                  <label>
                    Motif de la correction
                    <input
                      disabled={busy}
                      required
                      maxLength={300}
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                    />
                  </label>
                  <div className="sales-actions">
                    <button className="danger" disabled={busy}>
                      Annuler cette saisie
                    </button>
                    <button
                      className="secondary"
                      type="button"
                      disabled={busy}
                      onClick={() => setReversing(null)}
                    >
                      Retour
                    </button>
                  </div>
                </form>
              )}
            </div>
          ))}
        </>
      )}
      {canManage && overdue && (
        <div className="sales-actions">
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              prepare.mutate(undefined, { onSuccess: setReminder })
            }
          >
            Préparer une relance
          </button>
        </div>
      )}
      {invoice.invoiceNumber && (
        <>
          <button
            type="button"
            className="secondary"
            onClick={() => setHistory(!history)}
          >
            {history ? "Masquer les relances" : "Historique des relances"}
          </button>
          {history && (
            <>
              <QueryState query={reminders} />
              <p className="sales-muted">20 derniers messages préparés</p>
              {reminders.data?.length === 0 && (
                <p className="sales-muted">Aucune relance préparée.</p>
              )}
              {reminders.data?.map((r) => (
                <div className="sales-payment" key={r.id}>
                  <p>
                    {formatDate(r.createdAt)} · {r.subject}
                  </p>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => setReminder(r)}
                  >
                    Revoir le message
                  </button>
                </div>
              ))}
            </>
          )}
        </>
      )}
      {canManage && invoice.status !== "CANCELLED" && (
        <div className="sales-actions">
          <Link
            className="button-link secondary-link"
            to={`/recurring-invoices/new?invoiceId=${invoice.id}`}
          >
            Créer une récurrence
          </Link>
        </div>
      )}
      {reminder && (
        <ReminderDraft
          key={reminder.id}
          reminder={reminder}
          close={() => setReminder(null)}
        />
      )}
    </section>
  );
}
