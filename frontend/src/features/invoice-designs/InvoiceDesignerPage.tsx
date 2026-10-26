import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { PageHeader } from "../../components/ui/PageHeader";
import { Icon } from "../../components/ui/Icon";
import { EmptyState } from "../../components/ui/EmptyState";
import { ConfirmDialog } from "../../components/ui/ConfirmDialog";
import { companyApi } from "../companies/api/companyApi";
import {
  useCurrentCompany,
  useCompanyBranding,
} from "../companies/hooks/useCompanies";
import {
  invoiceDesignApi,
  designError,
  type InvoiceDesign,
  type InvoiceDesignSettings,
} from "./invoiceDesignApi";
import "./invoice-designer.css";
import { InvoiceFreeDesigner } from "./InvoiceFreeDesigner";
import { createLayout } from "./invoiceLayout";
import { InvoicePdfPreview } from "./InvoicePdfPreview";

export function InvoiceDesignerPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const platform = user?.role === "APP_ADMIN";
  const current = useCurrentCompany();
  const [companyId, setCompanyId] = useState(user?.companyId ?? "");
  const [pendingCompany, setPendingCompany] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
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
      if (!busy)
        setPendingRoute(
          destination.pathname + destination.search + destination.hash,
        );
    }
    document.addEventListener("click", protectNavigation, true);
    return () => document.removeEventListener("click", protectNavigation, true);
  }, [dirty, busy]);
  const companies = useQuery({
    queryKey: ["companies", "list"],
    queryFn: companyApi.list,
    enabled: platform,
  });
  const design = useQuery({
    queryKey: ["invoice-design", companyId],
    queryFn: () => invoiceDesignApi.get(companyId),
    enabled: Boolean(companyId),
    refetchOnWindowFocus: false,
  });
  const companyName = platform
    ? (companies.data?.content.find((company) => company.id === companyId)
        ?.companyName ?? "Entreprise sélectionnée")
    : (current.data?.companyName ?? "Votre entreprise");
  function select(next: string) {
    if (busy || next === companyId) return;
    if (dirty) setPendingCompany(next);
    else setCompanyId(next);
  }
  return (
    <section
      className="invoice-designer-page"
      data-testid="invoice-designer-page"
    >
      <PageHeader
        eyebrow="Administration"
        title="Design des factures"
        description="Un modèle commun à tous les clients de l’entreprise, actuels et futurs."
        action={
          <Link className="button-link secondary-link" to="/invoices">
            Voir les factures
          </Link>
        }
      />
      <div className="designer-company-bar">
        {platform ? (
          <label>
            <span>Entreprise</span>
            <select
              aria-label="Entreprise du modèle"
              value={companyId}
              disabled={busy || companies.isPending}
              onChange={(event) => select(event.target.value)}
            >
              <option value="">Choisir une entreprise</option>
              {companies.data?.content.map((company) => (
                <option key={company.id} value={company.id}>
                  {company.companyName}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <div className="designer-company">
            <Icon name="building" />
            <strong>{current.data?.companyName}</strong>
          </div>
        )}
        <p className="designer-shared-notice">
          Ce design sera utilisé pour toutes les prochaines factures de cette
          entreprise.
        </p>
      </div>
      {(companies.isError || (!platform && current.isError)) && (
        <div className="form-error" role="alert">
          Impossible de charger les entreprises.
          <button
            type="button"
            className="link-button"
            onClick={() => {
              if (platform) void companies.refetch();
              else void current.refetch();
            }}
          >
            Réessayer
          </button>
        </div>
      )}
      {!companyId ? (
        <EmptyState
          icon="building"
          title="Choisissez une entreprise"
          description="Son modèle de facture s’appliquera à tous ses clients."
        />
      ) : design.isPending ? (
        <div className="loading-state" role="status">
          Chargement du modèle…
        </div>
      ) : design.isError ? (
        <EmptyState
          icon="invoice"
          title="Impossible de charger le modèle"
          action={
            <button className="secondary" onClick={() => void design.refetch()}>
              Réessayer
            </button>
          }
        />
      ) : (
        <InvoiceDesignEditor
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
        description="Les modifications non enregistrées seront perdues."
        confirmLabel="Changer sans enregistrer"
        onClose={() => setPendingCompany(null)}
        onConfirm={() => {
          if (pendingCompany !== null) setCompanyId(pendingCompany);
          setPendingCompany(null);
          setDirty(false);
        }}
      />
      <ConfirmDialog
        open={pendingRoute !== null}
        title="Quitter le modèle de facture ?"
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

function InvoiceDesignEditor({
  initial,
  companyName,
  onDirty,
  onBusy,
}: {
  initial: InvoiceDesign;
  companyName: string;
  onDirty: (value: boolean) => void;
  onBusy: (value: boolean) => void;
}) {
  const cache = useQueryClient();
  const form = useRef<HTMLFormElement>(null);
  const [saved, setSaved] = useState(initial);
  const [settings, setSettings] = useState(initial.settings);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [reloading, setReloading] = useState(false);
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewBlob, setPreviewBlob] = useState<Blob | null>(null);
  const [pendingLayout, setPendingLayout] = useState<
    "automatic" | "free" | "framed" | null
  >(null);
  const [previewError, setPreviewError] = useState("");
  const [previewPending, setPreviewPending] = useState(true);
  const [previewRevision, setPreviewRevision] = useState(0);
  const { user } = useAuth();
  const branding = useCompanyBranding(
    initial.companyId,
    user?.role === "APP_ADMIN",
  );
  const dirty = JSON.stringify(settings) !== JSON.stringify(saved.settings);
  const mutation = useMutation({
    mutationFn: () =>
      invoiceDesignApi.save(initial.companyId, settings, saved.version),
  });
  const busy = mutation.isPending || reloading;
  const footerError =
    settings.footerText.split(/\r?\n/).length > 4
      ? "Le pied de page est limité à quatre lignes"
      : "";
  const canSave =
    !busy &&
    !footerError &&
    !(settings.layout && (previewPending || previewError)) &&
    !(saved.customized && !dirty);
  useEffect(() => {
    function saveShortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        if (canSave) form.current?.requestSubmit();
      }
    }
    document.addEventListener("keydown", saveShortcut);
    return () => document.removeEventListener("keydown", saveShortcut);
  }, [canSave]);
  useEffect(() => {
    // Query notifications may arrive after save state; never restore older props over that save.
    const newer = initial.version !== null && (saved.version === null || initial.version > saved.version);
    if (!dirty && !busy && newer) {
      setSaved(initial);
      setSettings(initial.settings);
    }
  }, [initial, saved, dirty, busy]);
  useEffect(() => onDirty(dirty), [dirty, onDirty]);
  useEffect(() => {
    onBusy(busy);
    return () => onBusy(false);
  }, [busy, onBusy]);
  useEffect(() => {
    if (!dirty) return;
    const beforeUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [dirty]);
  useEffect(() => {
    const controller = new AbortController();
    setPreviewPending(true);
    setPreviewError("");
    const timer = window.setTimeout(async () => {
      try {
        const blob = await invoiceDesignApi.preview(
          initial.companyId,
          settings,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          setPreviewUrl(URL.createObjectURL(blob));
          setPreviewBlob(blob);
        }
      } catch (caught) {
        const message = await designError(
          caught,
          "Impossible de générer l’aperçu PDF.",
        );
        if (!controller.signal.aborted) setPreviewError(message);
      } finally {
        if (!controller.signal.aborted) setPreviewPending(false);
      }
    }, 450);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [initial.companyId, settings, previewRevision, branding.data?.updatedAt]);
  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );
  function change<K extends keyof InvoiceDesignSettings>(
    name: K,
    value: InvoiceDesignSettings[K],
  ) {
    setSettings((current) => ({ ...current, [name]: value }));
    setSuccess("");
  }
  function applyLayout(mode: "automatic" | "free" | "framed") {
    setSettings((current) => ({
      ...current,
      layout:
        mode === "automatic" ? null : createLayout(current, mode === "framed"),
      template: mode === "framed" ? "CLASSIC" : current.template,
    }));
    setSuccess("");
    setPendingLayout(null);
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");
    try {
      const updated = await mutation.mutateAsync();
      setSaved(updated);
      setSettings(updated.settings);
      cache.setQueryData(["invoice-design", initial.companyId], updated);
      setSuccess(`Modèle enregistré pour tous les clients de ${companyName}.`);
    } catch (caught) {
      setError(
        await designError(caught, "Impossible d’enregistrer le modèle."),
      );
    }
  }
  async function reload() {
    setReloading(true);
    setError("");
    setSuccess("");
    try {
      const fresh = await invoiceDesignApi.get(initial.companyId);
      setSaved(fresh);
      setSettings(fresh.settings);
      cache.setQueryData(["invoice-design", initial.companyId], fresh);
    } catch (caught) {
      setError(await designError(caught, "Impossible de recharger le modèle."));
    } finally {
      setReloading(false);
    }
  }
  const toggles: {
    key:
      | "showProductCode"
      | "showDescription"
      | "showUnit"
      | "showDiscount"
      | "showVat";
    label: string;
  }[] = [
    { key: "showProductCode", label: "Code produit" },
    { key: "showDescription", label: "Description des articles" },
    { key: "showUnit", label: "Unité" },
    { key: "showDiscount", label: "Remise par ligne" },
    { key: "showVat", label: "Taux de TVA par ligne" },
  ];
  return (
    <form ref={form} className="designer-editor" onSubmit={save}>
      <div className="designer-workspace-heading">
        <div>
          <span className="designer-kicker">Modèle de l’entreprise</span>
          <h2>{companyName}</h2>
        </div>
        <span className={`designer-state ${dirty ? "is-dirty" : ""}`}>
          {dirty
            ? "Modifications non enregistrées"
            : saved.customized
              ? "Modèle personnalisé"
              : "Nouveau modèle"}
        </span>
      </div>
      {error && (
        <div className="form-error" role="alert">
          {error}
          <button
            type="button"
            className="link-button"
            disabled={busy}
            onClick={() => void reload()}
          >
            Recharger le modèle enregistré
          </button>
        </div>
      )}
      {success && (
        <div className="toast" role="status">
          {success}
        </div>
      )}
      <div className="designer-layout-modes" aria-label="Mode de composition">
        <button
          type="button"
          className="secondary"
          disabled={busy}
          aria-pressed={!settings.layout}
          onClick={() =>
            settings.layout ? setPendingLayout("automatic") : undefined
          }
        >
          Mise en page automatique
        </button>
        <button
          type="button"
          className="secondary"
          disabled={busy}
          aria-pressed={Boolean(settings.layout)}
          onClick={() => {
            if (!settings.layout) applyLayout("free");
          }}
        >
          Placement libre
        </button>
        <button
          type="button"
          className="secondary"
          disabled={busy}
          onClick={() =>
            settings.layout ? setPendingLayout("framed") : applyLayout("framed")
          }
        >
          Modèle à cadres
        </button>
      </div>
      {settings.layout && (
        <InvoiceFreeDesigner
          settings={{ ...settings, layout: settings.layout }}
          disabled={busy}
          onChange={(next) => {
            setSettings(next);
            setSuccess("");
          }}
          blob={previewBlob}
          pending={previewPending}
          previewError={previewError}
          canSave={canSave}
          onSave={() => form.current?.requestSubmit()}
          downloadUrl={
            !previewPending && !previewError ? previewUrl : undefined
          }
          saveError={error}
          saveSuccess={success}
        />
      )}
      <details
        className={`designer-document-settings ${settings.layout ? "is-free" : ""}`}
        open={!settings.layout}
      >
        <summary>
          Réglages du document · Police, couleurs, colonnes et pied de page
        </summary>
        <div className="designer-workspace">
          <fieldset className="designer-controls" disabled={busy}>
            <legend className="sr-only">Personnalisation du modèle</legend>
            <section>
              <h3>
                <Icon name="columns" size={16} /> Mise en page
              </h3>
              <div className="designer-templates">
                {(
                  [
                    ["CLASSIC", "Classique"],
                    ["MODERN", "Moderne"],
                    ["MINIMAL", "Épuré"],
                  ] as const
                ).map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={settings.template === value}
                    onClick={() => change("template", value)}
                  >
                    <span
                      aria-hidden="true"
                      className={`designer-template-mini template-${value.toLowerCase()}`}
                    >
                      <i />
                      <i />
                      <i />
                    </span>
                    {label}
                  </button>
                ))}
              </div>
              <div className="designer-fields">
                <label>
                  <span>Format</span>
                  <select
                    value={settings.paperFormat}
                    onChange={(event) =>
                      change(
                        "paperFormat",
                        event.target
                          .value as InvoiceDesignSettings["paperFormat"],
                      )
                    }
                  >
                    <option value="A4">A4</option>
                    <option value="LETTER">Lettre US</option>
                  </select>
                </label>
                <label>
                  <span>Taille du texte</span>
                  <select
                    value={settings.fontSize}
                    onChange={(event) =>
                      change("fontSize", Number(event.target.value))
                    }
                  >
                    {[9, 10, 11, 12].map((size) => (
                      <option key={size} value={size}>
                        {size} pt
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <label>
                <span>Police</span>
                <select
                  value={settings.fontFamily}
                  onChange={(event) =>
                    change(
                      "fontFamily",
                      event.target.value as InvoiceDesignSettings["fontFamily"],
                    )
                  }
                >
                  <option value="SANS">Helvetica · Sans empattement</option>
                  <option value="SERIF">Times · Avec empattement</option>
                  <option value="MONO">Courier · Monospace</option>
                </select>
              </label>
              <label className="designer-color">
                <span>Couleur principale</span>
                <input
                  type="color"
                  value={settings.accentColor}
                  onChange={(event) =>
                    change("accentColor", event.target.value)
                  }
                />
                <output>{settings.accentColor.toUpperCase()}</output>
              </label>
            </section>
            <section>
              <h3>
                <Icon name="building" size={16} /> En-tête
              </h3>
              <label className="designer-checkbox">
                <input
                  type="checkbox"
                  checked={settings.showLogo}
                  onChange={(event) => change("showLogo", event.target.checked)}
                />
                <span>Afficher le logo de l’entreprise</span>
              </label>
              {branding.data?.logoDataUrl && (
                <img
                  className="designer-logo"
                  src={branding.data.logoDataUrl}
                  alt="Logo de l’entreprise"
                />
              )}
              {branding.isError && (
                <p role="alert" className="field-error">
                  Logo indisponible.
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => void branding.refetch()}
                  >
                    Réessayer
                  </button>
                </p>
              )}
              {!settings.layout && (
                <div className="designer-fields">
                  <label>
                    <span>Bloc entreprise</span>
                    <select
                      value={settings.logoPosition}
                      onChange={(event) =>
                        change(
                          "logoPosition",
                          event.target
                            .value as InvoiceDesignSettings["logoPosition"],
                        )
                      }
                    >
                      <option value="LEFT">À gauche</option>
                      <option value="RIGHT">À droite</option>
                    </select>
                  </label>
                  <label>
                    <span>Bloc client</span>
                    <select
                      value={settings.clientPosition}
                      onChange={(event) =>
                        change(
                          "clientPosition",
                          event.target
                            .value as InvoiceDesignSettings["clientPosition"],
                        )
                      }
                    >
                      <option value="LEFT">À gauche</option>
                      <option value="RIGHT">À droite</option>
                    </select>
                  </label>
                </div>
              )}
              <Link
                className="designer-logo-link"
                to={
                  user?.role === "APP_ADMIN"
                    ? `/companies/${initial.companyId}`
                    : "/settings"
                }
                target="_blank"
                rel="noreferrer"
              >
                Gérer le logo de l’entreprise ↗
              </Link>
            </section>
            <section>
              <h3>
                <Icon name="invoice" size={16} /> Tableau des articles
              </h3>
              {toggles.map(({ key, label }) => (
                <label key={key} className="designer-checkbox">
                  <input
                    type="checkbox"
                    checked={settings[key]}
                    onChange={(event) => change(key, event.target.checked)}
                  />
                  <span>{label}</span>
                </label>
              ))}
            </section>
            <section>
              <h3>
                <Icon name="edit" size={16} /> Bas de facture
              </h3>
              <label className="designer-checkbox">
                <input
                  type="checkbox"
                  checked={settings.showAmountInWords}
                  onChange={(event) =>
                    change("showAmountInWords", event.target.checked)
                  }
                />
                <span>Montant en lettres</span>
              </label>
              <label>
                <span>Pied de page</span>
                <textarea
                  ref={(node) => node?.setCustomValidity(footerError)}
                  aria-invalid={Boolean(footerError)}
                  aria-describedby={
                    footerError ? "designer-footer-error" : undefined
                  }
                  rows={4}
                  maxLength={300}
                  value={settings.footerText}
                  placeholder="Coordonnées bancaires, remerciements…"
                  onChange={(event) => change("footerText", event.target.value)}
                />
              </label>
              {footerError && (
                <p id="designer-footer-error" className="field-error">
                  {footerError}
                </p>
              )}
              <span className="designer-character-count">
                {settings.footerText.length}/300
              </span>
            </section>
          </fieldset>
          {!settings.layout && (
            <section
              className="designer-preview"
              aria-label="Aperçu de la facture"
              aria-busy={previewPending}
            >
              <div className="designer-preview-toolbar">
                <strong>Aperçu PDF</strong>
                <span>
                  {previewPending ? "Actualisation…" : "Données d’exemple"}
                </span>
                {previewUrl && !previewPending && !previewError && (
                  <a
                    href={previewUrl}
                    download="apercu-facture.pdf"
                    aria-label="Télécharger l’aperçu PDF"
                  >
                    <Icon name="download" size={16} />
                  </a>
                )}
              </div>
              {previewError ? (
                <EmptyState
                  icon="invoice"
                  title="Aperçu indisponible"
                  description={previewError}
                  action={
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setPreviewRevision((value) => value + 1)}
                    >
                      Réessayer l’aperçu
                    </button>
                  }
                />
              ) : previewBlob ? (
                <InvoicePdfPreview
                  blob={previewBlob}
                  paperFormat={settings.paperFormat}
                  title={`Aperçu PDF de ${companyName}`}
                />
              ) : (
                <div className="designer-preview-loading" role="status">
                  <Icon name="invoice" size={32} />
                  Préparation de l’aperçu…
                </div>
              )}
            </section>
          )}
        </div>
      </details>
      <ConfirmDialog
        open={pendingLayout !== null}
        title="Remplacer la disposition ?"
        description="Les positions, textes libres et rectangles de la composition actuelle seront remplacés. Les réglages du document seront conservés."
        confirmLabel="Remplacer la disposition"
        onClose={() => setPendingLayout(null)}
        onConfirm={() => {
          if (pendingLayout) applyLayout(pendingLayout);
        }}
      />
      <footer className="designer-actions">
        <button
          type="button"
          className="secondary"
          disabled={busy || !dirty}
          onClick={() => {
            setSettings(saved.settings);
            setError("");
            setSuccess("");
          }}
        >
          Annuler les modifications
        </button>
        {settings.layout && previewUrl && !previewPending && !previewError && (
          <a
            className="button-link secondary-link"
            href={previewUrl}
            download="apercu-facture.pdf"
          >
            Télécharger le PDF
          </a>
        )}
        <button type="submit" disabled={!canSave}>
          <Icon name="status" size={16} />
          {mutation.isPending
            ? "Enregistrement…"
            : "Appliquer à tous les clients"}
        </button>
      </footer>
    </form>
  );
}
