import axios from "axios";
import { useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { Skeleton } from "../../../components/ui/Skeleton";
import {
  useCancelInvoice,
  useDownloadInvoicePdf,
  useInvoice,
  useMarkInvoicePaid,
  useValidateInvoice,
} from "../hooks/useInvoices";
import { InvoiceActionDialog } from "../components/InvoiceActionDialog";
import { InvoiceStatusBadge } from "../components/InvoiceStatusBadge";
import { InvoiceDocumentPreview } from "../components/InvoiceDocumentPreview";
import type { ProblemDetails } from "../types/invoice";
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatProductUnit,
} from "../../../utils/format";
import { Icon } from "../../../components/ui/Icon";
import { InvoiceFinancePanel } from "../../sales/InvoiceFinancePanel";

export function InvoiceDetailsPage() {
  const { id } = useParams();
  const location = useLocation();
  const { user } = useAuth();
  const query = useInvoice(id!);
  const download = useDownloadInvoicePdf();
  const validation = useValidateInvoice();
  const payment = useMarkInvoicePaid();
  const cancellation = useCancelInvoice();

  const [action, setAction] = useState<"validate" | "paid" | "cancel" | null>(
    null,
  );
  const [error, setError] = useState("");
  const [toast, setToast] = useState(
    (location.state as { toast?: string } | null)?.toast ?? "",
  );

  const canManage =
    user?.role === "APP_ADMIN" ||
    user?.role === "ADMIN" ||
    user?.role === "INVOICE_MANAGER";

  if (query.isPending) {
    return (
      <div className="max-w-6xl mx-auto w-full p-8 flex gap-8">
        <Skeleton width="70%" height="800px" radius="12px" />
        <Skeleton width="30%" height="400px" radius="12px" />
      </div>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div className="max-w-4xl mx-auto w-full p-12 text-center bg-white rounded-xl border border-slate-200 mt-8">
        <h2 className="text-xl font-bold text-slate-900 mb-2">
          Impossible de charger la facture
        </h2>
        <Link to="/invoices" className="text-indigo-600 hover:underline">
          Retour à la liste
        </Link>
      </div>
    );
  }

  const invoice = query.data;

  async function getPdf() {
    if (!invoice?.pdfAvailable) return;
    setError("");
    try {
      const response = await download.mutateAsync(invoice.id);
      const url = URL.createObjectURL(response.blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = response.filename || `Facture_${invoice.invoiceNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("Le téléchargement du PDF a échoué.");
    }
  }

  async function confirm() {
    if (!action) return;
    setError("");
    try {
      if (action === "validate") {
        await validation.mutateAsync({
          id: invoice.id,
          version: invoice.version,
        });
        setToast("La facture a été validée avec succès.");
      } else if (action === "paid") {
        await payment.mutateAsync({ id: invoice.id, version: invoice.version });
        setToast("La facture a été marquée comme payée.");
      } else if (action === "cancel") {
        await cancellation.mutateAsync({
          id: invoice.id,
          version: invoice.version,
        });
        setToast("La facture a été annulée.");
      }
      setAction(null);
      setTimeout(() => setToast(""), 3000);
    } catch (err) {
      const problem = axios.isAxiosError<ProblemDetails>(err)
        ? err.response?.data
        : undefined;
      setError(problem?.detail ?? "Une erreur inattendue est survenue.");
      setAction(null);
    }
  }

  const dialog =
    action === "validate"
      ? {
          title: "Valider cette facture ?",
          description:
            "La validation recalcule tous les montants, attribue le numéro définitif et génère le PDF. Les données commerciales ne pourront plus être modifiées.",
          label: "Valider la facture",
          danger: false,
        }
      : action === "paid"
        ? {
            title: "Marquer cette facture comme payée ?",
            description:
              "Confirmez après réception et rapprochement du paiement. Le solde restant sera enregistré comme règlement.",
            label: "Marquer comme payée",
            danger: false,
          }
        : {
            title: "Annuler cette facture ?",
            description:
              "L’annulation est définitive et conserve la facture à des fins d’audit.",
            label: "Annuler la facture",
            danger: true,
          };

  const pending =
    validation.isPending || payment.isPending || cancellation.isPending;

  return (
    <section className="max-w-7xl mx-auto w-full p-4 md:p-8">
      {/* Navigation & Header */}
      <div className="mb-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <Link
          to="/invoices"
          className="text-sm font-medium text-slate-500 hover:text-slate-900 inline-flex items-center gap-2 transition-colors"
        >
          <Icon name="chevronLeft" size={16} /> Retour aux factures
        </Link>
        <div className="flex items-center gap-3">
          {error && (
            <span className="text-sm text-red-600 bg-red-50 px-3 py-1 rounded-md">
              {error}
            </span>
          )}
          {toast && (
            <span className="text-sm text-emerald-600 bg-emerald-50 px-3 py-1 rounded-md">
              {toast}
            </span>
          )}
          <InvoiceStatusBadge status={invoice.status} />
        </div>
      </div>

      <div className="flex flex-col lg:flex-row gap-8 items-start">
        <div className="flex-1 min-w-0 w-full rounded-xl border border-slate-200 bg-slate-100 overflow-hidden">
          <div className="bg-white border-b border-slate-200 px-6 py-4">
            <h1 className="text-lg font-bold text-slate-900">
              {invoice.invoiceNumber
                ? `Facture ${invoice.invoiceNumber}`
                : "Brouillon de facture"}
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              {invoice.client.clientName} · {formatDate(invoice.invoiceDate)}
            </p>
            {invoice.customerOrderReference && (
              <p className="text-sm text-slate-500 mt-1">
                Réf. commande : {invoice.customerOrderReference}
              </p>
            )}
          </div>
          {invoice.pdfAvailable ? (
            <InvoiceDocumentPreview key={invoice.id} invoiceId={invoice.id} />
          ) : (
            <div className="bg-white p-4 md:p-6">
              <h2 className="font-semibold text-slate-900 mb-2">
                Récapitulatif
              </h2>
              <p className="text-sm text-slate-500 mb-6">
                {invoice.status === "DRAFT"
                  ? "Le PDF définitif sera disponible après validation, avec le modèle de facture de l’entreprise."
                  : "Cette facture ne dispose pas de PDF enregistré."}
              </p>
              {/* Lines Table */}
              <div className="mb-6 overflow-x-auto">
                {invoice.lines.length === 0 ? (
                  <div className="text-center p-12 border-2 border-dashed border-slate-200 rounded-lg text-slate-400">
                    Ce brouillon ne contient aucune ligne pour le moment.
                  </div>
                ) : (
                  <table className="w-full min-w-[620px] text-sm text-left">
                    <thead className="border-b-2 border-slate-900 text-slate-900">
                      <tr>
                        <th className="py-3 font-bold">Désignation</th>
                        <th className="py-3 font-bold text-right">Qté</th>
                        <th className="py-3 font-bold text-right">PU HT</th>
                        <th className="py-3 font-bold text-right">Remise</th>
                        <th className="py-3 font-bold text-right">TVA</th>
                        <th className="py-3 font-bold text-right">Total HT</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 text-slate-700">
                      {invoice.lines.map((line) => (
                        <tr key={line.id}>
                          <td className="py-4">
                            <p className="font-bold text-slate-900">
                              {line.productName}
                            </p>
                            <p className="text-xs text-slate-500 mt-0.5">
                              Réf: {line.productCode} ·{" "}
                              {formatProductUnit(line.unit)}
                            </p>
                          </td>
                          <td className="py-4 text-right tabular-nums">
                            {line.quantity.toFixed(3)}
                          </td>
                          <td className="py-4 text-right tabular-nums">
                            {formatMoney(line.unitPriceExcludingTax)}
                          </td>
                          <td className="py-4 text-right tabular-nums">
                            {line.discountRate > 0 ? (
                              <span>
                                {line.discountRate.toFixed(2)}%<br />
                                <span className="text-xs text-slate-400">
                                  {formatMoney(line.discountAmount)}
                                </span>
                              </span>
                            ) : (
                              "—"
                            )}
                          </td>
                          <td className="py-4 text-right tabular-nums">
                            {line.vatRate.toFixed(2)}%<br />
                            <span className="text-xs text-slate-400">
                              {formatMoney(line.vatAmount)}
                            </span>
                          </td>
                          <td className="py-4 text-right tabular-nums font-medium text-slate-900">
                            {formatMoney(line.netAmountExcludingTax)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              {/* Totals Box */}
              <div className="flex justify-end">
                <div className="w-full max-w-sm bg-slate-50 p-6 rounded-lg">
                  <div className="space-y-3 text-sm">
                    <div className="flex justify-between text-slate-600">
                      <span>Sous-total HT</span>
                      <span className="tabular-nums">
                        {formatMoney(invoice.subtotalExcludingTax)}
                      </span>
                    </div>
                    {invoice.totalDiscount > 0 && (
                      <div className="flex justify-between text-slate-600">
                        <span>Remise globale</span>
                        <span className="tabular-nums text-emerald-600">
                          − {formatMoney(invoice.totalDiscount)}
                        </span>
                      </div>
                    )}
                    <div className="flex justify-between text-slate-600">
                      <span>TVA Globale</span>
                      <span className="tabular-nums">
                        {formatMoney(invoice.totalVat)}
                      </span>
                    </div>
                    <div className="pt-4 mt-2 border-t border-slate-200 flex justify-between items-center">
                      <span className="font-bold text-slate-900 text-base">
                        Net à payer TTC
                      </span>
                      <span className="font-black text-slate-900 text-xl tabular-nums tracking-tight">
                        {formatMoney(invoice.totalIncludingTax)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* SIDEBAR (Right - 30%) */}
        <div className="w-full lg:w-80 shrink-0 space-y-6">
          {/* Main Actions Card */}
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-6 shadow-sm">
            <h3 className="font-bold text-slate-900 mb-4">Actions</h3>
            <div className="space-y-3">
              {/* PRIMARY CTA (Validate) */}
              {canManage && invoice.status === "DRAFT" && (
                <button
                  className="w-full py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 transition-colors shadow-sm flex items-center justify-center gap-2"
                  onClick={() => setAction("validate")}
                >
                  <Icon name="status" size={16} /> Valider la facture
                </button>
              )}

              {/* Mark as Paid CTA */}
              {canManage && invoice.status === "VALIDATED" && (
                <button
                  className="w-full py-3 bg-emerald-600 text-white rounded-lg font-medium hover:bg-emerald-700 transition-colors shadow-sm flex items-center justify-center gap-2"
                  onClick={() => setAction("paid")}
                >
                  <Icon name="status" size={16} /> Marquer comme payée
                </button>
              )}

              {/* SECONDARY CTA (Edit) */}
              {canManage && invoice.status === "DRAFT" && (
                <Link
                  to={`/invoices/${id}/edit`}
                  className="w-full py-3 bg-white border border-slate-300 text-slate-700 rounded-lg font-medium hover:bg-slate-50 transition-colors flex items-center justify-center gap-2"
                >
                  <Icon name="edit" size={16} /> Modifier le brouillon
                </Link>
              )}

              {/* Download PDF */}
              {invoice.pdfAvailable && (
                <button
                  className="w-full py-3 bg-white border border-slate-300 text-slate-700 rounded-lg font-medium hover:bg-slate-50 transition-colors flex items-center justify-center gap-2"
                  disabled={download.isPending}
                  onClick={getPdf}
                >
                  <Icon name="download" size={16} />
                  {download.isPending ? "Téléchargement…" : "Télécharger PDF"}
                </button>
              )}

              {/* DESTRUCTIVE ACTION (Cancel) at bottom */}
              {canManage &&
                (invoice.status === "DRAFT" ||
                  invoice.status === "VALIDATED") && (
                  <div className="pt-4 mt-4 border-t border-slate-200 text-center">
                    <button
                      className="text-xs font-medium text-slate-400 hover:text-red-600 transition-colors underline underline-offset-2"
                      onClick={() => setAction("cancel")}
                    >
                      Annuler la facture (Irréversible)
                    </button>
                  </div>
                )}
            </div>
          </div>

          <InvoiceFinancePanel
            key={invoice.id}
            invoice={invoice}
            canManage={canManage}
          />

          {/* Traceability Metadata */}
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-6 shadow-sm">
            <h3 className="font-bold text-slate-900 mb-4">Traçabilité</h3>
            <div className="space-y-4 text-sm">
              <div>
                <p className="text-slate-500 font-medium text-xs uppercase tracking-wider mb-1">
                  Création
                </p>
                <p className="text-slate-900">
                  {formatDateTime(invoice.createdAt)}
                </p>
                <p className="text-slate-500 text-xs mt-0.5">
                  Par {invoice.createdByName ?? "Utilisateur indisponible"}
                </p>
              </div>
              {invoice.updatedAt !== invoice.createdAt && (
                <div>
                  <p className="text-slate-500 font-medium text-xs uppercase tracking-wider mb-1">
                    Dernière modification
                  </p>
                  <p className="text-slate-900">
                    {formatDateTime(invoice.updatedAt)}
                  </p>
                  <p className="text-slate-500 text-xs mt-0.5">
                    Par {invoice.updatedByName ?? "Utilisateur indisponible"}
                  </p>
                </div>
              )}
              {invoice.validatedAt && (
                <div>
                  <p className="text-slate-500 font-medium text-xs uppercase tracking-wider mb-1">
                    Validation
                  </p>
                  <p className="text-slate-900">
                    {formatDateTime(invoice.validatedAt)}
                  </p>
                  <p className="text-slate-500 text-xs mt-0.5">
                    Par {invoice.validatorName ?? "Utilisateur indisponible"}
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <InvoiceActionDialog
        open={Boolean(action)}
        title={dialog.title}
        description={dialog.description}
        confirmLabel={dialog.label}
        danger={dialog.danger}
        pending={pending}
        onClose={() => setAction(null)}
        onConfirm={confirm}
      />
    </section>
  );
}
