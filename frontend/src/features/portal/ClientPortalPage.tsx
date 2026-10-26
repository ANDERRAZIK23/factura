import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  formatDate,
  formatMoney,
  formatPaymentMethod,
} from "../../utils/format";
import { InvoiceStatusBadge } from "../invoices/components/InvoiceStatusBadge";
import { InvoicePdfPreview } from "../invoice-designs/InvoicePdfPreview";
import { savePdf } from "../sales/salesApi";
import { ActionError } from "../sales/SalesUi";
import { portalApi, type PortalInvoice, type PortalPayment } from "./portalApi";
import "../sales/sales.css";
import "./portal.css";

export function ClientPortalPage() {
  const [token, setToken] = useState("");
  const [ready, setReady] = useState(false);
  const [storageError, setStorageError] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<PortalInvoice | null>(null);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [payments, setPayments] = useState<PortalPayment[]>([]);
  const cache = useQueryClient();
  const accessEpoch = useRef(0);
  const documentHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (selected) documentHeading.current?.focus();
  }, [selected]);
  useEffect(() => {
    try {
      const fragment = window.location.hash.slice(1);
      if (fragment) {
        sessionStorage.setItem("client-portal-token", fragment);
        window.history.replaceState(null, "", window.location.pathname);
      }
      setToken(fragment || sessionStorage.getItem("client-portal-token") || "");
    } catch {
      setStorageError(
        "Le stockage de session est indisponible. Autorisez-le puis rouvrez votre lien client.",
      );
    }
    setReady(true);
  }, []);
  const query = useQuery({
    queryKey: ["client-portal", token, page],
    queryFn: () => portalApi.list(token, page),
    enabled: Boolean(token),
    retry: false,
    gcTime: 0,
  });
  const document = useMutation({
    gcTime: 0,
    mutationFn: async (invoice: PortalInvoice) => {
      const [pdf, entries] = await Promise.all([
        portalApi.pdf(token, invoice.id),
        portalApi.payments(token, invoice.id),
      ]);
      return { pdf, entries, invoice };
    },
    onMutate: () => {
      setBlob(null);
      setSelected(null);
      setPayments([]);
      return accessEpoch.current;
    },
    onSuccess: (value, _invoice, epoch) => {
      if (epoch !== accessEpoch.current) return;
      setSelected(value.invoice);
      setBlob(value.pdf);
      setPayments(value.entries);
    },
  });
  function close() {
    accessEpoch.current += 1;
    try {
      sessionStorage.removeItem("client-portal-token");
    } catch {
      setStorageError(
        "Fermez cet onglet pour terminer l’accès : le stockage de session est indisponible.",
      );
    }
    setToken("");
    setSelected(null);
    setBlob(null);
    setPayments([]);
    cache.removeQueries({ queryKey: ["client-portal"] });
    document.reset();
  }
  return (
    <main className="portal-page">
      <div className="portal-inner">
        <header className="portal-heading">
          <div>
            <span className="portal-eyebrow">Espace client</span>
            <h1>{query.data?.companyName ?? "Vos factures"}</h1>
            <p className="sales-muted">
              {query.data?.clientName ??
                "Consultez vos documents et le suivi de vos règlements."}
            </p>
          </div>
          {token && (
            <button className="secondary" type="button" onClick={close}>
              Fermer cet espace
            </button>
          )}
        </header>
        {storageError && (
          <p role="alert" className="form-error">
            {storageError}
          </p>
        )}
        {ready && !token && !storageError && (
          <div className="sales-card portal-state">
            <h2>Bienvenue dans votre espace client</h2>
            <p>
              Ouvrez le lien personnel transmis par votre fournisseur pour
              accéder à vos factures.
            </p>
          </div>
        )}
        {token && query.isPending && (
          <p role="status" className="sales-state">
            Chargement de vos factures…
          </p>
        )}
        {query.isError && (
          <div className="sales-card portal-state" role="alert">
            <h2>Accès indisponible</h2>
            <p>
              Ce lien est invalide, expiré ou indisponible. Demandez un nouveau
              lien à votre fournisseur.
            </p>
            <button className="secondary" onClick={() => void query.refetch()}>
              Réessayer
            </button>
          </div>
        )}
        {query.data && !query.isError && (
          <>
            <div className="portal-balance">
              <div>
                <span>Solde total restant à régler</span>
                <strong>{formatMoney(query.data.totalOutstanding)}</strong>
              </div>
              <p>
                Retrouvez le détail de chaque facture et les règlements
                enregistrés ci-dessous.
              </p>
            </div>
            <section
              className="sales-card portal-invoices"
              aria-labelledby="portal-invoices-title"
            >
              <div className="portal-section-heading">
                <div>
                  <h2 id="portal-invoices-title">Mes factures</h2>
                  <p className="sales-muted">
                    Consultez vos documents et téléchargez leur version PDF.
                  </p>
                </div>
              </div>
              {query.data.invoices.length === 0 ? (
                <p>Aucune facture émise pour le moment.</p>
              ) : (
                <div className="sales-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Facture</th>
                        <th>Échéance</th>
                        <th className="numeric">TTC</th>
                        <th className="numeric">Reçu</th>
                        <th className="numeric">Restant</th>
                        <th>Statut</th>
                        <th>Document</th>
                      </tr>
                    </thead>
                    <tbody>
                      {query.data.invoices.map((i) => (
                        <tr
                          key={i.id}
                          className={
                            selected?.id === i.id ? "is-selected" : undefined
                          }
                        >
                          <td>
                            <strong>{i.invoiceNumber}</strong>
                            <small>{formatDate(i.invoiceDate)}</small>
                          </td>
                          <td data-label="Échéance">
                            {i.dueDate
                              ? formatDate(i.dueDate)
                              : "Non renseignée"}
                          </td>
                          <td className="numeric" data-label="Total TTC">
                            {formatMoney(i.totalIncludingTax)}
                          </td>
                          <td className="numeric" data-label="Reçu">
                            {formatMoney(i.paidAmount)}
                          </td>
                          <td
                            className="numeric portal-outstanding"
                            data-label="Restant"
                          >
                            {formatMoney(i.outstandingAmount)}
                          </td>
                          <td data-label="Statut">
                            <InvoiceStatusBadge status={i.status} />
                          </td>
                          <td className="portal-document-action">
                            <button
                              className="secondary"
                              disabled={!i.pdfAvailable || document.isPending}
                              aria-label={`Consulter le PDF ${i.invoiceNumber}`}
                              onClick={() => document.mutate(i)}
                            >
                              Consulter le PDF
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <nav
                className="sales-pagination"
                aria-label="Pagination des factures"
              >
                <button
                  className="secondary"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  Précédente
                </button>
                <span aria-live="polite">Page {page + 1}</span>
                <button
                  className="secondary"
                  disabled={!query.data.hasNext}
                  onClick={() => setPage(page + 1)}
                >
                  Suivante
                </button>
              </nav>
            </section>
            {document.isPending && (
              <p role="status">Ouverture de la facture…</p>
            )}
            <ActionError error={document.error} />
            {selected && blob && (
              <section
                className="sales-card portal-selected"
                aria-labelledby="portal-document-title"
              >
                <div className="portal-section-heading">
                  <div>
                    <span className="portal-eyebrow">Votre document</span>
                    <h2
                      id="portal-document-title"
                      tabIndex={-1}
                      ref={documentHeading}
                    >
                      {selected.invoiceNumber}
                    </h2>
                    <p className="sales-muted">
                      Émise le {formatDate(selected.invoiceDate)} ·{" "}
                      {formatMoney(selected.totalIncludingTax)} TTC
                    </p>
                  </div>
                  <div className="sales-actions">
                    <button
                      onClick={() =>
                        savePdf(blob, `${selected.invoiceNumber}.pdf`)
                      }
                    >
                      Télécharger le PDF
                    </button>
                    <button
                      className="secondary"
                      onClick={() => {
                        setSelected(null);
                        setBlob(null);
                        setPayments([]);
                      }}
                    >
                      Fermer le document
                    </button>
                  </div>
                </div>
                <div className="portal-payment-summary">
                  <div>
                    <span>Montant reçu</span>
                    <strong>{formatMoney(selected.paidAmount)}</strong>
                  </div>
                  <div>
                    <span>Solde restant</span>
                    <strong>{formatMoney(selected.outstandingAmount)}</strong>
                  </div>
                </div>
                <h3>Règlements enregistrés</h3>
                {payments.length ? (
                  <ul className="portal-payments">
                    {payments.map((p, index) => (
                      <li
                        key={index}
                        className={p.reversedAt ? "is-reversed" : undefined}
                      >
                        <div>
                          <strong>{formatMoney(p.amount)}</strong>
                          <span>{formatPaymentMethod(p.paymentMethod)}</span>
                        </div>
                        <span>
                          {p.paymentDate
                            ? formatDate(p.paymentDate)
                            : "Date non renseignée"}{" "}
                          {p.reversedAt && " · Saisie annulée"}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="sales-muted">Aucun règlement enregistré.</p>
                )}
                <div className="portal-document">
                  <InvoicePdfPreview
                    key={selected.id}
                    blob={blob}
                    title={`PDF ${selected.invoiceNumber}`}
                    scrollable={false}
                  />
                </div>
              </section>
            )}
          </>
        )}
        <footer className="portal-footer">
          Vos documents et votre suivi de règlement dans un espace personnel.
        </footer>
      </div>
    </main>
  );
}
