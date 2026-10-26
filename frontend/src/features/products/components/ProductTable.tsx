import { Link } from "react-router-dom";
import {
  formatDate,
  formatMoney,
  formatProductUnit,
} from "../../../utils/format";
import type { ProductSummary } from "../types/product";
import { ProductStatusBadge } from "./ProductStatusBadge";
import { Icon } from "../../../components/ui/Icon";

export function ProductTable({
  products,
  canManage,
  onStatus,
}: {
  products: ProductSummary[];
  canManage: boolean;
  onStatus: (product: ProductSummary) => void;
}) {
  return (
    <div className="table-wrap product-table">
      <table>
        <caption className="sr-only">Produits de la page sélectionnée</caption>
        <thead>
          <tr>
            <th scope="col">Produit / Service</th>
            <th scope="col">Type / unité</th>
            <th scope="col" className="numeric-column">
              Prix HT
            </th>
            <th scope="col" className="numeric-column">
              TVA
            </th>
            <th scope="col" className="numeric-column">
              Aperçu TTC
            </th>
            <th scope="col">Statut</th>
            <th scope="col" className="numeric-column">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {products.map((product) => {
            const isService = product.unit === "SERVICE";
            return (
              <tr key={product.id}>
                <td data-label="Produit / Service">
                  <div className="entity-cell">
                    <div
                      className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 border ${
                        isService
                          ? "bg-purple-50 text-purple-600 border-purple-100"
                          : "bg-indigo-50 text-indigo-600 border-indigo-100"
                      }`}
                    >
                      <Icon
                        name={isService ? "trend-up" : "product"}
                        size={18}
                      />
                    </div>
                    <div className="list-cell-copy">
                      <Link
                        to={`/products/${product.id}`}
                        className="font-bold text-slate-900 hover:text-indigo-600 transition-colors"
                      >
                        {product.productName}
                      </Link>
                      <div className="flex items-center gap-1.5 text-xs text-slate-500 font-medium mt-0.5">
                        <span className="font-mono">{product.productCode}</span>
                        <span>•</span>
                        <span>Ajouté le {formatDate(product.createdAt)}</span>
                      </div>
                    </div>
                  </div>
                </td>
                <td data-label="Unité">
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                        isService
                          ? "bg-purple-50 text-purple-700 border border-purple-200/60"
                          : "bg-slate-100 text-slate-700 border border-slate-200"
                      }`}
                    >
                      {isService ? "Service" : "Produit"}
                    </span>
                    <span className="text-xs text-slate-500 font-medium">
                      ({formatProductUnit(product.unit)})
                    </span>
                  </div>
                </td>
                <td data-label="Prix HT" className="numeric-cell">
                  {formatMoney(product.unitPriceExcludingTax)}
                </td>
                <td data-label="TVA" className="numeric-cell">
                  {product.defaultVatRate.toFixed(2)}%
                </td>
                <td data-label="Aperçu TTC" className="numeric-cell">
                  <strong className="text-slate-900 text-base">
                    {formatMoney(product.unitPriceIncludingTax)}
                  </strong>
                </td>
                <td data-label="Statut">
                  <ProductStatusBadge active={product.active} />
                </td>
                <td data-label="Actions" className="action-cell">
                  <div className="row-actions">
                    <Link
                      to={`/products/${product.id}`}
                      className="button-link secondary-link compact-button"
                      title="Voir la fiche"
                      aria-label="Voir"
                    >
                      <Icon name="eye" size={16} />
                    </Link>
                    {canManage && (
                      <>
                        <Link
                          to={`/products/${product.id}/edit`}
                          className="button-link secondary-link compact-button"
                          title="Modifier l'article"
                          aria-label="Modifier"
                        >
                          <Icon name="edit" size={16} />
                        </Link>
                        <button
                          data-testid={`product-status-${product.id}`}
                          type="button"
                          onClick={() => onStatus(product)}
                          className={`secondary compact-button ${
                            product.active
                              ? "text-slate-500 hover:text-red-600 hover:bg-red-50"
                              : "text-slate-500 hover:text-emerald-600 hover:bg-emerald-50"
                          }`}
                          aria-label={product.active ? "Désactiver" : "Activer"}
                          title={
                            product.active
                              ? "Désactiver l'article"
                              : "Activer l'article"
                          }
                        >
                          <Icon name="status" size={16} />
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
