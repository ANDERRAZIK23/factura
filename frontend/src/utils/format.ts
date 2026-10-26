const mad = new Intl.NumberFormat("fr-MA", {
  style: "currency",
  currency: "MAD",
  minimumFractionDigits: 2,
});
const shortDate = new Intl.DateTimeFormat("fr-MA", {
  day: "2-digit",
  month: "short",
  year: "numeric",
});
const dateTime = new Intl.DateTimeFormat("fr-MA", {
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatMoney(value: number) {
  return mad.format(Number.isFinite(value) ? value : 0);
}

export function formatDate(value: string | Date | null | undefined) {
  if (!value) return "—";
  const parsed = value instanceof Date ? value : new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : shortDate.format(parsed);
}

export function formatDateTime(value: string | Date | null | undefined) {
  if (!value) return "—";
  const parsed = value instanceof Date ? value : new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : dateTime.format(parsed);
}

export function formatRole(role: string | undefined) {
  return (
    (
      {
        APP_ADMIN: "Administrateur de la plateforme",
        ADMIN: "Administrateur d’entreprise",
        INVOICE_MANAGER: "Gestionnaire de facturation",
        VIEWER: "Lecteur",
      } as Record<string, string>
    )[role ?? ""] ?? "Utilisateur"
  );
}

export function formatPaymentMethod(method: string | undefined) {
  return (
    (
      {
        CASH: "Espèces",
        CHECK: "Chèque",
        BANK_TRANSFER: "Virement bancaire",
        CARD: "Carte",
        CREDIT: "Crédit",
        OTHER: "Autre",
      } as Record<string, string>
    )[method ?? ""] ?? "—"
  );
}

export function formatProductUnit(unit: string | undefined) {
  return (
    (
      {
        PIECE: "Pièce",
        KG: "kg",
        METER: "Mètre",
        ROLL: "Rouleau",
        LITER: "Litre",
        SERVICE: "Service",
        OTHER: "Autre",
      } as Record<string, string>
    )[unit ?? ""] ?? "—"
  );
}
