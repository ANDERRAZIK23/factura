import { Link } from "react-router-dom";
import { formatMoney } from "../../../utils/format";
import { ClientStatusBadge } from "./ClientStatusBadge";
import type { ClientSummary } from "../types/client";
import { Icon } from "../../../components/ui/Icon";

export function ClientTable({
  clients,
  canManage,
  onStatus,
  clientRevenue,
}: {
  clients: ClientSummary[];
  canManage: boolean;
  onStatus: (client: ClientSummary) => void;
  clientRevenue?: Map<string, number>;
}) {
  return (
    <div className="table-wrap client-table">
      <table>
        <caption className="sr-only">Clients de la page sélectionnée</caption>
        <thead>
          <tr>
            <th scope="col">Client</th>
            <th scope="col">ICE</th>
            <th scope="col">Contact</th>
            <th scope="col">Statut</th>
            <th scope="col" className="numeric-column">
              Facturé TTC
            </th>
            <th scope="col" className="numeric-column">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {clients.map((client) => {
            const revenue = clientRevenue?.get(client.id) ?? 0;
            return (
              <tr key={client.id}>
                <td data-label="Client">
                  <div className="entity-cell">
                    <span className="entity-avatar" aria-hidden="true">
                      {client.clientName.charAt(0).toUpperCase()}
                    </span>
                    <div className="list-cell-copy">
                      <Link
                        to={`/clients/${client.id}`}
                        className="font-bold text-slate-900 hover:text-indigo-600 transition-colors"
                      >
                        {client.clientName}
                      </Link>
                      <span className="text-xs text-slate-500 font-medium">
                        #{client.clientCode}
                      </span>
                    </div>
                  </div>
                </td>
                <td data-label="ICE">
                  {client.ice || (
                    <span className="text-slate-500 italic">Non renseigné</span>
                  )}
                </td>
                <td data-label="Contact">
                  <div className="list-cell-copy">
                    <span>
                      {client.phoneNumber || (
                        <span className="text-slate-500 italic">
                          Tél non renseigné
                        </span>
                      )}
                    </span>
                    <span className="text-xs text-slate-500">
                      {client.email || (
                        <span className="text-slate-500 italic">
                          E-mail non renseigné
                        </span>
                      )}
                    </span>
                  </div>
                </td>
                <td data-label="Statut">
                  <ClientStatusBadge active={client.active} />
                </td>
                <td data-label="Facturé TTC" className="numeric-cell">
                  <span
                    className={`font-bold ${revenue > 0 ? "text-slate-900" : "text-slate-500"}`}
                  >
                    {clientRevenue ? formatMoney(revenue) : "—"}
                  </span>
                </td>
                <td data-label="Actions" className="action-cell">
                  <div className="row-actions">
                    <Link
                      to={`/clients/${client.id}`}
                      className="button-link secondary-link compact-button"
                      title="Voir la fiche client"
                      aria-label="Voir"
                    >
                      <Icon name="eye" size={16} />
                    </Link>
                    {canManage && (
                      <>
                        <Link
                          to={`/clients/${client.id}/edit`}
                          className="button-link secondary-link compact-button"
                          title="Modifier le client"
                          aria-label="Modifier"
                        >
                          <Icon name="edit" size={16} />
                        </Link>
                        <button
                          data-testid={`status-${client.id}`}
                          type="button"
                          className="secondary compact-button"
                          onClick={() => onStatus(client)}
                          aria-label={client.active ? "Désactiver" : "Activer"}
                          title={`${client.active ? "Désactiver" : "Activer"} ${client.clientName}`}
                        >
                          <Icon name="status" size={16} />
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
