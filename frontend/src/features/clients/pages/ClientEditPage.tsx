import { Link, useNavigate, useParams } from "react-router-dom";
import { ClientForm } from "../components/ClientForm";
import { useClient, useUpdateClient } from "../hooks/useClients";
import type { ClientInput } from "../types/client";
import { PageHeader } from "../../../components/ui/PageHeader";

export function ClientEditPage() {
  const { id = "" } = useParams();
  const query = useClient(id);
  const mutation = useUpdateClient(id);
  const navigate = useNavigate();
  if (query.isPending)
    return (
      <div className="loading-state" role="status">
        Chargement du client…
      </div>
    );
  if (query.isError || !query.data)
    return (
      <section className="empty-state" role="alert">
        <h1>Impossible de modifier le client</h1>
        <Link to="/clients">Retour aux clients</Link>
      </section>
    );
  async function submit(input: ClientInput) {
    const client = await mutation.mutateAsync({
      ...input,
      version: query.data!.version,
    });
    navigate(`/clients/${client.id}`, {
      replace: true,
      state: { toast: "Client mis à jour avec succès." },
    });
  }
  return (
    <section className="narrow-page" data-testid="client-edit-page">
      <PageHeader
        eyebrow="Gestion des clients"
        title="Modifier le client"
        description="Mettez à jour les coordonnées sans modifier l’historique des factures."
        breadcrumbs={[
          { label: "Clients", to: "/clients" },
          { label: query.data.clientName, to: `/clients/${id}` },
          { label: "Modifier" },
        ]}
        action={
          <Link className="button-link secondary-link" to={`/clients/${id}`}>
            Retour à la fiche
          </Link>
        }
      />
      <ClientForm
        client={query.data}
        onSubmit={submit}
        onReload={async () => {
          await query.refetch();
        }}
      />
    </section>
  );
}
