import {
  lazy,
  useCallback,
  useEffect,
  Suspense,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type KeyboardEvent as ReactKeyboardEvent,
} from "react";
import type {
  InvoiceBlock,
  InvoiceDesignSettings,
  InvoiceFreeLayout,
} from "./invoiceDesignApi";
import {
  alignBlock,
  blockDisplayName,
  blockLabels,
  blockVisible,
  fitBlock,
  paperSize,
  type BlockAlignment,
} from "./invoiceLayout";
import { Icon } from "../../components/ui/Icon";
import "./invoice-free-designer.css";

const PdfCanvas = lazy(() => import("./InvoicePdfCanvas"));

export function InvoiceFreeDesigner({
  settings,
  onChange,
  blob,
  pending,
  previewError,
  disabled,
  canSave,
  onSave,
  downloadUrl,
  saveError,
  saveSuccess,
}: {
  settings: InvoiceDesignSettings & { layout: InvoiceFreeLayout };
  onChange: (settings: InvoiceDesignSettings) => void;
  blob: Blob | null;
  pending: boolean;
  previewError: string;
  disabled: boolean;
  canSave?: boolean;
  onSave?: () => void;
  downloadUrl?: string;
  saveError?: string;
  saveSuccess?: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const expandTrigger = useRef<HTMLButtonElement>(null);
  const wasExpanded = useRef(false);
  useEffect(() => {
    const target = dialog.current;
    if (expanded && target && !target.open) target.showModal();
    else if (!expanded && target?.open) target.close();
    if (!expanded && wasExpanded.current) expandTrigger.current?.focus();
    wasExpanded.current = expanded;
  }, [expanded]);
  const [selectedId, setSelectedId] = useState("company");
  const [grid, setGrid] = useState(true);
  const [outlines, setOutlines] = useState(true);
  const [zoom, setZoom] = useState(100);
  const [view, setView] = useState<"edit" | "preview">("edit");
  const [panel, setPanel] = useState<"blocks" | "properties">("properties");
  const [unit, setUnit] = useState<"%" | "mm">("%");
  const [page, setPage] = useState(1);
  const [pages, setPages] = useState(1);
  const documentLoaded = useCallback((count: number) => {
    setPages(count);
    setPage(1);
  }, []);
  const [past, setPast] = useState<InvoiceFreeLayout[]>([]);
  const [future, setFuture] = useState<InvoiceFreeLayout[]>([]);
  const sheet = useRef<HTMLDivElement>(null);
  const drag = useRef<{
    block: InvoiceBlock;
    x: number;
    y: number;
    width: number;
    height: number;
    resize: boolean;
    layout: InvoiceFreeLayout;
    pointerId: number;
  } | null>(null);
  const { layout } = settings;
  const selected = layout.blocks.find((block) => block.id === selectedId);
  const custom = selected?.kind === "TEXT" || selected?.kind === "RECTANGLE";
  const textStyle =
    selected &&
    !["RECTANGLE", "ITEMS", "TOTALS", "LOGO"].includes(selected.kind);
  const dimensions = paperSize(settings.paperFormat);
  function selectBlock(id: string) {
    setSelectedId(id);
    setPanel("properties");
  }
  function undo() {
    const previous = past.at(-1);
    if (disabled || !previous) return;
    setPast(past.slice(0, -1));
    setFuture([layout, ...future]);
    onChange({ ...settings, layout: previous });
  }
  function redo() {
    const next = future[0];
    if (disabled || !next) return;
    setPast([...past, layout]);
    setFuture(future.slice(1));
    onChange({ ...settings, layout: next });
  }
  function duplicate() {
    if (!selected || !custom || disabled || layout.blocks.length >= 40) return;
    const copy = fitBlock(selected, {
      id: `${selected.kind.toLowerCase()}-${crypto.randomUUID()}`,
      x: selected.x + 20,
      y: selected.y + 20,
    });
    const blocks = [...layout.blocks];
    blocks.splice(
      blocks.findIndex((block) => block.id === selected.id) + 1,
      0,
      copy,
    );
    commit({ blocks });
    selectBlock(copy.id);
  }
  function remove() {
    if (!selected || !custom || disabled) return;
    commit({
      blocks: layout.blocks.filter((block) => block.id !== selected.id),
    });
    setSelectedId("company");
  }
  function shortcut(event: ReactKeyboardEvent<HTMLElement>) {
    const target = event.target;
    if (
      disabled ||
      view !== "edit" ||
      (target instanceof HTMLElement &&
        (target.closest("input, textarea, select") || target.isContentEditable))
    )
      return;
    const key = event.key.toLowerCase();
    if ((event.ctrlKey || event.metaKey) && key === "z") {
      event.preventDefault();
      if (event.shiftKey) redo();
      else undo();
    } else if ((event.ctrlKey || event.metaKey) && key === "y") {
      event.preventDefault();
      redo();
    } else if ((event.ctrlKey || event.metaKey) && key === "d" && custom) {
      event.preventDefault();
      duplicate();
    } else if ((key === "delete" || key === "backspace") && custom) {
      event.preventDefault();
      remove();
    }
  }
  function remember(previous: InvoiceFreeLayout) {
    setPast((values) => [...values.slice(-29), previous]);
    setFuture([]);
  }
  function commit(next: InvoiceFreeLayout) {
    if (JSON.stringify(next) === JSON.stringify(layout)) return;
    remember(layout);
    onChange({ ...settings, layout: next });
  }
  function update(patch: Partial<InvoiceBlock>) {
    if (!selected) return;
    commit({
      blocks: layout.blocks.map((block) =>
        block.id === selected.id ? fitBlock(block, patch) : block,
      ),
    });
  }
  function add(kind: "TEXT" | "RECTANGLE") {
    const block: InvoiceBlock = {
      id: `${kind.toLowerCase()}-${crypto.randomUUID()}`,
      kind,
      x: 60,
      y: 50,
      width: 300,
      height: kind === "TEXT" ? 80 : 150,
      backgroundColor: kind === "RECTANGLE" ? "#eef1f8" : null,
      borderColor: settings.accentColor,
      borderWidth: kind === "RECTANGLE" ? 1 : 0,
      fontSize: 12,
      textColor: "#263247",
      textAlignment: "LEFT",
      text: kind === "TEXT" ? "Votre texte" : "",
    };
    commit({
      blocks:
        kind === "RECTANGLE"
          ? [block, ...layout.blocks]
          : [...layout.blocks, block],
    });
    selectBlock(block.id);
  }
  function start(
    event: ReactPointerEvent<HTMLButtonElement>,
    block: InvoiceBlock,
    resize: boolean,
  ) {
    if (disabled || event.button !== 0) return;
    event.preventDefault();
    const bounds = sheet.current!.getBoundingClientRect();
    drag.current = {
      block,
      x: event.clientX,
      y: event.clientY,
      width: bounds.width,
      height: bounds.height,
      resize,
      layout,
      pointerId: event.pointerId,
    };
    selectBlock(block.id);
    event.currentTarget.setPointerCapture(event.pointerId);
  }
  function move(event: ReactPointerEvent<HTMLButtonElement>) {
    const initial = drag.current;
    if (!initial || initial.pointerId !== event.pointerId) return;
    const dx = ((event.clientX - initial.x) / initial.width) * 1000;
    const dy = ((event.clientY - initial.y) / initial.height) * 1000;
    const snap = (value: number) =>
      grid ? Math.round(value / 10) * 10 : Math.round(value);
    const patch = initial.resize
      ? {
          width: Math.min(
            1000 - initial.block.x,
            snap(initial.block.width + dx),
          ),
          height: Math.min(
            1000 - initial.block.y,
            snap(initial.block.height + dy),
          ),
        }
      : { x: snap(initial.block.x + dx), y: snap(initial.block.y + dy) };
    onChange({
      ...settings,
      layout: {
        blocks: layout.blocks.map((block) =>
          block.id === initial.block.id
            ? fitBlock(initial.block, patch)
            : block,
        ),
      },
    });
  }
  function finish(event: ReactPointerEvent<HTMLButtonElement>, cancel = false) {
    const initial = drag.current;
    if (!initial || initial.pointerId !== event.pointerId) return;
    if (cancel) onChange({ ...settings, layout: initial.layout });
    else if (JSON.stringify(layout) !== JSON.stringify(initial.layout))
      remember(initial.layout);
    drag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId))
      event.currentTarget.releasePointerCapture(event.pointerId);
  }
  const workspace = (
    <section
      className={`invoice-free-designer ${view === "preview" ? "is-preview" : ""}`}
      aria-label="Composition libre de la facture"
      onKeyDown={shortcut}
    >
      <div className="invoice-free-toolbar">
        <div className="invoice-designer-title">
          <Icon name="columns" size={18} />
          <div>
            <strong>Atelier de facture</strong>
            <span>{settings.paperFormat} · Modèle de l’entreprise</span>
          </div>
        </div>
        <div className="invoice-view-switch" aria-label="Affichage du document">
          <button
            type="button"
            aria-pressed={view === "edit"}
            onClick={() => setView("edit")}
          >
            <Icon name="edit" size={14} /> Composer
          </button>
          <button
            type="button"
            aria-pressed={view === "preview"}
            onClick={() => setView("preview")}
          >
            <Icon name="eye" size={14} /> Aperçu
          </button>
        </div>
        <button
          ref={expandTrigger}
          type="button"
          className="secondary invoice-expand-button"
          onClick={() => setExpanded((value) => !value)}
        >
          <Icon name="columns" size={14} />
          {expanded ? "Réduire l’atelier" : "Agrandir l’atelier"}
        </button>
      </div>
      {expanded && saveError && (
        <p className="form-error invoice-expanded-message" role="alert">
          {saveError}
        </p>
      )}
      {expanded && saveSuccess && (
        <p className="toast invoice-expanded-message" role="status">
          {saveSuccess}
        </p>
      )}
      <div className="invoice-free-tools">
        <div className="invoice-tool-group">
          <button
            type="button"
            className="secondary"
            disabled={
              disabled || view === "preview" || layout.blocks.length >= 40
            }
            onClick={() => add("RECTANGLE")}
          >
            <Icon name="plus" size={14} /> Rectangle
          </button>
          <button
            type="button"
            className="secondary"
            disabled={
              disabled || view === "preview" || layout.blocks.length >= 40
            }
            onClick={() => add("TEXT")}
          >
            <Icon name="plus" size={14} /> Texte
          </button>
        </div>
        <div className="invoice-tool-group">
          <button
            type="button"
            className="secondary"
            disabled={disabled || view === "preview" || !past.length}
            title="Annuler · Ctrl/Cmd Z"
            onClick={undo}
          >
            Annuler
          </button>
          <button
            type="button"
            className="secondary"
            disabled={disabled || view === "preview" || !future.length}
            title="Rétablir · Ctrl/Cmd Maj Z"
            onClick={redo}
          >
            Rétablir
          </button>
        </div>
        <label className="invoice-zoom-control">
          Zoom
          <select
            value={zoom}
            onChange={(event) => setZoom(Number(event.target.value))}
          >
            {[75, 100, 125, 150, 200].map((value) => (
              <option key={value} value={value}>
                {value === 100 ? "Ajuster" : `${value} %`}
              </option>
            ))}
          </select>
        </label>
        <label>
          <input
            type="checkbox"
            checked={grid}
            disabled={view === "preview"}
            onChange={(event) => setGrid(event.target.checked)}
          />{" "}
          Grille aimantée
        </label>
        <label>
          <input
            type="checkbox"
            checked={outlines}
            disabled={view === "preview"}
            onChange={(event) => setOutlines(event.target.checked)}
          />{" "}
          Repères
        </label>
      </div>
      <div className="invoice-free-body">
        {view === "edit" && (
          <fieldset className="invoice-block-inspector" disabled={disabled}>
            <legend className="sr-only">Propriétés du bloc sélectionné</legend>
            <div
              className="invoice-panel-switch"
              aria-label="Panneau du designer"
            >
              <button
                type="button"
                aria-pressed={panel === "blocks"}
                onClick={() => setPanel("blocks")}
              >
                Blocs <span>{layout.blocks.length}</span>
              </button>
              <button
                type="button"
                aria-pressed={panel === "properties"}
                onClick={() => setPanel("properties")}
              >
                Propriétés
              </button>
            </div>
            {panel === "blocks" ? (
              <div className="invoice-layer-panel">
                <p className="invoice-inspector-hint">
                  Sélectionnez un bloc sur la feuille ou dans cette liste.
                </p>
                <ul
                  className="invoice-layer-list"
                  aria-label="Blocs du document"
                >
                  {layout.blocks.map((block) => (
                    <li key={block.id}>
                      <button
                        type="button"
                        aria-pressed={selectedId === block.id}
                        onClick={() => selectBlock(block.id)}
                      >
                        <Icon
                          name={
                            block.kind === "RECTANGLE"
                              ? "columns"
                              : block.kind === "LOGO"
                                ? "building"
                                : "invoice"
                          }
                          size={15}
                        />
                        <span>
                          <strong>
                            {blockDisplayName(block, layout.blocks)}
                          </strong>
                          {block.kind === "TEXT" && (
                            <small>{block.text || "Texte vide"}</small>
                          )}
                        </span>
                        {!blockVisible(block, settings) && (
                          <small>Masqué</small>
                        )}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <>
                {selected && (
                  <div className="invoice-selected-heading">
                    <span>Bloc sélectionné</span>
                    <strong>{blockDisplayName(selected, layout.blocks)}</strong>
                    <small>
                      {custom
                        ? "Élément personnalisé"
                        : "Information de facturation"}
                    </small>
                  </div>
                )}
                <label>
                  Bloc sélectionné
                  <select
                    value={selected?.id ?? ""}
                    onChange={(event) => setSelectedId(event.target.value)}
                  >
                    {!selected && <option value="">Choisir un bloc</option>}
                    {layout.blocks.map((block) => (
                      <option key={block.id} value={block.id}>
                        {blockDisplayName(block, layout.blocks)}
                        {!blockVisible(block, settings) ? " (masqué)" : ""}
                      </option>
                    ))}
                  </select>
                </label>
                {selected && (
                  <>
                    <p className="invoice-inspector-hint">
                      Glissez le bloc sur la feuille. Utilisez la poignée pour
                      le redimensionner, ou les flèches du clavier pour le
                      déplacer.
                    </p>
                    <div className="invoice-inspector-section-title">
                      <strong>Position et dimensions</strong>
                      <label className="invoice-unit-control">
                        <span className="sr-only">Unité des mesures</span>
                        <select
                          value={unit}
                          onChange={(event) =>
                            setUnit(event.target.value as "%" | "mm")
                          }
                        >
                          <option value="%">%</option>
                          <option value="mm">mm</option>
                        </select>
                      </label>
                    </div>
                    <div className="invoice-geometry-fields">
                      {(
                        [
                          ["x", `Position X (${unit})`],
                          ["y", `Position Y (${unit})`],
                          ["width", `Largeur (${unit})`],
                          ["height", `Hauteur (${unit})`],
                        ] as const
                      ).map(([key, label]) => (
                        <label key={key}>
                          {label}
                          <input
                            type="number"
                            min={
                              key === "width" || key === "height"
                                ? unit === "%"
                                  ? 2
                                  : 0.1
                                : 0
                            }
                            max={
                              unit === "%"
                                ? 100
                                : key === "x" || key === "width"
                                  ? dimensions.width
                                  : dimensions.height
                            }
                            step={0.1}
                            value={
                              unit === "%"
                                ? selected[key] / 10
                                : Math.round(
                                    (selected[key] / 1000) *
                                      (key === "x" || key === "width"
                                        ? dimensions.width
                                        : dimensions.height) *
                                      10,
                                  ) / 10
                            }
                            onChange={(event) => {
                              if (
                                event.target.value !== "" &&
                                Number.isFinite(event.target.valueAsNumber)
                              )
                                update({
                                  [key]:
                                    unit === "%"
                                      ? event.target.valueAsNumber * 10
                                      : (event.target.valueAsNumber /
                                          (key === "x" || key === "width"
                                            ? dimensions.width
                                            : dimensions.height)) *
                                        1000,
                                });
                            }}
                          />
                        </label>
                      ))}
                    </div>
                    <div
                      className="invoice-block-alignment"
                      role="group"
                      aria-label="Aligner le bloc sur la page"
                    >
                      {(
                        [
                          ["left", "Aligner à gauche", "Gauche"],
                          ["center", "Centrer horizontalement", "Centre"],
                          ["right", "Aligner à droite", "Droite"],
                          ["top", "Aligner en haut", "Haut"],
                          ["middle", "Centrer verticalement", "Milieu"],
                          ["bottom", "Aligner en bas", "Bas"],
                        ] as const
                      ).map(([value, label, text]) => (
                        <button
                          key={value}
                          type="button"
                          className="secondary"
                          aria-label={label}
                          onClick={() => {
                            const aligned = alignBlock(
                              selected,
                              value as BlockAlignment,
                            );
                            update({ x: aligned.x, y: aligned.y });
                          }}
                        >
                          {text}
                        </button>
                      ))}
                    </div>
                    <div className="invoice-inspector-section-title">
                      <strong>Apparence</strong>
                    </div>
                    <label className="invoice-inspector-check">
                      <input
                        type="checkbox"
                        checked={selected.backgroundColor !== null}
                        onChange={(event) =>
                          update({
                            backgroundColor: event.target.checked
                              ? "#eef1f8"
                              : null,
                          })
                        }
                      />{" "}
                      Fond coloré
                    </label>
                    {selected.backgroundColor !== null && (
                      <label className="invoice-inspector-color">
                        Couleur du fond
                        <input
                          type="color"
                          value={selected.backgroundColor}
                          onChange={(event) =>
                            update({ backgroundColor: event.target.value })
                          }
                        />
                      </label>
                    )}
                    <div className="invoice-geometry-fields">
                      <label>
                        Épaisseur du cadre
                        <select
                          value={selected.borderWidth}
                          onChange={(event) =>
                            update({ borderWidth: Number(event.target.value) })
                          }
                        >
                          {[0, 1, 2, 3, 4, 5].map((width) => (
                            <option key={width} value={width}>
                              {width ? `${width} pt` : "Sans cadre"}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label className="invoice-inspector-color">
                        Couleur du cadre
                        <input
                          type="color"
                          value={selected.borderColor}
                          onChange={(event) =>
                            update({ borderColor: event.target.value })
                          }
                        />
                      </label>
                    </div>
                    {textStyle && (
                      <>
                        <div className="invoice-geometry-fields">
                          <label>
                            Taille du bloc (pt)
                            <input
                              type="number"
                              min={8}
                              max={32}
                              value={selected.fontSize}
                              onChange={(event) => {
                                const value = event.target.valueAsNumber;
                                if (
                                  Number.isInteger(value) &&
                                  value >= 8 &&
                                  value <= 32
                                )
                                  update({ fontSize: value });
                              }}
                            />
                          </label>
                          <label className="invoice-inspector-color">
                            Couleur du texte
                            <input
                              type="color"
                              value={selected.textColor}
                              onChange={(event) =>
                                update({ textColor: event.target.value })
                              }
                            />
                          </label>
                        </div>
                        <label>
                          Alignement
                          <select
                            value={selected.textAlignment}
                            onChange={(event) =>
                              update({
                                textAlignment: event.target
                                  .value as InvoiceBlock["textAlignment"],
                              })
                            }
                          >
                            <option value="LEFT">Gauche</option>
                            <option value="CENTER">Centré</option>
                            <option value="RIGHT">Droite</option>
                          </select>
                        </label>
                      </>
                    )}
                    {selected.kind === "TEXT" && (
                      <label>
                        Texte du bloc
                        <textarea
                          rows={5}
                          maxLength={500}
                          value={selected.text}
                          onChange={(event) =>
                            update({ text: event.target.value })
                          }
                        />
                      </label>
                    )}
                    {selected.kind === "FOOTER" && (
                      <label>
                        Texte du pied de page
                        <textarea
                          rows={4}
                          maxLength={300}
                          value={settings.footerText}
                          onChange={(event) =>
                            onChange({
                              ...settings,
                              footerText: event.target.value,
                            })
                          }
                        />
                      </label>
                    )}
                    {selected.kind === "ITEMS" && (
                      <p className="invoice-inspector-hint">
                        Les articles continuent dans cette zone sur les pages
                        suivantes. Les totaux et le montant en lettres
                        apparaissent sur la dernière page.
                      </p>
                    )}
                    {custom && (
                      <>
                        <div className="invoice-block-order">
                          <button
                            type="button"
                            className="secondary"
                            onClick={() =>
                              commit({
                                blocks: [
                                  selected,
                                  ...layout.blocks.filter(
                                    (block) => block.id !== selected.id,
                                  ),
                                ],
                              })
                            }
                          >
                            À l’arrière
                          </button>
                          <button
                            type="button"
                            className="secondary"
                            onClick={() =>
                              commit({
                                blocks: [
                                  ...layout.blocks.filter(
                                    (block) => block.id !== selected.id,
                                  ),
                                  selected,
                                ],
                              })
                            }
                          >
                            À l’avant
                          </button>
                        </div>
                        <div className="invoice-block-actions">
                          <button
                            type="button"
                            className="secondary"
                            disabled={layout.blocks.length >= 40}
                            onClick={duplicate}
                          >
                            Dupliquer le bloc
                          </button>
                          <button
                            type="button"
                            className="invoice-delete-block"
                            onClick={remove}
                          >
                            <Icon name="trash" size={14} /> Supprimer le bloc
                          </button>
                        </div>
                      </>
                    )}
                  </>
                )}
              </>
            )}
          </fieldset>
        )}
        <div className="invoice-free-stage">
          <div className="invoice-stage-heading">
            <span>
              {settings.paperFormat} · {dimensions.width} × {dimensions.height}{" "}
              mm
            </span>
            <span
              className={`invoice-free-status ${pending ? "is-pending" : previewError ? "is-error" : "is-ready"}`}
              role="status"
            >
              {pending
                ? "Actualisation du PDF…"
                : previewError
                  ? "Ajustement nécessaire"
                  : "PDF à jour"}
            </span>
          </div>
          {previewError && (
            <p className="form-error" role="alert">
              {previewError}
            </p>
          )}
          <div
            className="invoice-sheet-viewport"
            aria-label="Feuille de facture"
          >
            <div
              ref={sheet}
              className={`invoice-free-sheet ${grid && outlines && view === "edit" ? "has-grid" : ""}`}
              data-stale={pending || Boolean(previewError)}
              style={{
                width: `${zoom}%`,
                maxWidth: (795 * zoom) / 100,
                aspectRatio:
                  settings.paperFormat === "A4" ? "595 / 842" : "612 / 792",
              }}
            >
              {blob && (
                <Suspense
                  fallback={<span className="sr-only">Chargement du PDF…</span>}
                >
                  <PdfCanvas blob={blob} page={page} onLoad={documentLoaded} />
                </Suspense>
              )}
              {view === "edit" &&
                layout.blocks
                  .filter((block) => blockVisible(block, settings, page, pages))
                  .map((block) => (
                    <div
                      key={block.id}
                      className={`invoice-layout-block ${selectedId === block.id ? "is-selected" : ""} ${outlines ? "show-outline" : ""} ${block.kind === "RECTANGLE" ? "is-decoration" : ""}`}
                      style={{
                        left: `${block.x / 10}%`,
                        top: `${block.y / 10}%`,
                        width: `${block.width / 10}%`,
                        height: `${block.height / 10}%`,
                        background:
                          pending || previewError || !blob
                            ? (block.backgroundColor ?? undefined)
                            : undefined,
                      }}
                    >
                      <button
                        type="button"
                        className="invoice-block-move"
                        aria-label={`Déplacer ${blockDisplayName(block, layout.blocks)}`}
                        aria-pressed={selectedId === block.id}
                        disabled={disabled || !outlines}
                        onFocus={() => selectBlock(block.id)}
                        onPointerDown={(event) => start(event, block, false)}
                        onPointerMove={move}
                        onPointerUp={(event) => finish(event)}
                        onPointerCancel={(event) => finish(event, true)}
                        onLostPointerCapture={(event) => finish(event)}
                        onKeyDown={(event) => {
                          const step = event.shiftKey ? 10 : 1;
                          const patch = {
                            ArrowLeft: { x: block.x - step },
                            ArrowRight: { x: block.x + step },
                            ArrowUp: { y: block.y - step },
                            ArrowDown: { y: block.y + step },
                          }[event.key];
                          if (patch) {
                            event.preventDefault();
                            remember(layout);
                            onChange({
                              ...settings,
                              layout: {
                                blocks: layout.blocks.map((item) =>
                                  item.id === block.id
                                    ? fitBlock(item, patch)
                                    : item,
                                ),
                              },
                            });
                          }
                        }}
                      >
                        <span>
                          {blockLabels[block.kind]}
                          {block.kind === "TEXT" &&
                          (pending || previewError || !blob)
                            ? ` · ${block.text}`
                            : ""}
                        </span>
                      </button>
                      {selectedId === block.id && outlines && (
                        <button
                          type="button"
                          className="invoice-block-resize"
                          aria-label={`Redimensionner ${blockDisplayName(block, layout.blocks)}`}
                          disabled={disabled}
                          onPointerDown={(event) => start(event, block, true)}
                          onPointerMove={move}
                          onPointerUp={(event) => finish(event)}
                          onPointerCancel={(event) => finish(event, true)}
                          onLostPointerCapture={(event) => finish(event)}
                          onKeyDown={(event) => {
                            const step = event.shiftKey ? 10 : 1;
                            const patch = {
                              ArrowLeft: { width: block.width - step },
                              ArrowRight: {
                                width: Math.min(
                                  1000 - block.x,
                                  block.width + step,
                                ),
                              },
                              ArrowUp: { height: block.height - step },
                              ArrowDown: {
                                height: Math.min(
                                  1000 - block.y,
                                  block.height + step,
                                ),
                              },
                            }[event.key];
                            if (patch) {
                              event.preventDefault();
                              update(patch);
                            }
                          }}
                        />
                      )}
                    </div>
                  ))}
            </div>
          </div>
          <div className="invoice-stage-footer">
            <p className="invoice-stage-note">
              {view === "preview"
                ? "Aperçu du document final · Données d’exemple"
                : "Flèches : déplacer · Maj : pas de 1 % · Ctrl/Cmd Z : annuler"}
            </p>
            <div
              className="invoice-page-navigation"
              aria-label="Pages de l’aperçu"
            >
              <button
                type="button"
                className="secondary"
                aria-label="Page précédente"
                disabled={pending || page === 1}
                onClick={() => setPage((value) => value - 1)}
              >
                ‹
              </button>
              <span>
                Page {page} / {pages}
              </span>
              <button
                type="button"
                className="secondary"
                aria-label="Page suivante"
                disabled={pending || page === pages}
                onClick={() => setPage((value) => value + 1)}
              >
                ›
              </button>
            </div>
          </div>
        </div>
      </div>
      {expanded && (
        <footer className="invoice-expanded-actions">
          <span>
            Modèle commun à tous les clients · Ctrl/Cmd S pour enregistrer
          </span>
          {downloadUrl && (
            <a
              href={downloadUrl}
              download="apercu-facture.pdf"
              className="button-link secondary-link"
            >
              <Icon name="download" size={14} /> Télécharger le PDF
            </a>
          )}
          <button type="button" disabled={!canSave} onClick={onSave}>
            {disabled ? "Enregistrement…" : "Appliquer à tous les clients"}
          </button>
        </footer>
      )}
    </section>
  );
  return (
    <>
      {!expanded && workspace}
      <dialog
        ref={dialog}
        className="invoice-designer-dialog"
        aria-label="Atelier de facture agrandi"
        onCancel={(event) => {
          event.preventDefault();
          setExpanded(false);
        }}
        onClose={() => setExpanded(false)}
      >
        {expanded && workspace}
      </dialog>
    </>
  );
}
