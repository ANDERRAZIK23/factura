import { Link, useNavigate, useParams } from "react-router-dom";
import { ProductForm } from "../components/ProductForm";
import { useProduct, useUpdateProduct } from "../hooks/useProducts";
import type { ProductInput } from "../types/product";
import { PageHeader } from "../../../components/ui/PageHeader";

export function ProductEditPage() {
  const { id = "" } = useParams();
  const query = useProduct(id);
  const mutation = useUpdateProduct(id);
  const navigate = useNavigate();
  if (query.isPending)
    return (
      <div className="loading-state" role="status">
        Chargement du produit…
      </div>
    );
  if (query.isError || !query.data)
    return (
      <section className="empty-state">
        <h1>Impossible de modifier le produit</h1>
        <Link to="/products">Retour aux produits</Link>
      </section>
    );
  async function submit(input: ProductInput) {
    const product = await mutation.mutateAsync({
      ...input,
      version: query.data!.version,
    });
    navigate(`/products/${product.id}`, {
      replace: true,
      state: { toast: "Produit mis à jour avec succès." },
    });
  }
  return (
    <section className="wide-form-page" data-testid="product-edit-page">
      <PageHeader
        eyebrow="Gestion du catalogue"
        title="Modifier l’article"
        description="Mettez à jour le catalogue sans altérer les factures existantes."
        breadcrumbs={[
          { label: "Produits et services", to: "/products" },
          { label: query.data.productName, to: `/products/${id}` },
          { label: "Modifier" },
        ]}
        action={
          <Link className="button-link secondary-link" to={`/products/${id}`}>
            Retour à la fiche
          </Link>
        }
      />
      <ProductForm
        product={query.data}
        onSubmit={submit}
        onReload={async () => {
          await query.refetch();
        }}
      />
    </section>
  );
}
