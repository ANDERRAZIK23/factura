import { Link, useNavigate } from "react-router-dom";
import { InvoiceCreateForm } from "../components/InvoiceCreateForm";
import { useCreateInvoice } from "../hooks/useInvoices";
import type { InvoiceInput } from "../types/invoice";
import { PageHeader } from "../../../components/ui/PageHeader";

export function InvoiceCreatePage() {
  const mutation = useCreateInvoice();
  const navigate = useNavigate();
  async function submit(input: InvoiceInput) {
    const invoice = await mutation.mutateAsync(input);
    navigate(`/invoices/${invoice.id}`, {
      replace: true,
      state: { toast: "Facture brouillon créée avec succès." },
    });
  }
  return (
    <section className="wide-form-page" data-testid="invoice-create-page">
      <PageHeader
        eyebrow="Ventes"
        title="Créer une facture"
        description="Renseignez le client, ajoutez vos articles et vérifiez les montants."
        breadcrumbs={[
          { label: "Factures", to: "/invoices" },
          { label: "Nouvelle facture" },
        ]}
        action={
          <Link className="button-link secondary-link" to="/invoices">
            Retour à la liste
          </Link>
        }
      />
      <InvoiceCreateForm onSubmit={submit} />
    </section>
  );
}
