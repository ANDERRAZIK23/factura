import { useState, useRef, useEffect, useId } from "react";
import { createPortal } from "react-dom";
import type { ProductSummary } from "../../products/types/product";
import { formatMoney, formatProductUnit } from "../../../utils/format";
import { useProducts } from "../../products/hooks/useProducts";
import { Icon } from "../../../components/ui/Icon";

export function ProductCombobox({
  products,
  value,
  onChange,
  error,
  id,
  label,
  disabled = false,
  storedLabel,
}: {
  products: ProductSummary[];
  value: string;
  onChange: (value: string, product?: ProductSummary) => void;
  error?: boolean;
  id: string;
  label: string;
  disabled?: boolean;
  storedLabel?: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [activeQuery, setActiveQuery] = useState("");
  const [chosen, setChosen] = useState<ProductSummary | null>(null);
  const query = useProducts({
    page: 0,
    size: 100,
    sort: "productName,asc",
    active: true,
    search: activeQuery || undefined,
  });
  useEffect(() => {
    const timer = window.setTimeout(() => setActiveQuery(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [position, setPosition] = useState({ top: 0, left: 0, width: 320 });
  const input = useRef<HTMLInputElement>(null);
  const popup = useRef<HTMLUListElement>(null);
  const listId = useId();
  const selected =
    products.find((product) => product.id === value) ??
    (chosen?.id === value ? chosen : undefined);
  const selectedLabel = selected
    ? `${selected.productCode} — ${selected.productName}`
    : (storedLabel ?? "");
  const filtered = query.data?.content ?? [];
  const searching = query.isFetching || search.trim() !== activeQuery;
  const active = Math.min(activeIndex, Math.max(filtered.length - 1, 0));

  useEffect(() => {
    if (!open) return;
    function place() {
      const rect = input.current?.getBoundingClientRect();
      if (!rect) return;
      const width = Math.min(Math.max(rect.width, 320), window.innerWidth - 32);
      const height = Math.min(264, popup.current?.offsetHeight ?? 264);
      const top =
        rect.bottom + height + 8 > window.innerHeight && rect.top > height + 8
          ? rect.top - height - 6
          : rect.bottom + 6;
      setPosition({
        top,
        left: Math.max(16, Math.min(rect.left, window.innerWidth - width - 16)),
        width,
      });
    }
    function outside(event: PointerEvent) {
      if (
        event.target instanceof Node &&
        !input.current?.contains(event.target) &&
        !popup.current?.contains(event.target)
      )
        setOpen(false);
    }
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    document.addEventListener("pointerdown", outside);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
      document.removeEventListener("pointerdown", outside);
    };
  }, [open, filtered.length]);

  function choose(product: ProductSummary) {
    if (searching || query.isError) return;
    setChosen(product);
    onChange(product.id, product);
    setSearch("");
    setOpen(false);
  }

  return (
    <div className="product-picker">
      <input
        ref={input}
        id={id}
        type="text"
        role="combobox"
        aria-label={label}
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={
          open && !searching && !query.isError && filtered.length
            ? `${listId}-${filtered[active].id}`
            : undefined
        }
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        autoComplete="off"
        disabled={disabled}
        value={open ? search : selectedLabel || search}
        placeholder="Choisir un produit…"
        onFocus={() => {
          if (selectedLabel) setSearch("");
          setActiveIndex(0);
          setOpen(true);
        }}
        onClick={() => {
          if (!open) {
            if (selectedLabel) setSearch("");
            setActiveIndex(0);
            setOpen(true);
          }
        }}
        onBlur={() => setOpen(false)}
        onChange={(event) => {
          setSearch(event.target.value);
          setOpen(true);
          setActiveIndex(0);
          if (value) onChange("");
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            setOpen(true);
            const next = !open
              ? 0
              : Math.max(
                  0,
                  Math.min(
                    active + (event.key === "ArrowDown" ? 1 : -1),
                    filtered.length - 1,
                  ),
                );
            setActiveIndex(next);
            popup.current?.children[next]?.scrollIntoView?.({
              block: "nearest",
            });
          } else if (event.key === "Enter" && open) {
            event.preventDefault();
            if (filtered[active]) choose(filtered[active]);
          } else if (event.key === "Escape" && open) {
            event.preventDefault();
            event.stopPropagation();
            setOpen(false);
          }
        }}
      />
      <Icon name="search" size={15} />
      {open &&
        createPortal(
          <ul
            ref={popup}
            id={listId}
            role="listbox"
            aria-label="Produits disponibles"
            className="product-picker-options"
            style={position}
            onMouseDown={(event) => event.preventDefault()}
          >
            {query.isError ? (
              <li role="presentation" className="product-picker-empty">
                Catalogue indisponible.{" "}
                <button
                  type="button"
                  className="secondary compact-button"
                  onClick={() => void query.refetch()}
                >
                  Réessayer
                </button>
              </li>
            ) : searching ? (
              <li role="presentation" className="product-picker-empty">
                Recherche des produits…
              </li>
            ) : filtered.length ? (
              filtered.map((product, index) => (
                <li
                  key={product.id}
                  id={`${listId}-${product.id}`}
                  role="option"
                  aria-selected={index === active}
                  onMouseMove={() => setActiveIndex(index)}
                  onClick={() => choose(product)}
                >
                  <span>
                    <strong>{product.productName}</strong>
                    <small>
                      {product.productCode} · {formatProductUnit(product.unit)}
                    </small>
                  </span>
                  <span className="product-picker-price">
                    {formatMoney(product.unitPriceExcludingTax)}
                    <small>HT / unité</small>
                  </span>
                </li>
              ))
            ) : (
              <li className="product-picker-empty" role="presentation">
                Aucun produit ne correspond à votre recherche.
              </li>
            )}
          </ul>,
          document.body,
        )}
    </div>
  );
}
