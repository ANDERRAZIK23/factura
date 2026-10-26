import { PRODUCT_UNITS } from "../types/product";
import { formatProductUnit } from "../../../utils/format";
import { Icon } from "../../../components/ui/Icon";

export function ProductFilters({
  search,
  active,
  unit,
  minPrice,
  maxPrice,
  sort,
  onSearch,
  onChange,
  onReset,
  hasFilters,
}: {
  onReset: () => void;
  hasFilters: boolean;
  search: string;
  active: string;
  unit: string;
  minPrice: string;
  maxPrice: string;
  sort: string;
  onSearch: (value: string) => void;
  onChange: (name: string, value: string) => void;
}) {
  return (
    <div className="list-filters">
      <div className="list-filter-heading">
        <span>
          <Icon name="filter" size={16} /> Recherche et filtres
        </span>
        <button
          type="button"
          className="link-button"
          onClick={onReset}
          disabled={!hasFilters}
        >
          Réinitialiser
        </button>
      </div>
      <div className="list-filter-grid product-filter-grid">
        <label className="list-search">
          <span>Rechercher des produits</span>
          <div className="list-search-input">
            <Icon name="search" size={16} />
            <input
              id="product-search"
              type="search"
              value={search}
              onChange={(event) => onSearch(event.target.value)}
              placeholder="Code, nom ou description"
            />
          </div>
        </label>
        <label>
          <span>Statut</span>
          <select
            id="product-status-filter"
            value={active}
            onChange={(event) => onChange("active", event.target.value)}
          >
            <option value="">Tous</option>
            <option value="true">Actifs</option>
            <option value="false">Inactifs</option>
          </select>
        </label>
        <label>
          <span>Unité</span>
          <select
            id="product-unit-filter"
            value={unit}
            onChange={(event) => onChange("unit", event.target.value)}
          >
            <option value="">Toutes</option>
            {PRODUCT_UNITS.map((value) => (
              <option key={value} value={value}>
                {formatProductUnit(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Trier par</span>
          <select
            id="product-sort"
            value={sort}
            onChange={(event) => onChange("sort", event.target.value)}
          >
            <option value="productName,asc">Nom A–Z</option>
            <option value="productName,desc">Nom Z–A</option>
            <option value="productCode,asc">Code croissant</option>
            <option value="unitPriceExcludingTax,asc">Prix croissant</option>
            <option value="unitPriceExcludingTax,desc">Prix décroissant</option>
            <option value="createdAt,desc">Plus récents</option>
          </select>
        </label>
      </div>
      <div className="list-filter-secondary">
        <span>Prix unitaire HT · MAD</span>
        <label>
          <span>Prix minimum</span>
          <input
            id="product-min-price"
            type="number"
            min="0"
            step="0.01"
            placeholder="0,00"
            value={minPrice}
            onChange={(event) => onChange("minPrice", event.target.value)}
          />
        </label>
        <label>
          <span>Prix maximum</span>
          <input
            id="product-max-price"
            type="number"
            min="0"
            step="0.01"
            placeholder="Sans limite"
            value={maxPrice}
            onChange={(event) => onChange("maxPrice", event.target.value)}
          />
        </label>
      </div>
    </div>
  );
}
