import { InvoicePdfPreview } from "../../invoice-designs/InvoicePdfPreview";
import { useInvoicePdf } from "../hooks/useInvoices";

export function InvoiceDocumentPreview({ invoiceId }: { invoiceId: string }) {
  const pdf = useInvoicePdf(invoiceId);

  if (pdf.isPending) {
    return (
      <div
        className="min-h-96 grid place-content-center text-sm text-slate-500"
        role="status"
      >
        Chargement du PDF de la facture…
      </div>
    );
  }

  if (pdf.isError) {
    return (
      <div className="min-h-96 flex flex-col items-center justify-center gap-4 p-6 text-center">
        <p role="alert" className="text-sm text-red-700">
          Impossible de charger le PDF de cette facture.
        </p>
        <button
          type="button"
          className="secondary"
          onClick={() => void pdf.refetch()}
        >
          Réessayer
        </button>
      </div>
    );
  }

  return (
    <InvoicePdfPreview
      blob={pdf.data.blob}
      title="PDF de la facture"
      scrollable={false}
    />
  );
}
