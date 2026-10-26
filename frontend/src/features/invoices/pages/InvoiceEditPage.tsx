import axios from "axios";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { InvoiceCreateForm } from "../components/InvoiceCreateForm";
import { useInvoice, useUpdateInvoice } from "../hooks/useInvoices";
import type { InvoiceInput } from "../types/invoice";
import { PageHeader } from "../../../components/ui/PageHeader";

export function InvoiceEditPage() {
  const { id = "" } = useParams();
  const query = useInvoice(id);
  const mutation = useUpdateInvoice(id);
  const navigate = useNavigate();
  if (query.isPending)
    return (
      <div className="loading-state" role="status">
        Chargement de la facture…
      </div>
    );
  if (query.isError) {
    const notFound =
      axios.isAxiosError(query.error) && query.error.response?.status === 404;
    return (
      <section className="empty-state">
        <h1>
          {notFound
            ? "Facture introuvable"
            : "Impossible de charger la facture"}
        </h1>
        {!notFound && (
          <button onClick={() => query.refetch()}>Réessayer</button>
        )}
        <Link to="/invoices">Retour aux factures</Link>
      </section>
    );
  }
  if (query.data.status !== "DRAFT")
    return <Navigate to={`/invoices/${id}`} replace />;
  async function submit(input: InvoiceInput) {
    await mutation.mutateAsync({ ...input, version: query.data!.version });
    navigate(`/invoices/${id}`, {
      replace: true,
      state: { toast: "Facture brouillon mise à jour avec succès." },
    });
  }
  return (
    <section className="wide-form-page" data-testid="invoice-edit-page">
      <PageHeader
        eyebrow="Ventes"
        title="Modifier le brouillon"
        description="Mettez à jour les informations et vérifiez les montants avant validation."
        breadcrumbs={[
          { label: "Factures", to: "/invoices" },
          {
            label: query.data.invoiceNumber ?? "Brouillon",
            to: `/invoices/${id}`,
          },
          { label: "Modifier" },
        ]}
        action={
          <Link className="button-link secondary-link" to={`/invoices/${id}`}>
            Retour à la facture
          </Link>
        }
      />
      <InvoiceCreateForm
        invoice={query.data}
        onSubmit={submit}
        onReload={async () => {
          await query.refetch();
        }}
      />
    </section>
  );
}
