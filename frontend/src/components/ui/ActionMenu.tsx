import { Icon } from "./Icon";

export function ActionMenu({
  label = "Actions",
  children,
}: {
  label?: string;
  children: React.ReactNode;
}) {
  return (
    <details className="action-menu">
      <summary aria-label={label} title={label}>
        <Icon name="dots" size={17} />
      </summary>
      <div className="action-menu-panel">{children}</div>
    </details>
  );
}
