import { useEffect, useRef } from "react";

export function InvoiceActionDialog({
  open,
  title,
  description,
  confirmLabel,
  pending,
  danger = false,
  onClose,
  onConfirm,
}: {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  danger?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (open) dialog.current?.showModal();
    else dialog.current?.close();
  }, [open]);
  return (
    <dialog
      ref={dialog}
      onCancel={onClose}
      aria-labelledby="invoice-action-title"
    >
      <h2 id="invoice-action-title">{title}</h2>
      <p className="muted">{description}</p>
      <div className="dialog-actions">
        <button
          type="button"
          className="secondary"
          disabled={pending}
          onClick={onClose}
        >
          Retour
        </button>
        <button
          type="button"
          className={danger ? "danger" : undefined}
          data-testid="confirm-invoice-action"
          disabled={pending}
          onClick={onConfirm}
        >
          {pending ? "Traitement…" : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
