import { useEffect, useRef } from "react";
import type { ProductSummary } from "../types/product";

export function ProductStatusDialog({
  product,
  pending,
  onConfirm,
  onClose,
}: {
  product: ProductSummary | null;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (product) dialog.current?.showModal();
    else dialog.current?.close();
  }, [product]);
  return (
    <dialog
      ref={dialog}
      onCancel={onClose}
      aria-labelledby="product-status-dialog-title"
    >
      <h2 id="product-status-dialog-title">
        {product?.active ? "Désactiver" : "Activer"} le produit ?
      </h2>
      <p>{product ? `${product.productCode} — ${product.productName}` : ""}</p>
      <p className="muted">
        {product?.active
          ? "Le produit restera dans le catalogue, mais ne pourra plus être sélectionné comme actif."
          : "Le produit sera de nouveau disponible pour les prochaines opérations commerciales."}
      </p>
      <div className="dialog-actions">
        <button
          className="secondary"
          type="button"
          onClick={onClose}
          disabled={pending}
        >
          Annuler
        </button>
        <button
          data-testid="confirm-product-status"
          type="button"
          onClick={onConfirm}
          disabled={pending}
        >
          {pending ? "Enregistrement…" : "Confirmer"}
        </button>
      </div>
    </dialog>
  );
}
