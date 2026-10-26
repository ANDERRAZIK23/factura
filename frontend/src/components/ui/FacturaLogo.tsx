import facturaLogo from "../../assets/factura-logo.png";

export function FacturaLogo({ iconOnly = false }: { iconOnly?: boolean }) {
  return (
    <svg
      className={`factura-logo${iconOnly ? " factura-logo-icon" : ""}`}
      role="img"
      aria-label="Factura"
      viewBox={iconOnly ? "250 170 360 390" : "250 170 1680 390"}
      width={iconOnly ? 360 : 1680}
      height={390}
    >
      {/* The viewport trims the source image's transparent margins. */}
      <image href={facturaLogo} width={2172} height={724} />
    </svg>
  );
}
