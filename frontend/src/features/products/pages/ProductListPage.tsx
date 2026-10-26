import { EmptyState } from "../../../components/ui/EmptyState";
import { ListPagination } from "../../../components/ui/ListPagination";
import axios from "axios";
import { useState, useMemo } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { useUrlPagination } from "../../../utils/useUrlPagination";
import { useDebouncedSearch } from "../../../utils/useDebouncedSearch";
import { Skeleton } from "../../../components/ui/Skeleton";
import { ProductFilters } from "../components/ProductFilters";
import { ProductStatusDialog } from "../components/ProductStatusDialog";
import { ProductTable } from "../components/ProductTable";
import { useProducts, useUpdateProductStatus } from "../hooks/useProducts";
import {
  PRODUCT_UNITS,
  type ProblemDetails,
  type ProductSummary,
  type ProductUnit,
} from "../types/product";
import { Icon } from "../../../components/ui/Icon";
import { MetricCard } from "../../../components/ui/MetricCard";
import { PageHeader } from "../../../components/ui/PageHeader";

export function ProductListPage() {
  const { user } = useAuth();
  const canManage =
    user?.role === "APP_ADMIN" ||
    user?.role === "ADMIN" ||
    user?.role === "INVOICE_MANAGER";

  const { page, size, sort, updateParam, setPage, param, resetFilters } =
    useUrlPagination({
      sort: "productName,asc",
    });
  const { search, setSearch, activeSearch } = useDebouncedSearch();
  const [statusProduct, setStatusProduct] = useState<ProductSummary | null>(
    null,
  );
  const [statusError, setStatusError] = useState("");

  const activeValue = param("active");
  const unitValue = param("unit");
  const minPrice = param("minPrice");
  const maxPrice = param("maxPrice");

  const query = useProducts({
    page,
    size,
    sort,
    search: activeSearch,
    active: activeValue === "" ? undefined : activeValue === "true",
    unit: PRODUCT_UNITS.includes(unitValue as ProductUnit)
      ? (unitValue as ProductUnit)
      : undefined,
    minPrice: minPrice === "" ? undefined : Number(minPrice),
    maxPrice: maxPrice === "" ? undefined : Number(maxPrice),
  });

  const hasFilters = Boolean(
    activeSearch || activeValue || unitValue || minPrice || maxPrice,
  );

  const statusMutation = useUpdateProductStatus();

  // Calculate quick stats from current data
  const { activeCount, serviceCount } = useMemo(() => {
    if (!query.data) return { activeCount: 0, serviceCount: 0 };
    let active = 0;
    let service = 0;
    query.data.content.forEach((item) => {
      if (item.active) active++;
      if (item.unit === "SERVICE") service++;
    });
    return { activeCount: active, serviceCount: service };
  }, [query.data]);

  const metricDetail = query.isPending
    ? "Chargement de l’indicateur…"
    : query.isError
      ? "Indicateur indisponible"
      : "Articles de la page courante";

  async function confirmStatus() {
    if (!statusProduct) return;
    setStatusError("");
    try {
      await statusMutation.mutateAsync({
        id: statusProduct.id,
        active: !statusProduct.active,
        version: statusProduct.version,
      });
      setStatusProduct(null);
    } catch (error) {
      const problem = axios.isAxiosError<ProblemDetails>(error)
        ? error.response?.data
        : undefined;
      setStatusError(
        problem?.detail ?? "Impossible de modifier le statut du produit.",
      );
      setStatusProduct(null);
    }
  }

  return (
    <section data-testid="products-page" className="list-page">
      <PageHeader
        eyebrow="Gestion commerciale"
        title="Produits et services"
        description="Gérez les prix, unités et disponibilités de votre catalogue commercial."
        action={
          canManage && (
            <Link
              className="button-link"
              data-testid="create-product"
              to="/products/new"
            >
              <Icon name="plus" size={16} /> Nouveau produit
            </Link>
          )
        }
      />

      <div className="metric-grid list-metrics">
        <MetricCard
          label="Articles correspondants"
          value={
            query.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : query.isError ? (
              "—"
            ) : (
              (query.data?.totalElements.toLocaleString("fr-FR") ?? "—")
            )
          }
          detail={
            query.isPending || query.isError
              ? metricDetail
              : "Selon les filtres en cours"
          }
          icon="product"
          tone="accent"
        />
        <MetricCard
          label="Articles actifs"
          value={
            query.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : query.isError ? (
              "—"
            ) : (
              `${activeCount} / ${query.data?.content.length ?? 0}`
            )
          }
          detail={metricDetail}
          icon="status"
          tone="success"
        />
        <MetricCard
          label="Prestations de services"
          value={
            query.isPending ? (
              <Skeleton width="4rem" height="1.8rem" />
            ) : query.isError ? (
              "—"
            ) : (
              serviceCount.toLocaleString("fr-FR")
            )
          }
          detail={metricDetail}
          icon="product"
        />
      </div>

      <ProductFilters
        search={search}
        active={activeValue}
        unit={unitValue}
        minPrice={minPrice}
        maxPrice={maxPrice}
        sort={sort}
        onReset={() => {
          setSearch("");
          resetFilters();
        }}
        hasFilters={Boolean(search) || hasFilters || sort !== "productName,asc"}
        onSearch={setSearch}
        onChange={updateParam}
      />

      {statusError && (
        <div className="form-error" role="alert">
          {statusError}
        </div>
      )}

      {query.isPending && (
        <div className="list-loading" role="status">
          <p className="muted">Chargement des produits…</p>
          <Skeleton width="100%" height="320px" radius="8px" />
        </div>
      )}

      {query.isError && (
        <div role="alert">
          <EmptyState
            icon="product"
            title="Impossible de charger les produits"
            description="Vérifiez votre connexion et réessayez."
            action={
              <button
                type="button"
                className="secondary"
                disabled={query.isFetching}
                onClick={() => void query.refetch()}
              >
                Réessayer
              </button>
            }
          />
        </div>
      )}
      {!query.isError && query.data?.content.length === 0 && (
        <EmptyState
          icon="product"
          title="Aucun produit trouvé"
          description={
            page > 0
              ? "Aucun résultat sur cette page. Revenez à la première page."
              : hasFilters
                ? "Modifiez votre recherche ou réinitialisez les filtres."
                : "Créez le premier produit ou service du catalogue pour commencer."
          }
          action={
            hasFilters ? (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setSearch("");
                  resetFilters();
                }}
              >
                Effacer les filtres
              </button>
            ) : (
              canManage &&
              page === 0 && (
                <Link className="button-link" to="/products/new">
                  <Icon name="plus" size={16} /> Ajouter un produit
                </Link>
              )
            )
          }
        />
      )}

      {!query.isError && query.data && query.data.content.length > 0 && (
        <div className="list-results" aria-busy={query.isFetching}>
          <div className="result-summary">
            <strong>
              {query.data.totalElements.toLocaleString("fr-FR")} produits
            </strong>
            <span>
              {query.isFetching ? "Actualisation…" : "Catalogue · Prix en MAD"}
            </span>
          </div>
          <ProductTable
            products={query.data.content}
            canManage={canManage}
            onStatus={setStatusProduct}
          />
        </div>
      )}

      {!query.isError && query.data && (
        <ListPagination
          page={query.data.page}
          size={query.data.size}
          count={query.data.content.length}
          totalElements={query.data.totalElements}
          totalPages={query.data.totalPages}
          pending={query.isFetching}
          label="produits"
          onPage={setPage}
          onSize={(value) => updateParam("size", value)}
        />
      )}

      <ProductStatusDialog
        product={statusProduct}
        pending={statusMutation.isPending}
        onClose={() => setStatusProduct(null)}
        onConfirm={confirmStatus}
      />
    </section>
  );
}
