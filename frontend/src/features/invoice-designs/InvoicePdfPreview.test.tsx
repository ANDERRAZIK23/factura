import { useEffect } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { InvoicePdfPreview } from "./InvoicePdfPreview";

vi.mock("./InvoicePdfCanvas", () => ({
  default: function PdfCanvasMock({
    page,
    onLoad,
    onPageSize,
  }: {
    page: number;
    onLoad: (pages: number) => void;
    onPageSize: (size: { width: number; height: number }) => void;
  }) {
    useEffect(() => {
      onLoad(2);
    }, [onLoad]);
    useEffect(() => {
      onPageSize(
        page === 1 ? { width: 612, height: 792 } : { width: 842, height: 595 },
      );
    }, [page, onPageSize]);
    return <canvas aria-label={`Page ${page} de la facture en PDF`} />;
  },
}));

describe("invoice PDF preview", () => {
  it("uses each stored page's dimensions and navigates the complete document", async () => {
    render(
      <InvoicePdfPreview
        blob={new Blob(["PDF"])}
        title="Facture enregistrée"
      />,
    );
    const canvas = await screen.findByLabelText("Page 1 de la facture en PDF");
    await waitFor(() =>
      expect(canvas.parentElement).toHaveStyle({ aspectRatio: "612 / 792" }),
    );
    expect(screen.getByText("Page 1 / 2")).toBeVisible();
    expect(screen.getByRole("button", { name: "Précédente" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Suivante" }));
    expect(
      await screen.findByLabelText("Page 2 de la facture en PDF"),
    ).toBeVisible();
    await waitFor(() =>
      expect(canvas.parentElement).toHaveStyle({ aspectRatio: "842 / 595" }),
    );
    expect(screen.getByText("Page 2 / 2")).toBeVisible();
    expect(screen.getByRole("button", { name: "Suivante" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Précédente" }));
    expect(
      await screen.findByLabelText("Page 1 de la facture en PDF"),
    ).toBeVisible();
  });
});
