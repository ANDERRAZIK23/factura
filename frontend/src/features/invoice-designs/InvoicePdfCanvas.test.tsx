import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import InvoicePdfCanvas from "./InvoicePdfCanvas";

const mocks = vi.hoisted(() => ({
  getDocument: vi.fn(),
  getPage: vi.fn(),
  destroy: vi.fn(),
  cancel: vi.fn(),
}));
vi.mock("pdfjs-dist", () => ({
  getDocument: mocks.getDocument,
  GlobalWorkerOptions: {},
}));
vi.mock("pdfjs-dist/build/pdf.worker.min.mjs?worker&url", () => ({
  default: "/pdf-worker.js",
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
    {} as CanvasRenderingContext2D,
  );
  mocks.getPage.mockResolvedValue({
    getViewport: () => ({ width: 900, height: 1200 }),
    render: () => ({ promise: Promise.resolve(), cancel: mocks.cancel }),
  });
  mocks.destroy.mockResolvedValue(undefined);
  mocks.getDocument.mockReturnValue({
    promise: Promise.resolve({ numPages: 3, getPage: mocks.getPage }),
    destroy: mocks.destroy,
  });
});
const blob = {
  arrayBuffer: () => Promise.resolve(new ArrayBuffer(10)),
} as Blob;

describe("invoice PDF canvas", () => {
  it("loads bytes once, reports pages and renders page changes without reloading the document", async () => {
    const loaded = vi.fn();
    const pageSize = vi.fn();
    const { rerender, unmount } = render(
      <InvoicePdfCanvas
        blob={blob}
        page={1}
        onLoad={loaded}
        onPageSize={pageSize}
      />,
    );
    await waitFor(() => expect(loaded).toHaveBeenCalledWith(3));
    await waitFor(() => expect(mocks.getPage).toHaveBeenCalledWith(1));
    await waitFor(() =>
      expect(pageSize).toHaveBeenCalledWith({ width: 900, height: 1200 }),
    );
    rerender(
      <InvoicePdfCanvas
        blob={blob}
        page={2}
        onLoad={loaded}
        onPageSize={pageSize}
      />,
    );
    await waitFor(() => expect(mocks.getPage).toHaveBeenCalledWith(2));
    expect(mocks.getDocument).toHaveBeenCalledTimes(1);
    expect(mocks.getDocument).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.any(Uint8Array) }),
    );
    unmount();
    expect(mocks.cancel).toHaveBeenCalled();
    expect(mocks.destroy).toHaveBeenCalledOnce();
  });

  it("reports a rendering failure and recovers on the next page", async () => {
    mocks.getPage.mockRejectedValueOnce(new Error("Page indisponible"));
    const { rerender } = render(<InvoicePdfCanvas blob={blob} page={1} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Impossible d’afficher cette page",
    );
    rerender(<InvoicePdfCanvas blob={blob} page={2} />);
    await waitFor(() => expect(mocks.getPage).toHaveBeenCalledWith(2));
    await waitFor(() =>
      expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
    );
    expect(screen.getByLabelText("Page 2 de la facture en PDF")).toBeVisible();
  });
});
