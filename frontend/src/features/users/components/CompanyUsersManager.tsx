import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ListPagination } from "../../../components/ui/ListPagination";
import axios from "axios";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import type { UserRole } from "../../../auth/types";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { EmptyState } from "../../../components/ui/EmptyState";
import {
  StatusBadge,
  type StatusTone,
} from "../../../components/ui/StatusBadge";
import { formatDate, formatRole } from "../../../utils/format";
import { companyUsersApi } from "../api/companyUsersApi";
import type { CompanyUser } from "../types";
import {
  companyUserSchema,
  type CompanyUserFormData,
} from "../schemas/user.schema";

export function CompanyUsersManager({
  companyId,
  companyName = "l’entreprise active",
}: {
  companyId?: string;
  companyName?: string;
}) {
  const queryClient = useQueryClient();
  const queryKey = ["company-users", companyId ?? "current"];
  const query = useQuery({
    queryKey,
    queryFn: () => companyUsersApi.list(companyId),
  });
  const users = useMemo(() => query.data ?? [], [query.data]);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState("");
  const [statusUser, setStatusUser] = useState<CompanyUser | null>(null);
  const [saving, setSaving] = useState(false);
  const [changingStatus, setChangingStatus] = useState(false);

  const visibleUsers = useMemo(() => {
    const text = search.trim().toLocaleLowerCase();
    return users.filter(
      (user) =>
        (!text ||
          [user.fullName, user.email, formatRole(user.role)].some((value) =>
            value.toLocaleLowerCase().includes(text),
          )) &&
        (!status || user.enabled === (status === "active")),
    );
  }, [search, status, users]);
  const currentPage = Math.min(
    page,
    Math.max(0, Math.ceil(visibleUsers.length / size) - 1),
  );
  const pageUsers = visibleUsers.slice(
    currentPage * size,
    (currentPage + 1) * size,
  );

  const form = useForm<CompanyUserFormData>({
    resolver: zodResolver(companyUserSchema),
    defaultValues: {
      fullName: "",
      email: "",
      password: "",
      role: "INVOICE_MANAGER",
    },
  });

  const create = form.handleSubmit(async (data) => {
    setSaving(true);
    try {
      const created = await companyUsersApi.create(data, companyId);
      queryClient.setQueryData<CompanyUser[]>(queryKey, (current) => [
        created,
        ...(current ?? []),
      ]);
      setSearch("");
      setStatus("");
      setPage(0);
      form.reset();
      toast.success(`Utilisateur créé pour ${companyName}.`);
    } catch (caught) {
      toast.error(message(caught, "Impossible de créer l’utilisateur."));
    } finally {
      setSaving(false);
    }
  });

  async function confirmStatus() {
    if (!statusUser) return;
    setChangingStatus(true);
    try {
      const updated = await companyUsersApi.setStatus(statusUser, companyId);
      queryClient.setQueryData<CompanyUser[]>(queryKey, (current) =>
        (current ?? []).map((item) =>
          item.id === updated.id ? updated : item,
        ),
      );
      toast.success(
        `${updated.fullName} est maintenant ${updated.enabled ? "actif" : "inactif"}.`,
      );
      setStatusUser(null);
    } catch (caught) {
      toast.error(
        message(caught, "Impossible de modifier le statut de l’utilisateur."),
      );
    } finally {
      setChangingStatus(false);
    }
  }

  return (
    <section
      className="company-users-manager"
      data-testid="company-users-manager"
      aria-labelledby={`company-users-${companyId ?? "current"}`}
    >
      <div className="record-card-heading company-users-heading">
        <div>
          <p className="section-kicker">Gestion des accès</p>
          <h2 id={`company-users-${companyId ?? "current"}`}>
            Utilisateurs de l’entreprise
          </h2>
          <p>
            Chaque compte est rattaché définitivement à {companyName} et ne peut
            accéder qu’à ses données.
          </p>
        </div>
      </div>
      <div className="company-admin-layout">
        <form className="company-form" onSubmit={create}>
          <div>
            <p className="section-kicker">Nouvel utilisateur</p>
            <h3>Créer un accès à l’entreprise</h3>
            <p className="form-intro">
              Choisissez le rôle opérationnel requis dans cet espace.
            </p>
          </div>
          <label>
            Nom complet *
            <input
              required
              maxLength={200}
              aria-invalid={!!form.formState.errors.fullName}
              {...form.register("fullName")}
            />
            {form.formState.errors.fullName && (
              <span
                className="field-error"
                style={{
                  color: "var(--danger)",
                  fontSize: "0.8rem",
                  marginTop: "0.25rem",
                  display: "block",
                }}
              >
                {form.formState.errors.fullName.message}
              </span>
            )}
          </label>
          <label>
            E-mail *
            <input
              required
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
            Rôle *
            <select
              aria-invalid={!!form.formState.errors.role}
              {...form.register("role")}
            >
              <option value="ADMIN">Administrateur d’entreprise</option>
              <option value="INVOICE_MANAGER">
                Gestionnaire de facturation
              </option>
              <option value="VIEWER">Lecteur</option>
            </select>
            {form.formState.errors.role && (
              <span
                className="field-error"
                style={{
                  color: "var(--danger)",
                  fontSize: "0.8rem",
                  marginTop: "0.25rem",
                  display: "block",
                }}
              >
                {form.formState.errors.role.message}
              </span>
            )}
            <small>
              Les lecteurs disposent d’un accès en lecture seule. Seuls les
              administrateurs d’entreprise gèrent les utilisateurs et l’identité
              visuelle.
            </small>
          </label>
          <label>
            Mot de passe temporaire *
            <input
              required
              type="password"
              minLength={12}
              autoComplete="new-password"
              aria-invalid={!!form.formState.errors.password}
              {...form.register("password")}
            />
            {form.formState.errors.password && (
              <span
                className="field-error"
                style={{
                  color: "var(--danger)",
                  fontSize: "0.8rem",
                  marginTop: "0.25rem",
                  display: "block",
                }}
              >
                {form.formState.errors.password.message}
              </span>
            )}
            <small>
              12 caractères minimum, avec une majuscule, une minuscule, un
              chiffre et un symbole.
            </small>
          </label>
          <button disabled={saving}>
            {saving ? "Création…" : "Créer l’utilisateur"}
          </button>
        </form>
        <div className="company-directory">
          <div className="directory-toolbar">
            <div>
              <strong>
                {query.isPending || query.isError ? "—" : visibleUsers.length}{" "}
                {users.length === 1 ? "utilisateur" : "utilisateurs"}
              </strong>
              <span>
                rattaché{users.length === 1 ? "" : "s"} à {companyName}
              </span>
            </div>
            <div className="directory-filters user-directory-filters">
              <label>
                <span>Rechercher des utilisateurs</span>
                <input
                  type="search"
                  value={search}
                  placeholder="Nom, e-mail ou rôle"
                  onChange={(event) => {
                    setSearch(event.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <label>
                <span>Statut</span>
                <select
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">Tous les statuts</option>
                  <option value="active">Actifs</option>
                  <option value="inactive">Inactifs</option>
                </select>
              </label>
              {(search || status) && (
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    setSearch("");
                    setStatus("");
                    setPage(0);
                  }}
                >
                  Réinitialiser
                </button>
              )}
            </div>
          </div>
          {query.isPending ? (
            <div className="loading-state" role="status">
              Chargement des utilisateurs…
            </div>
          ) : query.isError ? (
            <div role="alert">
              <EmptyState
                icon="users"
                title="Impossible de charger les utilisateurs"
                description="Vérifiez votre connexion et réessayez."
                action={
                  <button
                    type="button"
                    className="secondary"
                    disabled={query.isFetching}
                    onClick={() => void query.refetch()}
                  >
                    Réessayer
                  </button>
                }
              />
            </div>
          ) : visibleUsers.length === 0 ? (
            <EmptyState
              icon="users"
              title="Aucun utilisateur trouvé"
              description={
                users.length
                  ? "Modifiez votre recherche."
                  : "Créez le premier utilisateur de l’entreprise à l’aide du formulaire."
              }
            />
          ) : (
            <div className="table-wrap company-table user-table">
              <table>
                <caption className="sr-only">
                  Utilisateurs de l’entreprise
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Utilisateur</th>
                    <th scope="col">Rôle</th>
                    <th scope="col">Statut</th>
                    <th scope="col">Créé le</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pageUsers.map((user) => (
                    <tr key={user.id}>
                      <td data-label="Utilisateur">
                        <div className="entity-cell">
                          <span className="entity-avatar" aria-hidden="true">
                            {user.fullName
                              .split(/\s+/)
                              .map((part) => part[0])
                              .join("")
                              .slice(0, 2)
                              .toUpperCase()}
                          </span>
                          <span>
                            <strong>{user.fullName}</strong>
                            <small>{user.email}</small>
                          </span>
                        </div>
                      </td>
                      <td data-label="Rôle">
                        <StatusBadge
                          label={formatRole(user.role)}
                          tone={roleTone(user.role)}
                        />
                      </td>
                      <td data-label="Statut">
                        <StatusBadge
                          label={user.enabled ? "Actif" : "Inactif"}
                          tone={user.enabled ? "success" : "danger"}
                        />
                      </td>
                      <td data-label="Créé le">{formatDate(user.createdAt)}</td>
                      <td data-label="Actions">
                        {user.role !== "APP_ADMIN" ? (
                          <button
                            type="button"
                            className="link-button"
                            onClick={() => setStatusUser(user)}
                          >
                            {user.enabled ? "Désactiver" : "Activer"}
                          </button>
                        ) : (
                          <span className="muted-copy">
                            Géré par la plateforme
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {!query.isPending && !query.isError && (
            <ListPagination
              page={currentPage}
              size={size}
              count={pageUsers.length}
              totalElements={visibleUsers.length}
              totalPages={Math.ceil(visibleUsers.length / size)}
              label="utilisateurs"
              onPage={setPage}
              onSize={(value) => {
                setSize(Number(value));
                setPage(0);
              }}
            />
          )}
        </div>
      </div>
      <ConfirmDialog
        open={Boolean(statusUser)}
        title={`${statusUser?.enabled ? "Désactiver" : "Activer"} cet utilisateur ?`}
        description={
          statusUser
            ? statusUser.enabled
              ? `${statusUser.fullName} ne pourra plus se connecter jusqu’à sa réactivation.`
              : `${statusUser.fullName} retrouvera l’accès selon le rôle qui lui est attribué.`
            : ""
        }
        confirmLabel={
          statusUser?.enabled
            ? "Désactiver l’utilisateur"
            : "Activer l’utilisateur"
        }
        danger={Boolean(statusUser?.enabled)}
        pending={changingStatus}
        onClose={() => setStatusUser(null)}
        onConfirm={confirmStatus}
      />
    </section>
  );
}

function roleTone(role: UserRole): StatusTone {
  return role === "ADMIN" || role === "APP_ADMIN"
    ? "info"
    : role === "INVOICE_MANAGER"
      ? "neutral"
      : "warning";
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
