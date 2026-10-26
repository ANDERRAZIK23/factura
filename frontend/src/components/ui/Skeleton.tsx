export function Skeleton({
  width,
  height = '1rem',
  radius = '4px',
  className = '',
}: {
  width?: string;
  height?: string;
  radius?: string;
  className?: string;
}) {
  return (
    <span
      className={`skeleton ${className}`}
      style={{ width, height, borderRadius: radius }}
      aria-hidden="true"
    />
  );
}

function SkeletonRow({ cols = 5 }: { cols?: number }) {
  return (
    <div className="skeleton-row" aria-hidden="true">
      {Array.from({ length: cols }, (_, i) => (
        <span key={i} className="skeleton" />
      ))}
    </div>
  );
}

function SkeletonCard() {
  return (
    <div className="skeleton-card" aria-hidden="true">
      <span className="skeleton" style={{ width: '40%', height: '0.75rem' }} />
      <span className="skeleton" style={{ width: '60%', height: '1.5rem' }} />
      <span className="skeleton" style={{ width: '80%', height: '0.75rem' }} />
    </div>
  );
}

function SkeletonTable({ rows = 5, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div className="skeleton-table" role="status" aria-label="Chargement des données…">
      {Array.from({ length: rows }, (_, i) => (
        <SkeletonRow key={i} cols={cols} />
      ))}
    </div>
  );
}

Skeleton.Row = SkeletonRow;
Skeleton.Card = SkeletonCard;
Skeleton.Table = SkeletonTable;
