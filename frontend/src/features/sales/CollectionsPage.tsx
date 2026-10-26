import { useState } from "react";
import { Link } from "react-router-dom";
import { PageHeader } from "../../components/ui/PageHeader";
import { Icon } from "../../components/ui/Icon";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatMoney } from "../../utils/format";
import { salesApi, type Reminder } from "./salesApi";
import {
  ActionError,
  QueryState,
  SalesEmptyState,
  SalesPagination,
} from "./SalesUi";
import { useCompanyQuery, useSalesAction } from "./salesHooks";
import { ReminderDraft } from "./ReminderDraft";

export function CollectionsPage() {
  const [page, setPage] = useState(0);
  const { user } = useAuth();
  const query = useCompanyQuery(["overdue", page], () =>
    salesApi.overdue(page),
  );
  const prepare = useSalesAction(salesApi.prepareReminder);
  const [reminder, setReminder] = useState<Reminder | null>(null);
  return (
    <section className="sales-page">
      <PageHeader
        eyebrow="Ventes"
        title="Échéances et relances"
        description="Factures validées dont l’échéance est dépassée et le solde reste à régler."
      />
      <div className="sales-card sales-list-card">
        <header className="sales-section-heading">
          <div>
            <h2>Factures à relancer</h2>
            <p>Le solde tient compte des règlements déjà enregistrés.</p>
          </div>
          {query.data && (
            <span className="sales-count">
              {query.data.page.totalElements}{" "}
              {query.data.page.totalElements === 1
                ? "facture en retard"
                : "factures en retard"}
            </span>
          )}
        </header>
        <QueryState query={query} />
        <ActionError error={prepare.error} />
        {query.data &&
          (query.data.content.length ? (
            <>
              <div className="sales-table sales-responsive-table">
                <table aria-label="Factures dont l’échéance est dépassée">
                  <thead>
                    <tr>
                      <th>Facture</th>
                      <th>Client</th>
                      <th>Échéance</th>
                      <th className="numeric">Reçu</th>
                      <th className="numeric">Solde restant</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {query.data.content.map((i) => (
                      <tr key={i.id}>
                        <td data-label="Facture" className="sales-cell-wide">
                          <Link
                            className="sales-document-link"
                            to={`/invoices/${i.id}`}
                          >
                            {i.invoiceNumber}
                          </Link>
                        </td>
                        <td data-label="Client" className="sales-cell-wide">
                          {i.client.clientName}
                        </td>
                        <td className="sales-warning" data-label="Échéance">
                          {formatDate(i.dueDate!)}
                        </td>
                        <td className="numeric" data-label="Reçu">
                          {formatMoney(i.paidAmount)}
                        </td>
                        <td
                          className="numeric sales-balance-due"
                          data-label="Solde restant"
                        >
                          <strong>{formatMoney(i.outstandingAmount)}</strong>
                        </td>
                        <td
                          data-label="Action"
                          className="sales-cell-wide sales-cell-actions"
                        >
                          {user?.role !== "VIEWER" && (
                            <button
                              className="secondary"
                              disabled={prepare.isPending}
                              onClick={() =>
                                prepare.mutate(i.id, { onSuccess: setReminder })
                              }
                            >
                              <Icon name="edit" size={15} />
                              Préparer la relance
                            </button>
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
              title="Aucune facture à relancer"
              description="Les factures validées apparaîtront ici dès que leur échéance sera dépassée avec un solde restant à régler."
              icon="calendar"
              action={
                <Link className="button-link secondary-link" to="/invoices">
                  Consulter les factures
                </Link>
              }
            />
          ))}
        <p className="sales-muted sales-table-note">
          Les échéances se renseignent dans le détail de chaque facture. Les
          relances sont des messages à copier dans votre messagerie.
        </p>
      </div>
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
