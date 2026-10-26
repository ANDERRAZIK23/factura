import axios from "axios";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ColumnDef, RowSelectionState } from "@tanstack/react-table";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { EmptyState } from "../../../components/ui/EmptyState";
import { PageHeader } from "../../../components/ui/PageHeader";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { useAuth } from "../../../auth/useAuth";
import { formatDate } from "../../../utils/format";
import { companyApi } from "../api/companyApi";
import type { Company, CompanyPage } from "../types/company";
import { Icon } from "../../../components/ui/Icon";
import { DataTable } from "../../../components/ui/DataTable";

export function CompaniesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<"all" | "active" | "inactive">("all");
  const [statusCompany, setStatusCompany] = useState<Company | null>(null);
  const [changingStatus, setChangingStatus] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});

  const {
    data: page,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["companies", "list"],
    queryFn: () => companyApi.list(),
  });
  const companies = useMemo(() => page?.content ?? [], [page]);

  const visibleCompanies = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return companies.filter((company) => {
      const matchesSearch =
        !query ||
        [
          company.companyName,
          company.companyCode,
          company.ice,
          company.email,
        ].some((value) => value?.toLocaleLowerCase().includes(query));
      const matchesStatus =
        status === "all" ||
        (status === "active" ? company.enabled : !company.enabled);
      return matchesSearch && matchesStatus;
    });
  }, [companies, search, status]);

  async function confirmStatus() {
    if (!statusCompany) return;
    setChangingStatus(true);
    setError("");
    setToast("");
    try {
      const updated = await companyApi.setStatus(statusCompany);
      queryClient.setQueryData<CompanyPage>(["companies", "list"], (old) => {
        if (!old) return old;
        return {
          ...old,
          content: old.content.map((item: Company) =>
            item.id === updated.id ? updated : item,
          ),
        };
      });
      setToast(
        `${updated.companyName} est maintenant ${updated.enabled ? "active" : "inactive"}.`,
      );
      setStatusCompany(null);
    } catch (caught) {
      setError(
        message(caught, "Impossible de modifier le statut de l’entreprise."),
      );
    } finally {
      setChangingStatus(false);
    }
  }

  function handleExportCsv() {
    const selectedIds = Object.keys(rowSelection).filter(
      (id) => rowSelection[id],
    );
    if (selectedIds.length === 0) return;

    const selectedCompanies = visibleCompanies.filter((company) =>
      selectedIds.includes(company.id),
    );

    const csvHeader = "Nom,Code,ICE,Email,Téléphone,Créée le,Statut\n";
    const csvContent = selectedCompanies
      .map((c) =>
        [
          c.companyName,
          c.companyCode,
          c.ice ?? "",
          c.email ?? "",
          c.phone ?? "",
          formatDate(c.createdAt),
          c.enabled ? "Active" : "Inactive",
        ]
          .map(csvCell)
          .join(","),
      )
      .join("\n");

    const blob = new Blob([csvHeader + csvContent], {
      type: "text/csv;charset=utf-8;",
    });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", "entreprises.csv");
    link.style.visibility = "hidden";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  const columns = useMemo<ColumnDef<Company>[]>(
    () => [
      {
        id: "select",
        header: ({ table }) => (
          <input
            type="checkbox"
            checked={table.getIsAllPageRowsSelected()}
            onChange={table.getToggleAllPageRowsSelectedHandler()}
            aria-label="Sélectionner tout"
          />
        ),
        cell: ({ row }) => (
          <input
            type="checkbox"
            checked={row.getIsSelected()}
            disabled={!row.getCanSelect()}
            onChange={row.getToggleSelectedHandler()}
            aria-label={`Sélectionner l'entreprise ${row.original.companyName}`}
          />
        ),
        enableSorting: false,
        enableHiding: false,
        size: 40,
      },
      {
        accessorKey: "companyName",
        header: "Entreprise",
        cell: ({ row }) => {
          const company = row.original;
          return (
            <div className="entity-cell">
              <span className="entity-avatar" aria-hidden="true">
                {company.companyName.charAt(0).toUpperCase()}
              </span>
              <span>
                <Link to={`/companies/${company.id}`}>
                  <strong>{company.companyName}</strong>
                </Link>
                <small>
                  {company.companyCode}
                  {company.ice ? ` · ICE ${company.ice}` : ""}
                </small>
              </span>
            </div>
          );
        },
        sortingFn: "text",
        enableHiding: false,
      },
      {
        id: "contact",
        accessorFn: (row) => row.email ?? row.phone ?? "", // for sorting
        header: "Contact",
        cell: ({ row }) => {
          const company = row.original;
          return (
            <>
              {company.email ?? "—"}
              <small style={{ display: "block" }}>
                {company.phone ?? "Aucun téléphone"}
              </small>
            </>
          );
        },
      },
      {
        accessorKey: "createdAt",
        header: "Créée le",
        cell: ({ getValue }) => formatDate(getValue() as string),
      },
      {
        accessorKey: "status",
        header: "Statut",
        accessorFn: (row) => row.enabled,
        cell: ({ row }) => (
          <StatusBadge
            label={row.original.enabled ? "Active" : "Inactive"}
            tone={row.original.enabled ? "success" : "danger"}
          />
        ),
      },
      {
        id: "actions",
        header: "Actions",
        cell: ({ row }) => {
          const company = row.original;
          const isCurrentCompany = company.id === user?.companyId;
          return (
            <div className="row-actions">
              <Link to={`/companies/${company.id}`}>Voir</Link>
              <button
                type="button"
                className="link-button"
                disabled={isCurrentCompany}
                title={
                  isCurrentCompany
                    ? "Votre compte administrateur plateforme utilise cette entreprise."
                    : undefined
                }
                onClick={() => setStatusCompany(company)}
              >
                {isCurrentCompany
                  ? "Entreprise actuelle"
                  : company.enabled
                    ? "Désactiver"
                    : "Activer"}
              </button>
            </div>
          );
        },
        enableSorting: false,
      },
    ],
    [user],
  );

  const selectedCount = visibleCompanies.filter(
    (company) => rowSelection[company.id],
  ).length;

  return (
    <section data-testid="companies-page" className="platform-page">
      <PageHeader
        eyebrow="Administration de la plateforme"
        title="Entreprises"
        description="Gérez les espaces indépendants inscrits sur la plateforme."
        action={
          <Link className="button-link" to="/companies/new">
            <Icon name="plus" size={16} /> Nouvelle entreprise
          </Link>
        }
      />

      <div className="platform-context-banner">
        <span aria-hidden="true">P</span>
        <div>
          <strong>Périmètre de la plateforme</strong>
          <p>
            Les modifications effectuées ici influent sur l’accès des
            entreprises. Les données des clients, produits, factures et
            utilisateurs restent isolées dans chaque entreprise.
          </p>
        </div>
      </div>

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

      <div className="company-directory">
        <div className="directory-toolbar">
          <div>
            <strong>
              {isLoading || isError ? "—" : visibleCompanies.length}{" "}
              {companies.length === 1 ? "entreprise" : "entreprises"}
            </strong>
            <span>
              {companies.filter((company) => company.enabled).length}{" "}
              {companies.filter((company) => company.enabled).length === 1
                ? "active"
                : "actives"}
            </span>
          </div>
          <div className="directory-filters">
            <label>
              <span>Rechercher des entreprises</span>
              <input
                type="search"
                value={search}
                placeholder="Nom, code, ICE ou e-mail"
                onChange={(event) => {
                  setSearch(event.target.value);
                  setRowSelection({});
                }}
              />
            </label>
            <label>
              <span>Statut</span>
              <select
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as typeof status);
                  setRowSelection({});
                }}
              >
                <option value="all">Toutes les entreprises</option>
                <option value="active">Actives</option>
                <option value="inactive">Inactives</option>
              </select>
            </label>
            {(search || status !== "all") && (
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  setSearch("");
                  setStatus("all");
                  setRowSelection({});
                }}
              >
                Réinitialiser
              </button>
            )}
          </div>
        </div>

        {isError && (
          <div className="form-error" role="alert">
            Impossible de charger les entreprises.{" "}
            <button className="link-button" onClick={() => refetch()}>
              Réessayer
            </button>
          </div>
        )}

        {!isError && (
          <DataTable
            columns={columns}
            data={visibleCompanies}
            getRowId={(company) => company.id}
            caption="Répertoire des entreprises"
            itemLabel="entreprises"
            isLoading={isLoading}
            rowSelection={rowSelection}
            onRowSelectionChange={setRowSelection}
            toolbarActions={
              selectedCount > 0 ? (
                <button className="secondary" onClick={handleExportCsv}>
                  <Icon name="download" size={16} /> Exporter {selectedCount}{" "}
                  CSV
                </button>
              ) : null
            }
            emptyState={
              <EmptyState
                icon="companies"
                title="Aucune entreprise trouvée"
                description={
                  companies.length
                    ? "Modifiez votre recherche ou le filtre de statut."
                    : "Créez le premier espace entreprise à l’aide du formulaire."
                }
              />
            }
          />
        )}
      </div>

      <ConfirmDialog
        open={Boolean(statusCompany)}
        title={`${statusCompany?.enabled ? "Désactiver" : "Activer"} cette entreprise ?`}
        description={
          statusCompany?.enabled
            ? `Les utilisateurs de ${statusCompany.companyName} ne pourront plus accéder à leur espace tant que l’entreprise ne sera pas réactivée.`
            : `Les utilisateurs de ${statusCompany?.companyName} pourront de nouveau accéder à leur espace entreprise.`
        }
        confirmLabel={
          statusCompany?.enabled
            ? "Désactiver l’entreprise"
            : "Activer l’entreprise"
        }
        danger={Boolean(statusCompany?.enabled)}
        pending={changingStatus}
        onClose={() => setStatusCompany(null)}
        onConfirm={confirmStatus}
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

function csvCell(value: string) {
  const safe = /^[=+\-@\t\r\n]/.test(value) ? `'${value}` : value;
  return `"${safe.replace(/"/g, '""')}"`;
}
