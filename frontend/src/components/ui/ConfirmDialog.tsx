import { useEffect, useId, useRef } from "react";

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  pending = false,
  danger = false,
  onClose,
  onConfirm,
}: {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  pending?: boolean;
  danger?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    if (open && !ref.current?.open) ref.current?.showModal();
    else if (!open && ref.current?.open) ref.current.close();
  }, [open]);
  return (
    <dialog ref={ref} onCancel={onClose} aria-labelledby={titleId}>
      <h2 id={titleId}>{title}</h2>
      <p className="muted">{description}</p>
      <div className="dialog-actions">
        <button
          type="button"
          className="secondary"
          disabled={pending}
          onClick={onClose}
        >
          Annuler
        </button>
        <button
          type="button"
          className={danger ? "danger" : undefined}
          disabled={pending}
          onClick={onConfirm}
        >
          {pending ? "Traitement…" : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
