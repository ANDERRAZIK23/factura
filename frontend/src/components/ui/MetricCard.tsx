import { Icon, type IconName } from "./Icon";

export function MetricCard({
  label,
  value,
  detail,
  tone = "neutral",
  icon,
  trend,
}: {
  label: string;
  value: React.ReactNode;
  detail?: string;
  tone?: "neutral" | "accent" | "warning" | "success";
  icon?: IconName;
  trend?: { value: number; label?: string };
}) {
  return (
    <article className={`metric-card metric-${tone}`} role="group" aria-label={label}>
      <div className="metric-card-heading">
        <div className="metric-card-label">{label}</div>
        {icon && (
          <span className="metric-card-icon">
            <Icon name={icon} size={17} />
          </span>
        )}
      </div>
      <div className="metric-card-value">
        <strong>{value}</strong>
        {trend && (
          <span className={`metric-trend ${trend.value >= 0 ? "metric-trend-up" : "metric-trend-down"}`}>
            <Icon name={trend.value >= 0 ? "trend-up" : "trend-down"} size={14} />
            {Math.abs(trend.value)}%
          </span>
        )}
      </div>
      {detail && <p>{detail}</p>}
    </article>
  );
}
