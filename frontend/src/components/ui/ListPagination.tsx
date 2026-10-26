export function ListPagination({
  page,
  size,
  count,
  totalElements,
  totalPages,
  pending = false,
  label,
  onPage,
  onSize,
}: {
  page: number;
  size: number;
  count: number;
  totalElements: number;
  totalPages: number;
  pending?: boolean;
  label: string;
  onPage: (page: number) => void;
  onSize?: (size: string) => void;
}) {
  return (
    <nav className="list-pagination" aria-label={`Pagination ${label}`}>
      <span className="list-range" aria-live="polite">
        {count ? `${page * size + 1}–${page * size + count}` : "0"} sur{" "}
        <strong>{totalElements.toLocaleString("fr-FR")}</strong> {label}
      </span>
      <div className="list-pagination-controls">
        {onSize && (
          <label>
            Lignes par page
            <select
              value={size}
              onChange={(event) => onSize(event.target.value)}
              disabled={pending}
            >
              {[10, 20, 50, 100].map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
        )}
        {page > 0 && count === 0 ? (
          <button
            type="button"
            className="secondary"
            disabled={pending}
            onClick={() => onPage(0)}
          >
            Revenir à la première page
          </button>
        ) : (
          <span>
            Page {page + 1} sur {Math.max(1, totalPages)}
          </span>
        )}
        <button
          type="button"
          className="secondary"
          disabled={page === 0 || pending}
          onClick={() => onPage(page - 1)}
        >
          Précédent
        </button>
        <button
          type="button"
          className="secondary"
          disabled={page + 1 >= totalPages || pending}
          onClick={() => onPage(page + 1)}
        >
          Suivant
        </button>
      </div>
    </nav>
  );
}
