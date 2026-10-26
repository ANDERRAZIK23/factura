import { Link, useNavigate } from "react-router-dom";
import { ProductForm } from "../components/ProductForm";
import { useCreateProduct } from "../hooks/useProducts";
import type { ProductInput } from "../types/product";
import { PageHeader } from "../../../components/ui/PageHeader";

export function ProductCreatePage() {
  const mutation = useCreateProduct();
  const navigate = useNavigate();
  async function submit(input: ProductInput) {
    const product = await mutation.mutateAsync(input);
    navigate(`/products/${product.id}`, {
      replace: true,
      state: { toast: "Produit créé avec succès." },
    });
  }
  return (
    <section className="wide-form-page" data-testid="product-create-page">
      <PageHeader
        eyebrow="Catalogue"
        title="Nouveau produit"
        description="Ajoutez un produit ou un service utilisable dans les factures."
        breadcrumbs={[
          { label: "Produits et services", to: "/products" },
          { label: "Nouveau produit" },
        ]}
        action={
          <Link className="button-link secondary-link" to="/products">
            Retour à la liste
          </Link>
        }
      />
      <ProductForm onSubmit={submit} />
    </section>
  );
}
