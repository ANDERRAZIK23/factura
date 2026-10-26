export type StatusTone = "success" | "warning" | "danger" | "info" | "neutral";

export function StatusBadge({
  label,
  tone = "neutral",
}: {
  label: string;
  tone?: StatusTone;
}) {
  return <span className={`status-badge status-${tone}`}>{label}</span>;
}
