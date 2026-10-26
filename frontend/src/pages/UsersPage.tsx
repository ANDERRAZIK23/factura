import { PageHeader } from "../components/ui/PageHeader";
import { CompanyUsersManager } from "../features/users/components/CompanyUsersManager";

export function UsersPage() {
  return (
    <section data-testid="users-page">
      <PageHeader
        eyebrow="Entreprise"
        title="Utilisateurs"
        description="Gérez les rôles et les accès des utilisateurs rattachés à l’entreprise active."
      />
      <CompanyUsersManager />
    </section>
  );
}
