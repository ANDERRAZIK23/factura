import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import { formatDate, formatMoney } from "../../../utils/format";
import type { InvoiceSummary } from "../types/invoice";
import { InvoiceStatusBadge } from "./InvoiceStatusBadge";

export function InvoiceTable({
  invoices,
  canManage,
}: {
  invoices: InvoiceSummary[];
  canManage: boolean;
}) {
  return (
    <div className="table-wrap invoice-table">
      <table>
        <caption className="sr-only">Factures de la page sélectionnée</caption>
        <thead>
          <tr>
            <th scope="col">Numéro</th>
            <th scope="col">Client</th>
            <th scope="col">Date</th>
            <th scope="col" className="numeric-column">
              Total HT
            </th>
            <th scope="col" className="numeric-column">
              TVA
            </th>
            <th scope="col" className="numeric-column">
              Total TTC
            </th>
            <th scope="col">Statut</th>
            <th scope="col" className="numeric-column">
              Solde restant
            </th>
            <th scope="col" className="action-cell">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {invoices.map((invoice) => (
            <tr key={invoice.id}>
              <td data-label="Numéro">
                <div>
                  <Link
                    className="invoice-number"
                    to={`/invoices/${invoice.id}`}
                  >
                    {invoice.invoiceNumber ?? "Brouillon"}
                  </Link>
                  <small>
                    {invoice.lineCount}{" "}
                    {invoice.lineCount === 1 ? "ligne" : "lignes"}
                  </small>
                </div>
              </td>
              <td data-label="Client">
                <div>
                  <strong>{invoice.client.clientName}</strong>
                  <small>{invoice.client.clientCode}</small>
                </div>
              </td>
              <td data-label="Date">{formatDate(invoice.invoiceDate)}</td>
              <td data-label="Total HT" className="numeric-cell">
                {formatMoney(invoice.taxableAmount)}
              </td>
              <td data-label="TVA" className="numeric-cell">
                {formatMoney(invoice.totalVat)}
              </td>
              <td data-label="Total TTC" className="numeric-cell">
                <strong className="invoice-amount">
                  {formatMoney(invoice.totalIncludingTax)}
                </strong>
              </td>
              <td data-label="Statut">
                <InvoiceStatusBadge status={invoice.status} />
              </td>
              <td data-label="Solde restant" className="numeric-cell">
                {invoice.status === "DRAFT"
                  ? "—"
                  : formatMoney(invoice.outstandingAmount)}
                {invoice.dueDate && (
                  <small>Échéance : {formatDate(invoice.dueDate)}</small>
                )}
              </td>
              <td data-label="Actions" className="action-cell">
                <div className="row-actions">
                  <Link
                    to={`/invoices/${invoice.id}`}
                    className="button-link secondary-link compact-button"
                    title="Voir le détail de la facture"
                    aria-label="Voir le détail"
                  >
                    <Icon name="eye" size={16} />
                  </Link>
                  {canManage && invoice.status === "DRAFT" && (
                    <Link
                      to={`/invoices/${invoice.id}/edit`}
                      className="button-link secondary-link compact-button"
                      title="Modifier le brouillon"
                      aria-label="Modifier"
                    >
                      <Icon name="edit" size={16} />
                    </Link>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
