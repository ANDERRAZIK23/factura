import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  EditorContent,
  useEditor,
  useEditorState,
  type Editor,
} from "@tiptap/react";
import { PageHeader } from "../../components/ui/PageHeader";
import { Icon } from "../../components/ui/Icon";
import { EmptyState } from "../../components/ui/EmptyState";
import { ConfirmDialog } from "../../components/ui/ConfirmDialog";
import { companyApi } from "../companies/api/companyApi";
import { useCompanyBranding } from "../companies/hooks/useCompanies";
import { formatDateTime } from "../../utils/format";
import { contractExtensions, contractFields } from "./contractEditor";
import {
  contractDesignApi,
  contractDesignError,
  type ContractDesign,
  type ContractDesignSettings,
} from "./contractDesignApi";
import "./contract-designer.css";

const ContractPdfPreview = lazy(() => import("./ContractPdfPreview"));

export default function ContractDesignerPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const companyId = params.get("companyId") ?? "";
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [pendingCompany, setPendingCompany] = useState<string | null>(null);
  const [pendingRoute, setPendingRoute] = useState<string | null>(null);
  useEffect(() => {
    if (!dirty) return;
    function protectNavigation(event: MouseEvent) {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.ctrlKey ||
        event.metaKey ||
        event.shiftKey ||
        event.altKey
      )
        return;
      const link =
        event.target instanceof Element
          ? event.target.closest<HTMLAnchorElement>("a[href]")
          : null;
      if (
        !link ||
        link.hasAttribute("download") ||
        (link.target && link.target !== "_self")
      )
        return;
      const destination = new URL(link.href);
      if (
        destination.origin !== window.location.origin ||
        destination.pathname + destination.search ===
          window.location.pathname + window.location.search
      )
        return;
      event.preventDefault();
      event.stopPropagation();
      setPendingRoute(
        destination.pathname + destination.search + destination.hash,
      );
    }
    document.addEventListener("click", protectNavigation, true);
    return () => document.removeEventListener("click", protectNavigation, true);
  }, [dirty]);
  const companies = useQuery({
    queryKey: ["companies", "list"],
    queryFn: companyApi.list,
  });
  const design = useQuery({
    queryKey: ["contract-design", companyId],
    queryFn: () => contractDesignApi.get(companyId),
    enabled: Boolean(companyId),
    retry: false,
    refetchOnWindowFocus: false,
  });
  const companyName =
    companies.data?.content.find((company) => company.id === companyId)
      ?.companyName ?? "Entreprise sélectionnée";
  function selectCompany(id: string) {
    if (busy) return;
    if (dirty) setPendingCompany(id);
    else setParams(id ? { companyId: id } : {});
  }
  return (
    <section
      className="contract-designer-page"
      data-testid="contract-designer-page"
    >
      <PageHeader
        eyebrow="Administration de l’application"
        title="Design des contrats"
        description="Un modèle de contrat pour chaque entreprise, avec une édition libre et un export PDF."
      />
      <div className="contract-company-bar">
        <span className="contract-company-icon">
          <Icon name="building" size={20} />
        </span>
        <label>
          Entreprise du modèle
          <select
            aria-label="Entreprise du modèle de contrat"
            value={companyId}
            disabled={busy || companies.isPending || companies.isError}
            onChange={(event) => selectCompany(event.target.value)}
          >
            <option value="">Choisir une entreprise</option>
            {companies.data?.content.map((company) => (
              <option key={company.id} value={company.id}>
                {company.companyCode} — {company.companyName}
              </option>
            ))}
          </select>
        </label>
        <p>
          Le document et sa mise en page sont enregistrés pour cette entreprise.
        </p>
      </div>
      {companies.isError && (
        <div className="form-error" role="alert">
          Impossible de charger les entreprises.{" "}
          <button
            type="button"
            className="link-button"
            onClick={() => void companies.refetch()}
          >
            Réessayer
          </button>
        </div>
      )}
      {!companyId ? (
        <EmptyState
          icon="edit"
          title={
            companies.isPending
              ? "Chargement des entreprises…"
              : "Choisissez une entreprise"
          }
          description="Retrouvez son modèle ou commencez à rédiger son contrat."
        />
      ) : design.isPending ? (
        <div className="loading-state" role="status">
          Chargement du modèle de contrat…
        </div>
      ) : design.isError ? (
        <EmptyState
          icon="invoice"
          title="Impossible de charger le modèle"
          description="Vérifiez que l’entreprise existe et réessayez."
          action={
            <button
              type="button"
              className="secondary"
              onClick={() => void design.refetch()}
            >
              Réessayer
            </button>
          }
        />
      ) : (
        <ContractEditor
          key={companyId}
          initial={design.data}
          companyName={companyName}
          onDirty={setDirty}
          onBusy={setBusy}
        />
      )}
      <ConfirmDialog
        open={pendingCompany !== null}
        title="Changer d’entreprise ?"
        description="Les modifications non enregistrées de ce modèle seront perdues."
        confirmLabel="Changer sans enregistrer"
        onClose={() => setPendingCompany(null)}
        onConfirm={() => {
          if (pendingCompany !== null)
            setParams(pendingCompany ? { companyId: pendingCompany } : {});
          setPendingCompany(null);
          setDirty(false);
        }}
      />
      <ConfirmDialog
        open={pendingRoute !== null}
        title="Quitter le modèle de contrat ?"
        description="Les modifications non enregistrées seront perdues."
        confirmLabel="Quitter sans enregistrer"
        onClose={() => setPendingRoute(null)}
        onConfirm={() => {
          if (pendingRoute) navigate(pendingRoute);
          setPendingRoute(null);
          setDirty(false);
        }}
      />
    </section>
  );
}

