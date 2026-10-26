import axios from "axios";
import { ClientPortalAccessCard } from "../../portal/ClientPortalAccessCard";
import { useState, useMemo } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { ClientStatusBadge } from "../components/ClientStatusBadge";
import { ClientStatusDialog } from "../components/ClientStatusDialog";
import { useClient, useUpdateClientStatus } from "../hooks/useClients";
import { useInvoices } from "../../invoices/hooks/useInvoices";
import { InvoiceStatusBadge } from "../../invoices/components/InvoiceStatusBadge";
import type { ProblemDetails } from "../types/client";
import { formatDateTime, formatDate, formatMoney } from "../../../utils/format";
import { Skeleton } from "../../../components/ui/Skeleton";
import { Icon } from "../../../components/ui/Icon";

export function ClientDetailsPage() {
  const { id = "" } = useParams();
  const location = useLocation();
  const { user } = useAuth();

  const canManage =
    user?.role === "APP_ADMIN" ||
    user?.role === "ADMIN" ||
    user?.role === "INVOICE_MANAGER";

  const query = useClient(id);
  const mutation = useUpdateClientStatus();

  // Invoices specifically for this client
  const clientInvoices = useInvoices({
    page: 0,
    size: 20,
    sort: "createdAt,desc",
    clientId: id,
  });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [error, setError] = useState("");
  const toast = (location.state as { toast?: string } | null)?.toast;

  // Compute Client Financial Metrics
  const { totalCa, totalPaid, totalPending, invoiceCount } = useMemo(() => {
    let ca = 0;
    let paidCount = 0;
    let pendingAmount = 0;
    const items = clientInvoices.data?.content ?? [];

    items.forEach((inv) => {
      if (inv.status === "PAID" || inv.status === "VALIDATED") {
        ca += inv.totalIncludingTax;
      }
      if (inv.status === "PAID") {
        paidCount++;
      }
      if (inv.status === "VALIDATED") {
        pendingAmount += inv.outstandingAmount;
      }
    });

    return {
      totalCa: ca,
      totalPaid: paidCount,
      totalPending: pendingAmount,
      invoiceCount: clientInvoices.data?.totalElements ?? items.length,
    };
  }, [clientInvoices.data]);

  if (query.isPending) {
    return (
      <div className="max-w-6xl mx-auto w-full p-8 flex gap-8">
        <Skeleton width="100%" height="400px" radius="12px" />
      </div>
    );
  }

  if (query.isError) {
    const notFound =
      axios.isAxiosError(query.error) && query.error.response?.status === 404;
    return (
      <section
        className="max-w-4xl mx-auto w-full p-12 text-center bg-white rounded-xl border border-slate-200 mt-8"
        data-testid="client-not-found"
      >
        <h2 className="text-xl font-bold text-slate-900 mb-2">
          {notFound ? "Client introuvable" : "Impossible de charger le client"}
        </h2>
        <p className="text-slate-500 mb-6">
          {notFound
            ? "Ce client n’existe pas ou n’est plus disponible."
            : "Veuillez réessayer ultérieurement."}
        </p>
        <div className="flex justify-center gap-4">
          {!notFound && (
            <button
              className="px-4 py-2 bg-indigo-50 text-indigo-600 rounded-lg font-medium hover:bg-indigo-100"
              onClick={() => query.refetch()}
            >
              Réessayer
            </button>
          )}
          <Link
            to="/clients"
            className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg font-medium hover:bg-slate-200"
          >
            Retour aux clients
          </Link>
        </div>
      </section>
    );
  }

  const client = query.data!;

  async function confirmStatus() {
    setError("");
    try {
      await mutation.mutateAsync({
        id: client.id,
        active: !client.active,
        version: client.version,
      });
      setDialogOpen(false);
    } catch (caught) {
      const problem = axios.isAxiosError<ProblemDetails>(caught)
        ? caught.response?.data
        : undefined;
      setError(
        problem?.detail ?? "Impossible de modifier le statut du client.",
      );
      setDialogOpen(false);
    }
  }

  // Format author UUID into a clean user badge
  const formatUser = (uuid: string) => {
    if (uuid && uuid.length > 20 && uuid.includes("-")) {
      return (
        <span className="inline-flex items-center gap-2">
          <span className="w-6 h-6 rounded-full bg-slate-200 text-slate-600 text-xs font-bold flex items-center justify-center">
            A
          </span>
          <span className="font-medium text-slate-700">Administrateur</span>
        </span>
      );
    }
    return uuid || "Système";
  };

  return (
    <section
      data-testid="client-details-page"
      className="max-w-7xl mx-auto w-full p-4 md:p-8 space-y-6"
    >
      {/* Toast Notification */}
      {toast && (
        <div className="bg-emerald-50 text-emerald-600 px-4 py-3 rounded-lg border border-emerald-100 text-sm font-medium mb-6">
          {toast}
        </div>
      )}

      {error && (
        <div className="bg-red-50 text-red-600 px-4 py-3 rounded-lg border border-red-100 text-sm font-medium mb-6">
          {error}
        </div>
      )}

      {/* Header and Actions */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <div className="flex items-center gap-3 mb-1">
            <Link
              to="/clients"
              className="text-slate-400 hover:text-slate-600 transition-colors"
            >
              <Icon name="chevronLeft" size={20} />
            </Link>
            <span className="text-sm font-bold text-slate-400 tracking-wider uppercase font-mono">
              {client.clientCode}
            </span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900">
            {client.clientName}
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Fiche client, historique des règlements et coordonnées commerciales.
          </p>
        </div>

        {/* Buttons - Primary CTA first, subtle destructive action */}
        {canManage && (
          <div className="flex items-center gap-3">
            <button
              data-testid="details-status"
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors border ${
                client.active
                  ? "bg-white text-red-600 border-red-200 hover:bg-red-50"
                  : "bg-white text-emerald-600 border-emerald-200 hover:bg-emerald-50"
              }`}
              onClick={() => setDialogOpen(true)}
            >
              {client.active ? "Désactiver le compte" : "Activer le compte"}
            </button>
            <Link
              to={`/clients/${id}/edit`}
              className="px-4 py-2 bg-indigo-600 !text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors shadow-sm flex items-center gap-2"
            >
              <Icon name="edit" size={16} /> Modifier le client
            </Link>
          </div>
        )}
      </div>

      {/* Financial Mini-KPIs Bento */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <Icon name="trend-up" size={24} />
          </div>
          <div>
            <p className="text-slate-500 text-sm font-medium">
              Chiffre d’affaires TTC
            </p>
            <p className="text-2xl font-bold text-slate-900">
              {formatMoney(totalCa)}
            </p>
          </div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
            <Icon name="status" size={24} />
          </div>
          <div>
            <p className="text-slate-500 text-sm font-medium">
              En attente de paiement
            </p>
            <p className="text-2xl font-bold text-slate-900">
              {formatMoney(totalPending)}
            </p>
          </div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <Icon name="invoice" size={24} />
          </div>
          <div>
            <p className="text-slate-500 text-sm font-medium">
              Factures émises
            </p>
            <p className="text-2xl font-bold text-slate-900">
              {invoiceCount}{" "}
              <span className="text-sm font-normal text-slate-400">
                ({totalPaid} payée{totalPaid > 1 ? "s" : ""})
              </span>
            </p>
          </div>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row gap-6 items-start">
        {/* Main Details (Left) */}
        <div className="flex-1 w-full space-y-6">
          {/* Client Information Card */}
          <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
            <div className="p-6 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center font-bold">
                  {client.clientName.charAt(0).toUpperCase()}
                </div>
                <div>
                  <h2 className="font-bold text-slate-900">
                    Coordonnées de facturation
                  </h2>
                  <p className="text-xs text-slate-500">
                    Données légales et commerciales
                  </p>
                </div>
              </div>
              <ClientStatusBadge active={client.active} />
            </div>

            <div className="p-6 grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-1">
                  Identifiant Commun de l'Entreprise (ICE)
                </p>
                <p className="text-slate-900 font-mono font-medium text-sm mt-1">
                  {client.ice || (
                    <span className="text-slate-400 italic">Non renseigné</span>
                  )}
                </p>
              </div>

              <div>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-1">
                  Numéro de téléphone
                </p>
                <p className="text-slate-900 text-sm mt-1">
                  {client.phoneNumber || (
                    <span className="text-slate-400 italic">Non renseigné</span>
                  )}
                </p>
              </div>

              <div>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-1">
                  Adresse électronique
                </p>
                <p className="text-slate-900 text-sm mt-1">
                  {client.email ? (
                    <a
                      href={`mailto:${client.email}`}
                      className="text-indigo-600 hover:underline"
                    >
                      {client.email}
                    </a>
                  ) : (
                    <span className="text-slate-400 italic">Non renseigné</span>
                  )}
                </p>
              </div>

              <div>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-1">
                  Adresse postale
                </p>
                <p className="text-slate-700 text-sm mt-1">
                  {client.address || (
                    <span className="text-slate-400 italic">
                      Non renseignée
                    </span>
                  )}
                </p>
              </div>
            </div>
          </div>

          {/* Recent Invoices for this Client */}
          <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
            <div className="p-6 border-b border-slate-100 flex justify-between items-center">
              <div>
                <h3 className="font-bold text-slate-900">
                  Historique des factures
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Dernières factures émises pour ce client
                </p>
              </div>
              {canManage && (
                <Link
                  to={`/invoices/new?clientId=${client.id}`}
                  className="text-xs font-medium text-indigo-600 hover:text-indigo-700 flex items-center gap-1"
                >
                  <Icon name="plus" size={14} /> Créer une facture
                </Link>
              )}
            </div>

            {clientInvoices.isPending ? (
              <div className="p-6">
                <Skeleton width="100%" height="150px" radius="8px" />
              </div>
            ) : (clientInvoices.data?.content.length ?? 0) === 0 ? (
              <div className="p-8 text-center text-slate-400 text-sm">
                Aucune facture enregistrée pour ce client.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm whitespace-nowrap">
                  <thead className="bg-slate-50/80 text-slate-500 text-xs uppercase tracking-wider">
                    <tr>
                      <th className="px-5 py-3 font-semibold">N° Facture</th>
                      <th className="px-5 py-3 font-semibold">Date</th>
                      <th className="px-5 py-3 font-semibold text-right">
                        Total TTC
                      </th>
                      <th className="px-5 py-3 font-semibold">Statut</th>
                      <th className="px-5 py-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-slate-700">
                    {clientInvoices.data?.content.map((invoice) => (
                      <tr
                        key={invoice.id}
                        className="hover:bg-slate-50/50 transition-colors"
                      >
                        <td className="px-5 py-3.5 font-bold text-indigo-600">
                          <Link to={`/invoices/${invoice.id}`}>
                            {invoice.invoiceNumber ?? "Brouillon"}
                          </Link>
                        </td>
                        <td className="px-5 py-3.5 text-slate-600 text-xs">
                          {formatDate(invoice.invoiceDate)}
                        </td>
                        <td className="px-5 py-3.5 text-right font-bold text-slate-900 tabular-nums">
                          {formatMoney(invoice.totalIncludingTax)}
                        </td>
                        <td className="px-5 py-3.5">
                          <InvoiceStatusBadge status={invoice.status} />
                        </td>
                        <td className="px-5 py-3.5 text-right">
                          <Link
                            to={`/invoices/${invoice.id}`}
                            className="p-1.5 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg inline-flex"
                            title="Consulter"
                          >
                            <Icon name="eye" size={16} />
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* Sidebar (Right) */}
        <aside className="w-full lg:w-80 shrink-0">
          {canManage && (
            <ClientPortalAccessCard
              clientId={client.id}
              active={client.active}
            />
          )}
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-6 shadow-sm">
            <h3 className="font-bold text-slate-900 mb-6 flex items-center gap-2">
              <span className="text-slate-400">
                <Icon name="status" size={18} />
              </span>
              Traçabilité
            </h3>
            <div className="space-y-6">
              <div>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">
                  Création du compte
                </p>
                <p className="text-slate-900 text-sm font-medium">
                  {formatDateTime(client.createdAt)}
                </p>
                <div className="mt-2">{formatUser(client.createdBy)}</div>
              </div>

              {client.updatedAt !== client.createdAt && (
                <div className="pt-4 border-t border-slate-200">
                  <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">
                    Dernière modification
                  </p>
                  <p className="text-slate-900 text-sm font-medium">
                    {formatDateTime(client.updatedAt)}
                  </p>
                  <div className="mt-2">{formatUser(client.updatedBy)}</div>
                </div>
              )}

              <div className="pt-4 border-t border-slate-200">
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-1">
                  Version enregistrement
                </p>
                <p className="text-slate-500 text-sm font-mono">
                  v{client.version}
                </p>
              </div>
            </div>
          </div>
        </aside>
      </div>

      <ClientStatusDialog
        client={dialogOpen ? client : null}
        pending={mutation.isPending}
        onClose={() => setDialogOpen(false)}
        onConfirm={confirmStatus}
      />
    </section>
  );
}
