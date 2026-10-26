import { Link, useNavigate } from "react-router-dom";
import { ClientForm } from "../components/ClientForm";
import { useCreateClient } from "../hooks/useClients";
import type { ClientInput } from "../types/client";
import { PageHeader } from "../../../components/ui/PageHeader";

export function ClientCreatePage() {
  const mutation = useCreateClient();
  const navigate = useNavigate();
  async function submit(input: ClientInput) {
    const client = await mutation.mutateAsync(input);
    navigate(`/clients/${client.id}`, {
      replace: true,
      state: { toast: "Client créé avec succès." },
    });
  }
  return (
    <section className="wide-form-page" data-testid="client-create-page">
      <PageHeader
        eyebrow="Ventes"
        title="Nouveau client"
        description="Créez une fiche client pour préparer vos prochaines factures."
        breadcrumbs={[
          { label: "Clients", to: "/clients" },
          { label: "Nouveau client" },
        ]}
        action={
          <Link className="button-link secondary-link" to="/clients">
            Retour à la liste
          </Link>
        }
      />
      <ClientForm onSubmit={submit} />
    </section>
  );
}