function ContractEditor({
  initial,
  companyName,
  onDirty,
  onBusy,
}: {
  initial: ContractDesign;
  companyName: string;
  onDirty: (value: boolean) => void;
  onBusy: (value: boolean) => void;
}) {
  const cache = useQueryClient();
  const branding = useCompanyBranding(initial.companyId, true);
  const [settings, setSettings] = useState(initial.settings);
  const [saved, setSaved] = useState(initial);
  const [tab, setTab] = useState<"home" | "insert" | "layout">("home");
  const [zoom, setZoom] = useState(100);
  const [saving, setSaving] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [pdfPending, setPdfPending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [confirmation, setConfirmation] = useState<"blank" | "reload" | null>(
    null,
  );
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewBlob, setPreviewBlob] = useState<Blob | null>(null);
  const previewDialog = useRef<HTMLDialogElement>(null);
  const busy = saving || pdfPending || reloading;
  const dirty = JSON.stringify(settings) !== JSON.stringify(saved.settings);
  const editor = useEditor({
    extensions: contractExtensions,
    content: initial.settings.content,
    editorProps: {
      attributes: {
        class: "contract-document-content",
        role: "textbox",
        "aria-label": "Contenu du contrat",
        "aria-multiline": "true",
        spellcheck: "true",
      },
    },
    onCreate: ({ editor }) => {
      const content = editor.getJSON();
      setSettings((current) => ({ ...current, content }));
      setSaved((current) => ({
        ...current,
        settings: { ...current.settings, content },
      }));
    },
    onUpdate: ({ editor }) => {
      setSettings((current) => ({ ...current, content: editor.getJSON() }));
      setSuccess("");
    },
  });
  useEffect(() => {
    editor?.setEditable(!busy, false);
  }, [editor, busy]);
  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);
  useEffect(() => {
    onBusy(busy);
    return () => onBusy(false);
  }, [busy, onBusy]);
  useEffect(() => {
    if (!dirty) return;
    const beforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [dirty]);
  useEffect(() => {
    if (previewUrl) previewDialog.current?.showModal();
    else previewDialog.current?.close();
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);
  const save = useCallback(async () => {
    if (!editor || busy) return;
    if (!settings.title.trim()) {
      setError("Donnez un titre au modèle avant de l’enregistrer.");
      return;
    }
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const updated = await contractDesignApi.save(
        initial.companyId,
        { ...settings, content: editor.getJSON() },
        saved.version,
      );
      setSaved(updated);
      setSettings(updated.settings);
      cache.setQueryData(["contract-design", initial.companyId], updated);
      setSuccess(`Modèle enregistré pour ${companyName}.`);
    } catch (caught) {
      setError(
        await contractDesignError(
          caught,
          "Impossible d’enregistrer le modèle de contrat.",
        ),
      );
    } finally {
      setSaving(false);
    }
  }, [
    editor,
    busy,
    settings,
    initial.companyId,
    saved.version,
    cache,
    companyName,
  ]);
  useEffect(() => {
    function shortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        void save();
      }
    }
    window.addEventListener("keydown", shortcut);
    return () => window.removeEventListener("keydown", shortcut);
  }, [save]);
  function change<K extends keyof ContractDesignSettings>(
    key: K,
    value: ContractDesignSettings[K],
  ) {
    setSettings((current) => ({ ...current, [key]: value }));
    setSuccess("");
  }
  async function reload() {
    if (!editor) return;
    setConfirmation(null);
    setReloading(true);
    setError("");
    try {
      const updated = await contractDesignApi.get(initial.companyId);
      editor.commands.setContent(updated.settings.content, {
        emitUpdate: false,
      });
      const normalized = {
        ...updated,
        settings: { ...updated.settings, content: editor.getJSON() },
      };
      setSettings(normalized.settings);
      setSaved(normalized);
      cache.setQueryData(["contract-design", initial.companyId], normalized);
      setSuccess("La version enregistrée a été rechargée.");
    } catch (caught) {
      setError(
        await contractDesignError(caught, "Impossible de recharger le modèle."),
      );
    } finally {
      setReloading(false);
    }
  }
  async function pdf(download: boolean) {
    if (!editor || busy) return;
    if (!settings.title.trim()) {
      setError("Donnez un titre au modèle avant de l’exporter.");
      return;
    }
    setPdfPending(true);
    setError("");
    try {
      const blob = await contractDesignApi.pdf(initial.companyId, {
        ...settings,
        content: editor.getJSON(),
      });
      const url = URL.createObjectURL(blob);
      if (download) {
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `${
          settings.title
            .replace(/[<>:"/\\|?*]/g, "-")
            .replace(/\p{Cc}/gu, "-")
            .trim() || "modele-contrat"
        }.pdf`;
        document.body.append(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      } else {
        setPreviewBlob(blob);
        setPreviewUrl(url);
      }
    } catch (caught) {
      setError(
        await contractDesignError(
          caught,
          "Impossible de générer le PDF du contrat.",
        ),
      );
    } finally {
      setPdfPending(false);
    }
  }
  const pageWidth = settings.paperFormat === "A4" ? 210 : 215.9;
  const pageHeight = settings.paperFormat === "A4" ? 297 : 279.4;
  const pageStyle = {
    "--contract-page-width": `${settings.orientation === "PORTRAIT" ? pageWidth : pageHeight}mm`,
    "--contract-page-height": `${settings.orientation === "PORTRAIT" ? pageHeight : pageWidth}mm`,
    "--contract-page-margin": `${settings.marginMm}mm`,
    "--contract-zoom": zoom / 100,
  } as CSSProperties;
  return (
    <div className="contract-editor" aria-busy={busy}>
      <div className="contract-document-bar">
        <div className="contract-document-heading">
          <span className="contract-file-icon">
            <Icon name="invoice" size={24} />
          </span>
          <label>
            <span className="sr-only">Titre du modèle</span>
            <input
              aria-label="Titre du modèle"
              maxLength={160}
              value={settings.title}
              disabled={busy}
              onChange={(event) => change("title", event.target.value)}
            />
            <small>
              {companyName} <span aria-hidden="true">·</span>{" "}
              {dirty
                ? "Modifications non enregistrées"
                : saved.customized
                  ? "Enregistré"
                  : "Modèle à personnaliser"}
            </small>
          </label>
        </div>
        <div className="contract-document-actions">
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => void pdf(false)}
          >
            <Icon name="eye" size={16} /> Aperçu PDF
          </button>
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => void pdf(true)}
          >
            <Icon name="download" size={16} /> Exporter PDF
          </button>
          <button type="button" disabled={busy} onClick={() => void save()}>
            {saving ? "Enregistrement…" : "Enregistrer"}
          </button>
        </div>
      </div>
      {error && (
        <div className="contract-editor-message form-error" role="alert">
          {error}
          <button
            type="button"
            className="link-button"
            disabled={busy}
            onClick={() => setConfirmation("reload")}
          >
            Recharger le modèle enregistré
          </button>
        </div>
      )}
      {success && (
        <p className="contract-editor-message success-message" role="status">
          {success}
        </p>
      )}
      {branding.isError && (
        <div className="contract-editor-message form-error" role="alert">
          Impossible de charger le logo de l’entreprise.{" "}
          <button
            type="button"
            className="link-button"
            onClick={() => void branding.refetch()}
          >
            Réessayer
          </button>
        </div>
      )}
      <div
        className="contract-ribbon-tabs"
        role="tablist"
        aria-label="Outils du document"
      >
        {(
          [
            { id: "home", label: "Accueil" },
            { id: "insert", label: "Insertion" },
            { id: "layout", label: "Mise en page" },
          ] as const
        ).map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            id={`contract-tab-${item.id}`}
            aria-controls="contract-ribbon-panel"
            aria-selected={tab === item.id}
            tabIndex={tab === item.id ? 0 : -1}
            onClick={() => setTab(item.id)}
            onKeyDown={(event) => {
              const tabs = ["home", "insert", "layout"] as const;
              const index = tabs.indexOf(item.id);
              const next =
                event.key === "ArrowRight"
                  ? (index + 1) % 3
                  : event.key === "ArrowLeft"
                    ? (index + 2) % 3
                    : event.key === "Home"
                      ? 0
                      : event.key === "End"
                        ? 2
                        : -1;
              if (next >= 0) {
                event.preventDefault();
                setTab(tabs[next]);
                document.getElementById(`contract-tab-${tabs[next]}`)?.focus();
              }
            }}
          >
            {item.label}
          </button>
        ))}
        <button
          type="button"
          className="contract-reload"
          disabled={busy}
          onClick={() => setConfirmation("reload")}
        >
          Recharger
        </button>
      </div>
      <div
        className="contract-ribbon"
        id="contract-ribbon-panel"
        role="tabpanel"
        aria-labelledby={`contract-tab-${tab}`}
      >
        {editor && tab === "home" && (
          <HomeToolbar editor={editor} disabled={busy} />
        )}
        {editor && tab === "insert" && (
          <InsertToolbar
            editor={editor}
            disabled={busy}
            onBlank={() => setConfirmation("blank")}
          />
        )}
        {tab === "layout" && (
          <>
            <label>
              Format
              <select
                aria-label="Format du papier"
                value={settings.paperFormat}
                disabled={busy}
                onChange={(event) =>
                  change(
                    "paperFormat",
                    event.target.value as ContractDesignSettings["paperFormat"],
                  )
                }
              >
                <option value="A4">A4</option>
                <option value="LETTER">Lettre</option>
              </select>
            </label>
            <label>
              Orientation
              <select
                aria-label="Orientation du document"
                value={settings.orientation}
                disabled={busy}
                onChange={(event) =>
                  change(
                    "orientation",
                    event.target.value as ContractDesignSettings["orientation"],
                  )
                }
              >
                <option value="PORTRAIT">Portrait</option>
                <option value="LANDSCAPE">Paysage</option>
              </select>
            </label>
            <label>
              Marges
              <select
                aria-label="Marges du document"
                value={settings.marginMm}
                disabled={busy}
                onChange={(event) =>
                  change("marginMm", Number(event.target.value))
                }
              >
                {[10, 15, 20, 25, 30, 35].map((value) => (
                  <option key={value} value={value}>
                    {value} mm
                  </option>
                ))}
              </select>
            </label>
            <label className="contract-checkbox">
              <input
                type="checkbox"
                checked={settings.showLogo}
                disabled={busy}
                onChange={(event) => change("showLogo", event.target.checked)}
              />{" "}
              Logo de l’entreprise
            </label>
            <label className="contract-checkbox">
              <input
                type="checkbox"
                checked={settings.pageNumbers}
                disabled={busy}
                onChange={(event) =>
                  change("pageNumbers", event.target.checked)
                }
              />{" "}
              Numéros de page
            </label>
          </>
        )}
      </div>
      {editor && <TableToolbar editor={editor} disabled={busy} />}
      <div className="contract-canvas" style={pageStyle}>
        <div className="contract-paper">
          {settings.showLogo && branding.data?.logoDataUrl && (
            <img
              className="contract-paper-logo"
              src={branding.data.logoDataUrl}
              alt={`Logo de ${companyName}`}
            />
          )}
          <EditorContent editor={editor} />
        </div>
      </div>
      <div className="contract-statusbar">
        {editor && <WordCount editor={editor} />}
        <span>
          {pdfPending
            ? "Génération du PDF…"
            : saved.updatedAt
              ? `Dernier enregistrement : ${formatDateTime(saved.updatedAt)}`
              : "Aucun enregistrement"}
        </span>
        <label>
          Zoom
          <select
            aria-label="Zoom du document"
            value={zoom}
            onChange={(event) => setZoom(Number(event.target.value))}
          >
            {[50, 75, 100, 125, 150].map((value) => (
              <option key={value} value={value}>
                {value} %
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="contract-editor-hint">
        Raccourcis : ⌘ / Ctrl + S pour enregistrer, ⌘ / Ctrl + Z pour annuler.
        Consultez l’aperçu PDF pour vérifier la pagination finale.
      </p>
      <ConfirmDialog
        open={confirmation !== null}
        title={
          confirmation === "blank"
            ? "Commencer un document vierge ?"
            : "Recharger le modèle enregistré ?"
        }
        description="Le contenu en cours sera remplacé. Les modifications non enregistrées seront perdues."
        confirmLabel={
          confirmation === "blank" ? "Créer un document vierge" : "Recharger"
        }
        onClose={() => setConfirmation(null)}
        onConfirm={() => {
          if (confirmation === "reload") void reload();
          else {
            editor?.commands.setContent({
              type: "doc",
              content: [{ type: "paragraph" }],
            });
            setConfirmation(null);
          }
        }}
      />
      <dialog
        ref={previewDialog}
        className="contract-pdf-dialog"
        aria-labelledby="contract-pdf-title"
        onCancel={() => {
          setPreviewUrl(null);
          setPreviewBlob(null);
        }}
      >
        <div className="contract-pdf-heading">
          <div>
            <h2 id="contract-pdf-title">Aperçu PDF du contrat</h2>
            <p>
              {companyName} · {settings.title}
            </p>
          </div>
          <button
            type="button"
            className="secondary"
            onClick={() => {
              setPreviewUrl(null);
              setPreviewBlob(null);
            }}
            aria-label="Fermer l’aperçu PDF"
          >
            Fermer
          </button>
        </div>
        {previewUrl && previewBlob && (
          <Suspense fallback={<p role="status">Chargement du lecteur PDF…</p>}>
            <ContractPdfPreview url={previewUrl} blob={previewBlob} />
          </Suspense>
        )}
      </dialog>
    </div>
  );
}

function ToolButton({
  label,
  active,
  disabled,
  children,
  onClick,
}: {
  label: string;
  active?: boolean;
  disabled?: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className="contract-tool-button"
      aria-label={label}
      title={label}
      aria-pressed={active}
      disabled={disabled}
      onMouseDown={(event) => event.preventDefault()}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function InsertToolbar({
  editor,
  disabled,
  onBlank,
}: {
  editor: Editor;
  disabled: boolean;
  onBlank: () => void;
}) {
  const inStructure = useEditorState({
    editor,
    selector: ({ editor }) =>
      editor.isActive("table") || editor.isActive("listItem"),
  });
  return (
    <>
      <div className="contract-tool-group">
        <button
          type="button"
          className="secondary"
          disabled={disabled || inStructure}
          onClick={() =>
            editor
              .chain()
              .focus()
              .insertTable({ rows: 3, cols: 2, withHeaderRow: true })
              .run()
          }
        >
          <Icon name="columns" size={16} /> Tableau
        </button>
        <button
          type="button"
          className="secondary"
          disabled={disabled || inStructure}
          onClick={() =>
            editor.chain().focus().insertContent({ type: "pageBreak" }).run()
          }
        >
          Saut de page
        </button>
        <button
          type="button"
          className="secondary"
          disabled={disabled}
          onClick={() => editor.chain().focus().setHorizontalRule().run()}
        >
          Séparateur
        </button>
      </div>
      <div className="contract-tool-group">
        <label>
          Champ du modèle
          <select
            aria-label="Insérer un champ du modèle"
            value=""
            disabled={disabled}
            onChange={(event) => {
              if (event.target.value)
                editor
                  .chain()
                  .focus()
                  .insertContent([
                    {
                      type: "templateField",
                      attrs: { key: event.target.value },
                    },
                    { type: "text", text: " " },
                  ])
                  .run();
            }}
          >
            <option value="">Choisir un champ…</option>
            {Object.entries(contractFields).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <button
        type="button"
        className="secondary"
        disabled={disabled}
        onClick={onBlank}
      >
        Document vierge
      </button>
      <p className="contract-ribbon-help">
        {inStructure
          ? "Placez le curseur hors du tableau ou de la liste pour insérer un tableau ou un saut de page."
          : "Les coordonnées de l’entreprise sont remplies dans le PDF. Les champs du client et du contrat restent à compléter."}
      </p>
    </>
  );
}

function HomeToolbar({
  editor,
  disabled,
}: {
  editor: Editor;
  disabled: boolean;
}) {
  const state = useEditorState({
    editor,
    selector: ({ editor }) => ({
      bold: editor.isActive("bold"),
      italic: editor.isActive("italic"),
      underline: editor.isActive("underline"),
      strike: editor.isActive("strike"),
      highlight: editor.isActive("highlight"),
      bullet: editor.isActive("bulletList"),
      ordered: editor.isActive("orderedList"),
      alignment:
        editor.getAttributes("paragraph").textAlign ??
        editor.getAttributes("heading").textAlign ??
        "left",
      family: editor.getAttributes("textStyle").fontFamily ?? "Arial",
      size:
        editor.getAttributes("textStyle").fontSize ??
        (editor.isActive("heading", { level: 1 })
          ? "24pt"
          : editor.isActive("heading", { level: 2 })
            ? "18pt"
            : editor.isActive("heading", { level: 3 })
              ? "14pt"
              : "12pt"),
      color: editor.getAttributes("textStyle").color ?? "#111827",
      level: editor.isActive("heading")
        ? String(editor.getAttributes("heading").level)
        : "paragraph",
      undo: editor.can().undo(),
      redo: editor.can().redo(),
    }),
  });
  return (
    <>
      <div className="contract-tool-group">
        <ToolButton
          label="Annuler la dernière action"
          disabled={disabled || !state.undo}
          onClick={() => editor.chain().focus().undo().run()}
        >
          ↶
        </ToolButton>
        <ToolButton
          label="Rétablir la dernière action"
          disabled={disabled || !state.redo}
          onClick={() => editor.chain().focus().redo().run()}
        >
          ↷
        </ToolButton>
      </div>
      <div className="contract-tool-group">
        <label>
          <span className="sr-only">Style de paragraphe</span>
          <select
            aria-label="Style de paragraphe"
            value={state.level}
            disabled={disabled}
            onChange={(event) => {
              if (event.target.value === "paragraph")
                editor.chain().focus().setParagraph().run();
              else
                editor
                  .chain()
                  .focus()
                  .setHeading({
                    level: Number(event.target.value) as 1 | 2 | 3,
                  })
                  .run();
            }}
          >
            <option value="paragraph">Normal</option>
            <option value="1">Titre 1</option>
            <option value="2">Titre 2</option>
            <option value="3">Titre 3</option>
          </select>
        </label>
        <label>
          <span className="sr-only">Police</span>
          <select
            aria-label="Police du texte"
            value={state.family.replace(/["']/g, "")}
            disabled={disabled}
            onChange={(event) =>
              editor.chain().focus().setFontFamily(event.target.value).run()
            }
          >
            <option>Arial</option>
            <option>Times New Roman</option>
            <option>Courier New</option>
          </select>
        </label>
        <label>
          <span className="sr-only">Taille du texte</span>
          <select
            aria-label="Taille du texte"
            value={state.size}
            disabled={disabled}
            onChange={(event) =>
              editor.chain().focus().setFontSize(event.target.value).run()
            }
          >
            {[8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72].map(
              (size) => (
                <option key={size} value={`${size}pt`}>
                  {size}
                </option>
              ),
            )}
          </select>
        </label>
      </div>
      <div className="contract-tool-group">
        <ToolButton
          label="Gras"
          active={state.bold}
          disabled={disabled}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          <strong>G</strong>
        </ToolButton>
        <ToolButton
          label="Italique"
          active={state.italic}
          disabled={disabled}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          <i>I</i>
        </ToolButton>
        <ToolButton
          label="Souligné"
          active={state.underline}
          disabled={disabled}
          onClick={() => editor.chain().focus().toggleUnderline().run()}
        >
          <u>S</u>
        </ToolButton>
        <ToolButton
          label="Barré"
          active={state.strike}
          disabled={disabled}
          onClick={() => editor.chain().focus().toggleStrike().run()}
        >
          <s>ab</s>
        </ToolButton>
        <label className="contract-color" title="Couleur du texte">
          <span>A</span>
          <input
            type="color"
            aria-label="Couleur du texte"
            value={
              /^#[0-9a-f]{6}$/i.test(state.color) ? state.color : "#111827"
            }
            disabled={disabled}
            onChange={(event) =>
              editor.chain().focus().setColor(event.target.value).run()
            }
          />
        </label>
        <ToolButton
          label="Surligner"
          active={state.highlight}
          disabled={disabled}
          onClick={() =>
            editor.chain().focus().toggleHighlight({ color: "#fef08a" }).run()
          }
        >
          <span className="contract-highlight-icon">ab</span>
        </ToolButton>
      </div>
      <div className="contract-tool-group">
        {(["left", "center", "right", "justify"] as const).map((alignment) => (
          <ToolButton
            key={alignment}
            label={
              {
                left: "Aligner à gauche",
                center: "Centrer",
                right: "Aligner à droite",
                justify: "Justifier",
              }[alignment]
            }
            active={state.alignment === alignment}
            disabled={disabled}
            onClick={() => editor.chain().focus().setTextAlign(alignment).run()}
          >
            <AlignmentIcon alignment={alignment} />
          </ToolButton>
        ))}
        <ToolButton
          label="Liste à puces"
          active={state.bullet}
          disabled={disabled}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          • ≡
        </ToolButton>
        <ToolButton
          label="Liste numérotée"
          active={state.ordered}
          disabled={disabled}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          1. ≡
        </ToolButton>
      </div>
      <ToolButton
        label="Effacer la mise en forme"
        disabled={disabled}
        onClick={() =>
          editor.chain().focus().unsetAllMarks().clearNodes().run()
        }
      >
        Tₓ
      </ToolButton>
    </>
  );
}

function AlignmentIcon({
  alignment,
}: {
  alignment: "left" | "center" | "right" | "justify";
}) {
  const start = alignment === "right" ? 8 : alignment === "center" ? 5 : 3;
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      aria-hidden="true"
    >
      <path
        d={`M3 5h18M${start} 10h${alignment === "justify" ? 18 : 13}M3 15h18M${start} 20h${alignment === "justify" ? 18 : 13}`}
      />
    </svg>
  );
}

function TableToolbar({
  editor,
  disabled,
}: {
  editor: Editor;
  disabled: boolean;
}) {
  const inTable = useEditorState({
    editor,
    selector: ({ editor }) => editor.isActive("table"),
  });
  if (!inTable) return null;
  return (
    <div
      className="contract-table-tools"
      role="group"
      aria-label="Outils du tableau"
    >
      <span>Tableau</span>
      <button
        type="button"
        disabled={disabled}
        onClick={() => editor.chain().focus().addRowAfter().run()}
      >
        + Ligne
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={() => editor.chain().focus().addColumnAfter().run()}
      >
        + Colonne
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={() => editor.chain().focus().deleteRow().run()}
      >
        Supprimer la ligne
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={() => editor.chain().focus().deleteColumn().run()}
      >
        Supprimer la colonne
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={() => editor.chain().focus().deleteTable().run()}
      >
        Supprimer le tableau
      </button>
    </div>
  );
}

function WordCount({ editor }: { editor: Editor }) {
  const count = useEditorState({
    editor,
    selector: ({ editor }) =>
      editor.getText().trim().split(/\s+/).filter(Boolean).length,
  });
  return (
    <span>
      {count} mot{count > 1 ? "s" : ""}
    </span>
  );
}
