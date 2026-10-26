import type { InvoiceStatus } from "../types/invoice";
import { StatusBadge } from "../../../components/ui/StatusBadge";

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  const config = {
    DRAFT: ["Brouillon", "neutral"],
    VALIDATED: ["En attente", "warning"],
    PAID: ["Payée", "success"],
    CANCELLED: ["Annulée", "danger"],
  } as const;
  return <StatusBadge label={config[status][0]} tone={config[status][1]} />;
}
