import { useEffect, useRef } from "react";
import type { ClientSummary } from "../types/client";

export function ClientStatusDialog({
  client,
  pending,
  onConfirm,
  onClose,
}: {
  client: ClientSummary | null;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (client) dialog.current?.showModal();
    else dialog.current?.close();
  }, [client]);
  return (
    <dialog
      ref={dialog}
      onCancel={onClose}
      aria-labelledby="status-dialog-title"
    >
      <h2 id="status-dialog-title">
        {client?.active ? "Désactiver" : "Activer"} le client ?
      </h2>
      <p>{client ? `${client.clientCode} — ${client.clientName}` : ""}</p>
      <p className="muted">
        {client?.active
          ? "Le client restera dans les données, mais ne pourra plus être utilisé comme client actif."
          : "Le client sera de nouveau disponible."}
      </p>
      <div className="dialog-actions">
        <button
          type="button"
          className="secondary"
          onClick={onClose}
          disabled={pending}
        >
          Annuler
        </button>
        <button
          type="button"
          data-testid="confirm-status"
          onClick={onConfirm}
          disabled={pending}
        >
          {pending ? "Enregistrement…" : "Confirmer"}
        </button>
      </div>
    </dialog>
  );
}
