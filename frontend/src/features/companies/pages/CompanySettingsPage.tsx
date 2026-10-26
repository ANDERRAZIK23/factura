import axios from "axios";
import { useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../components/ui/PageHeader";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { formatDateTime } from "../../../utils/format";
import { companyApi } from "../api/companyApi";
import { CompanyLogoEditor } from "../components/CompanyLogoEditor";
import type { Company, CompanyInput } from "../types/company";

export function CompanySettingsPage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [form, setForm] = useState<CompanyInput>({ companyName: "" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    companyApi
      .current()
      .then((data) => {
        setCompany(data);
        setForm({
          companyName: data.companyName,
          address: data.address ?? "",
          phone: data.phone ?? "",
          email: data.email ?? "",
          ice: data.ice ?? "",
        });
      })
      .catch(() => setError("Impossible de charger le profil de l’entreprise."))
      .finally(() => setLoading(false));
  }, []);
  function change(name: keyof CompanyInput, value: string) {
    setForm((current) => ({ ...current, [name]: value }));
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!company) return;
    setSaving(true);
    setError("");
    setToast("");
    try {
      const updated = await companyApi.updateCurrent({
        ...form,
        version: company.version,
      });
      setCompany(updated);
      setToast("Le profil de l’entreprise a été mis à jour avec succès.");
    } catch (caught) {
      setError(
        axios.isAxiosError<{ detail?: string }>(caught)
          ? (caught.response?.data?.detail ??
              "Impossible de mettre à jour le profil de l’entreprise.")
          : "Impossible de mettre à jour le profil de l’entreprise.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section data-testid="company-settings-page">
      <PageHeader
        eyebrow="Entreprise"
        title="Profil de l’entreprise"
        description="Gérez l’identité et les coordonnées utilisées dans tout l’espace de votre entreprise."
      />
      {toast && (
        <p className="toast" role="status">
          {toast}
        </p>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {loading && (
        <div className="loading-state" role="status">
          Chargement du profil de l’entreprise…
        </div>
      )}
      {company && (
        <div className="settings-layout">
          <aside className="record-card company-identity-card">
            <CompanyLogoEditor companyName={company.companyName} />
            <h2>{company.companyName}</h2>
            <p>{company.companyCode}</p>
            <StatusBadge
              label={company.enabled ? "Active" : "Inactive"}
              tone={company.enabled ? "success" : "danger"}
            />
            <dl className="record-meta">
              <div>
                <dt>Créée le</dt>
                <dd>{formatDateTime(company.createdAt)}</dd>
              </div>
              <div>
                <dt>Dernière mise à jour</dt>
                <dd>{formatDateTime(company.updatedAt)}</dd>
              </div>
            </dl>
          </aside>
          <form className="company-form company-profile-form" onSubmit={submit}>
            <section>
              <div className="record-card-heading">
                <p className="section-kicker">Informations générales</p>
                <h2>Identité de l’entreprise</h2>
              </div>
              <div className="company-form-grid">
                <label>
                  Nom de l’entreprise *
                  <input
                    required
                    maxLength={200}
                    value={form.companyName}
                    onChange={(event) =>
                      change("companyName", event.target.value)
                    }
                  />
                </label>
                <label>
                  ICE
                  <input
                    inputMode="numeric"
                    pattern="[0-9]{15}"
                    maxLength={15}
                    value={form.ice ?? ""}
                    onChange={(event) => change("ice", event.target.value)}
                  />
                </label>
              </div>
            </section>
            <section>
              <div className="record-card-heading">
                <p className="section-kicker">Contact</p>
                <h2>Coordonnées professionnelles</h2>
              </div>
              <div className="company-form-grid">
                <label>
                  E-mail professionnel
                  <input
                    type="email"
                    value={form.email ?? ""}
                    onChange={(event) => change("email", event.target.value)}
                  />
                </label>
                <label>
                  Téléphone
                  <input
                    value={form.phone ?? ""}
                    onChange={(event) => change("phone", event.target.value)}
                  />
                </label>
              </div>
              <label>
                Adresse
                <textarea
                  rows={4}
                  maxLength={500}
                  value={form.address ?? ""}
                  onChange={(event) => change("address", event.target.value)}
                />
              </label>
            </section>
            <div className="form-actions">
              <button disabled={saving}>
                {saving ? "Enregistrement…" : "Enregistrer le profil"}
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
