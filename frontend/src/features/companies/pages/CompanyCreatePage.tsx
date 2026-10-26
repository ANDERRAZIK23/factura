import axios from "axios";
import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { PageHeader } from "../../../components/ui/PageHeader";
import { companyApi } from "../api/companyApi";
import type { CompanyCreateInput } from "../types/company";

const empty: CompanyCreateInput = {
  companyName: "",
  address: "",
  phone: "",
  email: "",
  ice: "",
  adminFullName: "",
  adminEmail: "",
  adminPassword: "",
};

export function CompanyCreatePage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(empty);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  function change(name: keyof CompanyCreateInput, value: string) {
    setForm((current) => ({ ...current, [name]: value }));
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      await companyApi.create(form);
      navigate("/companies", { replace: true });
    } catch (caught) {
      setError(message(caught, "Impossible de créer l’entreprise."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section
      className="platform-page company-create-page"
      data-testid="company-create-page"
    >
      <PageHeader
        eyebrow="Administration de la plateforme"
        title="Nouvelle entreprise"
        description="Créez un espace de données isolé et son premier compte administrateur."
        breadcrumbs={[
          { label: "Entreprises", to: "/companies" },
          { label: "Nouvelle entreprise" },
        ]}
        action={
          <Link className="button-link secondary-link" to="/companies">
            Retour à la liste
          </Link>
        }
      />
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <form className="company-form company-create-form" onSubmit={submit}>
        <section className="form-section">
          <header className="form-section-heading">
            <span>1</span>
            <div>
              <h2>Identité de l’entreprise</h2>
              <p>
                Ces informations seront utilisées dans l’espace de gestion et
                les documents commerciaux.
              </p>
            </div>
          </header>
          <div className="company-form-fields">
            <label className="form-field-wide">
              Nom de l’entreprise *
              <input
                required
                maxLength={200}
                autoFocus
                value={form.companyName}
                onChange={(event) => change("companyName", event.target.value)}
              />
            </label>
            <label>
              ICE
              <input
                inputMode="numeric"
                pattern="[0-9]{15}"
                maxLength={15}
                value={form.ice}
                onChange={(event) => change("ice", event.target.value)}
              />
            </label>
            <label>
              E-mail professionnel
              <input
                type="email"
                value={form.email}
                onChange={(event) => change("email", event.target.value)}
              />
            </label>
            <label>
              Téléphone
              <input
                value={form.phone}
                onChange={(event) => change("phone", event.target.value)}
              />
            </label>
            <label>
              Adresse
              <input
                value={form.address}
                onChange={(event) => change("address", event.target.value)}
              />
            </label>
          </div>
        </section>
        <section className="form-section">
          <header className="form-section-heading">
            <span>2</span>
            <div>
              <h2>Premier administrateur</h2>
              <p>
                Ce compte pourra configurer l’entreprise et créer ses
                utilisateurs.
              </p>
            </div>
          </header>
          <div className="company-form-fields">
            <label>
              Nom complet *
              <input
                required
                maxLength={200}
                value={form.adminFullName}
                onChange={(event) =>
                  change("adminFullName", event.target.value)
                }
              />
            </label>
            <label>
              E-mail *
              <input
                required
                type="email"
                value={form.adminEmail}
                onChange={(event) => change("adminEmail", event.target.value)}
              />
            </label>
            <label className="form-field-wide">
              Mot de passe temporaire *
              <input
                required
                type="password"
                minLength={12}
                autoComplete="new-password"
                value={form.adminPassword}
                onChange={(event) =>
                  change("adminPassword", event.target.value)
                }
              />
              <small>
                12 caractères minimum, avec une majuscule, une minuscule, un
                chiffre et un symbole.
              </small>
            </label>
          </div>
        </section>
        <footer className="form-actions command-bar">
          <Link className="button-link secondary-link" to="/companies">
            Annuler
          </Link>
          <button type="submit" disabled={saving}>
            {saving ? "Création…" : "Créer l’entreprise"}
          </button>
        </footer>
      </form>
    </section>
  );
}

function message(caught: unknown, fallback: string) {
  if (
    !axios.isAxiosError<{
      detail?: string;
      fieldErrors?: Record<string, string>;
    }>(caught)
  )
    return fallback;
  const problem = caught.response?.data;
  return problem?.fieldErrors
    ? (Object.values(problem.fieldErrors)[0] ?? fallback)
    : (problem?.detail ?? fallback);
}
