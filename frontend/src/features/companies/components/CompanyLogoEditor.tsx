import axios from "axios";
import { useRef, useState, type ChangeEvent } from "react";
import { companyApi } from "../api/companyApi";
import { useCompanyBranding } from "../hooks/useCompanies";

export function CompanyLogoEditor({
  companyId,
  companyName,
}: {
  companyId?: string;
  companyName: string;
}) {
  const branding = useCompanyBranding(companyId, Boolean(companyId));
  const input = useRef<HTMLInputElement>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");

  async function selectLogo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setError("");
    setToast("");
    if (!["image/png", "image/jpeg"].includes(file.type)) {
      setError("Choisissez une image PNG ou JPEG.");
      event.target.value = "";
      return;
    }
    if (file.size > 512 * 1024) {
      setError("Le logo ne doit pas dépasser 512 Ko.");
      event.target.value = "";
      return;
    }
    setSaving(true);
    try {
      await companyApi.uploadLogo(file, companyId);
      await branding.refetch();
      setToast("Le logo de l’entreprise a été mis à jour.");
    } catch (caught) {
      setError(
        message(caught, "Impossible d’importer le logo de l’entreprise."),
      );
    } finally {
      setSaving(false);
      event.target.value = "";
    }
  }

  async function removeLogo() {
    setSaving(true);
    setError("");
    setToast("");
    try {
      await companyApi.removeLogo(companyId);
      await branding.refetch();
      setToast("Le logo de l’entreprise a été supprimé.");
    } catch (caught) {
      setError(
        message(caught, "Impossible de supprimer le logo de l’entreprise."),
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section
      className="company-branding-editor"
      aria-labelledby={`branding-${companyId ?? "current"}`}
    >
      <div
        className="company-logo-preview"
        aria-label={`Logo de ${companyName}`}
      >
        {branding.data?.logoDataUrl ? (
          <img src={branding.data.logoDataUrl} alt={`Logo de ${companyName}`} />
        ) : (
          <span aria-hidden="true">{companyName.charAt(0).toUpperCase()}</span>
        )}
      </div>
      <div className="company-branding-copy">
        <p className="section-kicker">Identité visuelle de l’espace</p>
        <h2 id={`branding-${companyId ?? "current"}`}>Logo de l’entreprise</h2>
        <p>
          Affiché dans l’en-tête et la barre latérale pour tous les utilisateurs
          de cette entreprise.
        </p>
        {toast && (
          <p className="toast compact-toast" role="status">
            {toast}
          </p>
        )}
        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}
        <div className="form-actions logo-actions">
          <button
            type="button"
            disabled={saving || branding.isPending}
            onClick={() => input.current?.click()}
          >
            {saving
              ? "Importation…"
              : branding.data?.logoDataUrl
                ? "Remplacer le logo"
                : "Importer un logo"}
          </button>
          {branding.data?.logoDataUrl && (
            <button
              type="button"
              className="secondary"
              disabled={saving}
              onClick={removeLogo}
            >
              Supprimer
            </button>
          )}
        </div>
        <input
          ref={input}
          className="sr-only"
          type="file"
          accept="image/png,image/jpeg"
          onChange={selectLogo}
        />
        <small>
          PNG ou JPEG, 512 Ko maximum. Un logo carré offre le meilleur rendu.
        </small>
      </div>
    </section>
  );
}

function message(caught: unknown, fallback: string) {
  return axios.isAxiosError<{ detail?: string }>(caught)
    ? (caught.response?.data?.detail ?? fallback)
    : fallback;
}
