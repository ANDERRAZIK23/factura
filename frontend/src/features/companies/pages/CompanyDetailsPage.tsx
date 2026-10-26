import axios from "axios";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { useAuth } from "../../../auth/useAuth";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { PageHeader } from "../../../components/ui/PageHeader";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { CompanyUsersManager } from "../../users/components/CompanyUsersManager";
import { formatDateTime } from "../../../utils/format";
import { companyApi } from "../api/companyApi";
import type { Company } from "../types/company";
import { CompanyLogoEditor } from "../components/CompanyLogoEditor";
import { companySchema, type CompanyFormData } from "../schemas/company.schema";

export function CompanyDetailsPage() {
  const { id = "" } = useParams();
  const { user, impersonate } = useAuth();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const query = useQuery({
    queryKey: ["companies", "detail", id],
    queryFn: () => companyApi.get(id),
    enabled: Boolean(id),
    retry: false,
  });
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [impersonating, setImpersonating] = useState(false);
  const [impersonateConfirmOpen, setImpersonateConfirmOpen] = useState(false);

  const form = useForm<CompanyFormData>({
    resolver: zodResolver(companySchema),
    values: query.data
      ? {
          companyName: query.data.companyName,
          address: query.data.address ?? "",
          phone: query.data.phone ?? "",
          email: query.data.email ?? "",
          ice: query.data.ice ?? "",
        }
      : undefined,
    resetOptions: { keepDirtyValues: true },
  });

  const updateMutation = useMutation({
    mutationFn: (data: CompanyFormData) =>
      companyApi.update(id, { ...data, version: query.data!.version }),
    onMutate: async (newData) => {
      await cache.cancelQueries({ queryKey: ["companies", "detail", id] });
      const previousCompany = cache.getQueryData<Company>([
        "companies",
        "detail",
        id,
      ]);
      if (previousCompany) {
        cache.setQueryData(["companies", "detail", id], {
          ...previousCompany,
          ...newData,
        });
      }
      return { previousCompany };
    },
    onError: (err, _newData, context) => {
      if (context?.previousCompany) {
        cache.setQueryData(
          ["companies", "detail", id],
          context.previousCompany,
        );
      }
      toast.error(
        message(err, "Impossible de mettre à jour le profil de l’entreprise."),
      );
    },
    onSettled: () => {
      cache.invalidateQueries({ queryKey: ["companies", "detail", id] });
      cache.invalidateQueries({ queryKey: ["companies", "list"] });
    },
    onSuccess: () => {
      toast.success("Le profil de l’entreprise a été mis à jour avec succès.");
    },
  });

  if (query.isPending)
    return (
      <div className="loading-state" role="status">
        Chargement de l’entreprise…
      </div>
    );
  if (query.isError || !query.data)
    return (
      <section className="empty-state">
        <h1>Impossible de charger l’entreprise</h1>
        <p>Cette entreprise n’existe pas ou n’est pas disponible.</p>
        <button onClick={() => query.refetch()}>Réessayer</button>
      </section>
    );
  const company = query.data;
  const isCurrentCompany = company.id === user?.companyId;

  const saveProfile = form.handleSubmit((data) => {
    updateMutation.mutate(data);
  });
  async function changeStatus() {
    setPending(true);
    try {
      const updated = await companyApi.setStatus(company);
      cache.setQueryData(["companies", "detail", id], updated);
      setConfirmOpen(false);
      toast.success(
        `${updated.companyName} est maintenant ${updated.enabled ? "active" : "inactive"}.`,
      );
    } catch (caught) {
      toast.error(
        message(caught, "Impossible de modifier le statut de l’entreprise."),
      );
    } finally {
      setPending(false);
    }
  }

  async function startImpersonation() {
    setImpersonating(true);
    try {
      await impersonate(company.id);
      navigate("/dashboard");
    } catch (caught) {
      toast.error(
        message(caught, "Impossible de démarrer la session de support."),
      );
      setImpersonating(false);
    }
  }

  return (
    <section className="platform-page" data-testid="company-details-page">
      <PageHeader
        breadcrumbs={[
          { label: "Entreprises", to: "/companies" },
          { label: company.companyName },
        ]}
        eyebrow={company.companyCode}
        title={company.companyName}
        description="Gérez cet espace entreprise indépendant, son identité, son image de marque et ses utilisateurs."
        action={
          <>
            <StatusBadge
              label={company.enabled ? "Active" : "Inactive"}
              tone={company.enabled ? "success" : "danger"}
            />
            <Link
              className="button-link secondary-link"
              to={`/contract-designer?companyId=${company.id}`}
            >
              Design du contrat
            </Link>
            {!isCurrentCompany && company.enabled && (
              <button
                type="button"
                className="secondary"
                onClick={() => setImpersonateConfirmOpen(true)}
              >
                Se connecter
              </button>
            )}
            <button
              className={company.enabled ? "danger" : undefined}
              disabled={isCurrentCompany}
              title={
                isCurrentCompany
                  ? "Votre compte administrateur plateforme utilise cette entreprise."
                  : undefined
              }
              onClick={() => setConfirmOpen(true)}
            >
              {isCurrentCompany
                ? "Entreprise actuelle"
                : company.enabled
                  ? "Désactiver"
                  : "Activer"}
            </button>
          </>
        }
      />
      <div className="platform-context-banner">
        <span aria-hidden="true">
          {company.companyName.charAt(0).toUpperCase()}
        </span>
        <div>
          <strong>Espace entreprise isolé</strong>
          <p>
            Chaque utilisateur créé ci-dessous est rattaché à{" "}
            {company.companyName}. Ses clients, produits et factures restent
            séparés de ceux des autres entreprises.
          </p>
        </div>
      </div>

      <div className="platform-company-management-grid">
        <div className="record-card">
          <CompanyLogoEditor
            companyId={company.id}
            companyName={company.companyName}
          />
          <dl className="record-meta company-platform-meta">
            <div>
              <dt>Code entreprise</dt>
              <dd>{company.companyCode}</dd>
            </div>
            <div>
              <dt>Créée le</dt>
              <dd>{formatDateTime(company.createdAt)}</dd>
            </div>
            <div>
              <dt>Dernière mise à jour</dt>
              <dd>{formatDateTime(company.updatedAt)}</dd>
            </div>
          </dl>
        </div>
        <form
          className="company-form platform-company-profile-form"
          onSubmit={saveProfile}
        >
          <div className="record-card-heading">
            <p className="section-kicker">Identité de l’entreprise</p>
            <h2>Profil et coordonnées de facturation</h2>
            <p>
              Ces informations identifient l’espace et apparaissent dans son
              interface d’administration.
            </p>
          </div>
          <div className="company-form-grid">
            <label>
              Nom de l’entreprise *
              <input
                required
                maxLength={200}
                aria-invalid={!!form.formState.errors.companyName}
                {...form.register("companyName")}
              />
              {form.formState.errors.companyName && (
                <span
                  className="field-error"
                  style={{
                    color: "var(--danger)",
                    fontSize: "0.8rem",
                    marginTop: "0.25rem",
                    display: "block",
                  }}
                >
                  {form.formState.errors.companyName.message}
                </span>
              )}
            </label>
            <label>
              ICE
              <input
                inputMode="numeric"
                maxLength={15}
                aria-invalid={!!form.formState.errors.ice}
                {...form.register("ice")}
              />
              {form.formState.errors.ice && (
                <span
                  className="field-error"
                  style={{
                    color: "var(--danger)",
                    fontSize: "0.8rem",
                    marginTop: "0.25rem",
                    display: "block",
                  }}
                >
                  {form.formState.errors.ice.message}
                </span>
              )}
            </label>
          </div>
          <div className="company-form-grid">
            <label>
              E-mail professionnel
              <input
                type="email"
                aria-invalid={!!form.formState.errors.email}
                {...form.register("email")}
              />
              {form.formState.errors.email && (
                <span
                  className="field-error"
                  style={{
                    color: "var(--danger)",
                    fontSize: "0.8rem",
                    marginTop: "0.25rem",
                    display: "block",
                  }}
                >
                  {form.formState.errors.email.message}
                </span>
              )}
            </label>
            <label>
              Téléphone
              <input
                maxLength={40}
                aria-invalid={!!form.formState.errors.phone}
                {...form.register("phone")}
              />
              {form.formState.errors.phone && (
                <span
                  className="field-error"
                  style={{
                    color: "var(--danger)",
                    fontSize: "0.8rem",
                    marginTop: "0.25rem",
                    display: "block",
                  }}
                >
                  {form.formState.errors.phone.message}
                </span>
              )}
            </label>
          </div>
          <label>
            Adresse
            <textarea
              rows={4}
              maxLength={500}
              aria-invalid={!!form.formState.errors.address}
              {...form.register("address")}
            />
            {form.formState.errors.address && (
              <span
                className="field-error"
                style={{
                  color: "var(--danger)",
                  fontSize: "0.8rem",
                  marginTop: "0.25rem",
                  display: "block",
                }}
              >
                {form.formState.errors.address.message}
              </span>
            )}
          </label>
          <div className="form-actions">
            <button disabled={updateMutation.isPending}>
              {updateMutation.isPending
                ? "Enregistrement…"
                : "Enregistrer le profil"}
            </button>
          </div>
        </form>
      </div>

      <CompanyUsersManager
        companyId={company.id}
        companyName={company.companyName}
      />
      <ConfirmDialog
        open={confirmOpen}
        title={`${company.enabled ? "Désactiver" : "Activer"} cette entreprise ?`}
        description={
          company.enabled
            ? `Les utilisateurs de ${company.companyName} perdront leur accès jusqu’à la réactivation de l’entreprise.`
            : `Les utilisateurs de ${company.companyName} pourront de nouveau accéder à leur espace.`
        }
        confirmLabel={
          company.enabled ? "Désactiver l’entreprise" : "Activer l’entreprise"
        }
        danger={company.enabled}
        pending={pending}
        onClose={() => setConfirmOpen(false)}
        onConfirm={changeStatus}
      />
      <ConfirmDialog
        open={impersonateConfirmOpen}
        title="Se connecter en tant que cette entreprise ?"
        description={`Vous êtes sur le point de vous connecter avec le compte administrateur de ${company.companyName}. Vous aurez accès à toutes ses données. Vous pourrez revenir à votre compte à tout moment.`}
        confirmLabel="Se connecter"
        pending={impersonating}
        onClose={() => setImpersonateConfirmOpen(false)}
        onConfirm={startImpersonation}
      />
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
