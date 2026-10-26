import { useState } from "react";
import { formatDateTime } from "../../utils/format";
import { ActionError, QueryState } from "../sales/SalesUi";
import { useCompanyQuery, useSalesAction } from "../sales/salesHooks";
import { portalApi } from "./portalApi";
import "./portal.css";

export function ClientPortalAccessCard({
  clientId,
  active,
}: {
  clientId: string;
  active: boolean;
}) {
  const query = useCompanyQuery(["portal-access", clientId], () =>
    portalApi.info(clientId),
  );
  const create = useSalesAction((days: number) =>
    portalApi.create(clientId, days),
  );
  const revoke = useSalesAction(() => portalApi.revoke(clientId));
  const [days, setDays] = useState(30);
  const [url, setUrl] = useState("");
  const [copied, setCopied] = useState("");
  const [confirmRevoke, setConfirmRevoke] = useState(false);
  const busy = create.isPending || revoke.isPending;
  async function copyLink() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied("Lien copié.");
    } catch {
      setCopied("Sélectionnez le lien pour le copier manuellement.");
    }
  }
  return (
    <section
      className="sales-card sales-form portal-access"
      aria-labelledby="portal-access-title"
    >
      <h2 id="portal-access-title">Portail client</h2>
      <p className="sales-muted">
        Le lien donne accès aux factures émises et aux règlements de ce client.
        Transmettez-le à son destinataire.
      </p>
      <QueryState query={query} />
      {query.data && (
        <p
          className={`portal-access-status${query.data.active && active ? " is-active" : ""}`}
        >
          <strong>
            {!active
              ? "Accès suspendu"
              : query.data.active
                ? "Accès actif"
                : "Aucun accès actif"}
          </strong>
          {query.data.active && (
            <span>
              Valable jusqu’au {formatDateTime(query.data.expiresAt!)}
            </span>
          )}
          {!active && (
            <span>
              Réactivez le client pour lui permettre de consulter son portail.
            </span>
          )}
        </p>
      )}
      <label>
        Durée de validité
        <select
          disabled={busy || !active}
          value={days}
          onChange={(e) => setDays(Number(e.target.value))}
        >
          <option value={7}>7 jours</option>
          <option value={30}>30 jours</option>
          <option value={90}>90 jours</option>
        </select>
      </label>
      <button
        type="button"
        disabled={!active || busy || query.isPending || query.isError}
        onClick={() =>
          create.mutate(days, {
            onSuccess: (value) => {
              setUrl(`${window.location.origin}/portal#${value.token}`);
              setCopied("");
            },
          })
        }
      >
        {query.data?.active
          ? "Remplacer le lien client"
          : "Créer un lien client"}
      </button>
      {query.data?.active && (
        <p className="sales-muted">
          Remplacer le lien désactive immédiatement le précédent.
        </p>
      )}
      {url && (
        <div className="portal-link-created">
          <label>
            Lien à transmettre
            <input
              readOnly
              value={url}
              onFocus={(e) => e.currentTarget.select()}
            />
          </label>
          <button
            className="secondary"
            type="button"
            onClick={() => void copyLink()}
          >
            Copier le lien
          </button>
          {copied && <p role="status">{copied}</p>}
          <p className="sales-muted">
            Le lien complet est affiché uniquement lors de sa création.
          </p>
        </div>
      )}
      {query.data?.active && (
        <div className="sales-actions">
          <button
            className="secondary"
            type="button"
            disabled={busy}
            onClick={() => setConfirmRevoke(true)}
          >
            Révoquer l’accès
          </button>
        </div>
      )}
      {confirmRevoke && (
        <div role="group" aria-label="Confirmer la révocation">
          <p>Le lien ne permettra plus de consulter le portail.</p>
          <div className="sales-actions">
            <button
              className="danger"
              disabled={busy}
              type="button"
              onClick={() =>
                revoke.mutate(undefined, {
                  onSuccess: () => {
                    setUrl("");
                    setConfirmRevoke(false);
                  },
                })
              }
            >
              Confirmer la révocation
            </button>
            <button
              className="secondary"
              type="button"
              disabled={busy}
              onClick={() => setConfirmRevoke(false)}
            >
              Retour
            </button>
          </div>
        </div>
      )}
      <ActionError error={create.error ?? revoke.error} />
    </section>
  );
}
