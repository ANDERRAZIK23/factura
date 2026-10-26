import { describe, expect, it } from "vitest";
import { alignBlock, blockVisible } from "./invoiceLayout";
import type { InvoiceBlock, InvoiceDesignSettings } from "./invoiceDesignApi";

const block: InvoiceBlock = {
  id: "totals",
  kind: "TOTALS",
  x: 50,
  y: 50,
  width: 950,
  height: 500,
  backgroundColor: null,
  borderColor: "#3346a8",
  borderWidth: 0,
  textColor: "#263247",
  fontSize: 10,
  textAlignment: "LEFT",
  text: "",
};

describe("invoice layout boundaries", () => {
  it("keeps large blocks inside the page when aligning to margins", () => {
    expect(alignBlock(block, "left")).toMatchObject({ x: 50, width: 950 });
    expect(alignBlock(block, "right")).toMatchObject({ x: 0, width: 950 });
    expect(alignBlock(block, "bottom")).toMatchObject({ y: 440, height: 500 });
  });

  it("shows final-page blocks only on the final PDF page", () => {
    const settings = {
      showLogo: false,
      showAmountInWords: true,
    } as InvoiceDesignSettings;
    expect(blockVisible(block, settings, 1, 3)).toBe(false);
    expect(blockVisible(block, settings, 3, 3)).toBe(true);
    expect(blockVisible({ ...block, kind: "LOGO" }, settings, 3, 3)).toBe(
      false,
    );
    expect(blockVisible({ ...block, kind: "ITEMS" }, settings, 2, 3)).toBe(
      true,
    );
    expect(
      blockVisible(
        { ...block, kind: "AMOUNT_WORDS" },
        { ...settings, showAmountInWords: false },
        3,
        3,
      ),
    ).toBe(false);
  });
});
