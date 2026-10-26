import { lazy, Suspense, useCallback, useState } from "react";
import "./invoice-pdf-preview.css";

const PdfCanvas = lazy(() => import("./InvoicePdfCanvas"));

export function InvoicePdfPreview({
  blob,
  paperFormat,
  title,
  scrollable = true,
}: {
  blob: Blob;
  paperFormat?: "A4" | "LETTER";
  title: string;
  scrollable?: boolean;
}) {
  const [page, setPage] = useState(1);
  const [pages, setPages] = useState(1);
  const [pageSize, setPageSize] = useState<{
    width: number;
    height: number;
  } | null>(null);
  const loaded = useCallback((count: number) => {
    setPages(count);
    setPage(1);
  }, []);
  return (
    <div
      className={`invoice-document-preview${scrollable ? "" : " is-full-page"}`}
      role="group"
      aria-label={title}
    >
      <div className="invoice-document-preview-pages">
        <div
          className="invoice-document-preview-paper"
          style={{
            aspectRatio: pageSize
              ? `${pageSize.width} / ${pageSize.height}`
              : paperFormat === "LETTER"
                ? "612 / 792"
                : "595 / 842",
          }}
        >
          <Suspense fallback={<p role="status">Préparation de l’aperçu…</p>}>
            <PdfCanvas
              blob={blob}
              page={page}
              onLoad={loaded}
              onPageSize={setPageSize}
            />
          </Suspense>
        </div>
      </div>
      <div className="invoice-page-navigation" aria-label="Pages de l’aperçu">
        <button
          type="button"
          className="secondary"
          disabled={page === 1}
          onClick={() => setPage((value) => value - 1)}
        >
          Précédente
        </button>
        <span>
          Page {page} / {pages}
        </span>
        <button
          type="button"
          className="secondary"
          disabled={page === pages}
          onClick={() => setPage((value) => value + 1)}
        >
          Suivante
        </button>
      </div>
    </div>
  );
}
