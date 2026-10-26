import { StatusBadge } from "../../../components/ui/StatusBadge";

export function ClientStatusBadge({ active }: { active: boolean }) {
  return (
    <StatusBadge
      label={active ? "Actif" : "Inactif"}
      tone={active ? "success" : "danger"}
    />
  );
}
