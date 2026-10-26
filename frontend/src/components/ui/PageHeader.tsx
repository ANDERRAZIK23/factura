import { Link } from "react-router-dom";
import { Icon } from "./Icon";

export function PageHeader({
  eyebrow,
  title,
  badge,
  description,
  action,
  breadcrumbs,
}: {
  eyebrow?: string;
  title: string;
  badge?: React.ReactNode;
  description?: string;
  action?: React.ReactNode;
  breadcrumbs?: Array<{ label: string; to?: string }>;
}) {
  return (
    <>
      {breadcrumbs && (
        <nav className="breadcrumbs" aria-label="Fil d’Ariane">
          {breadcrumbs.map((item, index) => (
            <span key={`${item.label}-${index}`}>
              {index > 0 && <Icon name="chevronLeft" size={14} />}
              {item.to ? (
                <Link to={item.to}>{item.label}</Link>
              ) : (
                <span aria-current="page">{item.label}</span>
              )}
            </span>
          ))}
        </nav>
      )}
      <header className="page-heading page-header">
        <div className="page-header-copy">
          {eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h1 className="page-title">
            {title}
            {badge && <span>{badge}</span>}
          </h1>
          {description && <p className="muted">{description}</p>}
        </div>
        {action && <div className="heading-actions">{action}</div>}
      </header>
    </>
  );
}
