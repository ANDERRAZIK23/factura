import { StatusBadge } from "../../../components/ui/StatusBadge";

export function ProductStatusBadge({ active }: { active: boolean }) {
  return (
    <StatusBadge
      label={active ? "Actif" : "Inactif"}
      tone={active ? "success" : "danger"}
    />
  );
}
