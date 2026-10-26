import type {
  InvoiceBlock,
  InvoiceBlockKind,
  InvoiceDesignSettings,
  InvoiceFreeLayout,
} from "./invoiceDesignApi";

export const blockLabels: Record<InvoiceBlockKind, string> = {
  LOGO: "Logo",
  COMPANY: "Entreprise",
  TITLE: "Titre et numéro",
  CLIENT: "Client",
  METADATA: "Informations",
  ITEMS: "Articles",
  TOTALS: "Totaux",
  AMOUNT_WORDS: "Montant en lettres",
  FOOTER: "Pied de page",
  TEXT: "Texte libre",
  RECTANGLE: "Rectangle",
};

export function createLayout(
  settings: InvoiceDesignSettings,
  framed = false,
): InvoiceFreeLayout {
  const block = (
    kind: InvoiceBlockKind,
    x: number,
    y: number,
    width: number,
    height: number,
    size = settings.fontSize,
  ): InvoiceBlock => ({
    id: kind.toLowerCase(),
    kind,
    x,
    y,
    width,
    height,
    backgroundColor: null,
    borderColor: settings.accentColor,
    borderWidth:
      framed && ["COMPANY", "CLIENT", "METADATA", "TOTALS"].includes(kind)
        ? 1
        : 0,
    textColor: "#263247",
    fontSize: size,
    textAlignment: "LEFT",
    text: "",
  });
  const identityX = settings.logoPosition === "LEFT" ? 60 : 520;
  const titleX = settings.logoPosition === "LEFT" ? 580 : 60;
  return {
    blocks: [
      block("LOGO", identityX, 45, 200, 70),
      block("COMPANY", identityX, 120, 420, 130),
      {
        ...block("TITLE", titleX, 45, 360, 110, 22),
        backgroundColor:
          settings.template === "MODERN" ? settings.accentColor : null,
        textColor:
          settings.template === "MODERN" ? "#ffffff" : settings.accentColor,
        textAlignment: "RIGHT",
      },
      {
        ...block(
          "CLIENT",
          settings.clientPosition === "LEFT" ? 60 : 560,
          255,
          380,
          125,
        ),
        backgroundColor: "#f3f5f9",
      },
      block(
        "METADATA",
        settings.clientPosition === "LEFT" ? 480 : 60,
        285,
        460,
        90,
      ),
      block("ITEMS", 60, 390, 880, 260),
      block("TOTALS", 530, 675, 410, 170),
      block("AMOUNT_WORDS", 60, 845, 880, 65),
      block("FOOTER", 60, 910, 880, 70, 8),
    ],
  };
}

export function fitBlock(
  block: InvoiceBlock,
  patch: Partial<InvoiceBlock>,
): InvoiceBlock {
  const next = { ...block, ...patch };
  next.width = Math.max(20, Math.min(1000, Math.round(next.width)));
  next.height = Math.max(20, Math.min(1000, Math.round(next.height)));
  next.x = Math.max(0, Math.min(1000 - next.width, Math.round(next.x)));
  next.y = Math.max(0, Math.min(1000 - next.height, Math.round(next.y)));
  return next;
}

export function blockVisible(
  block: InvoiceBlock,
  settings: InvoiceDesignSettings,
  page = 1,
  pages = 1,
) {
  return (
    (block.kind !== "LOGO" || settings.showLogo) &&
    (block.kind !== "AMOUNT_WORDS" || settings.showAmountInWords) &&
    (!["TOTALS", "AMOUNT_WORDS"].includes(block.kind) || page === pages)
  );
}

export type BlockAlignment =
  "left" | "center" | "right" | "top" | "middle" | "bottom";

export function alignBlock(block: InvoiceBlock, alignment: BlockAlignment) {
  const positions = {
    left: { x: 60 },
    center: { x: (1000 - block.width) / 2 },
    right: { x: 940 - block.width },
    top: { y: 60 },
    middle: { y: (1000 - block.height) / 2 },
    bottom: { y: 940 - block.height },
  };
  return fitBlock(block, positions[alignment]);
}

export function blockDisplayName(block: InvoiceBlock, blocks: InvoiceBlock[]) {
  if (!["TEXT", "RECTANGLE"].includes(block.kind))
    return blockLabels[block.kind];
  const index = blocks
    .filter((item) => item.kind === block.kind)
    .findIndex((item) => item.id === block.id);
  return `${blockLabels[block.kind]} ${index + 1}`;
}

export function paperSize(format: InvoiceDesignSettings["paperFormat"]) {
  return format === "A4"
    ? { width: 210, height: 297 }
    : { width: 215.9, height: 279.4 };
}
