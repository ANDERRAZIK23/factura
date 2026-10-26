import { useEffect, useRef, useState } from "react";
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentProxy,
  type PDFDocumentLoadingTask,
  type RenderTask,
} from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?worker&url";

GlobalWorkerOptions.workerSrc = workerUrl;

export default function ContractPdfPreview({
  url,
  blob,
}: {
  url: string;
  blob: Blob;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [pending, setPending] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    setPdf(null);
    setPageNumber(1);
    setPending(true);
    setError("");
    let loading: PDFDocumentLoadingTask | undefined;
    void blob
      .arrayBuffer()
      .then((buffer) => {
        if (!active) return;
        loading = getDocument({
          data: new Uint8Array(buffer),
          useSystemFonts: true,
        });
        return loading.promise;
      })
      .then((document) => {
        if (active && document) setPdf(document);
      })
      .catch(() => {
        if (active) {
          setError(
            "Impossible d’afficher l’aperçu PDF. Vous pouvez télécharger le document.",
          );
          setPending(false);
        }
      });
    return () => {
      active = false;
      void loading?.destroy();
    };
  }, [blob]);
  useEffect(() => {
    if (!pdf) return;
    const document = pdf;
    let active = true;
    let task: RenderTask | undefined;
    setPending(true);
    setError("");
    async function renderPage() {
      const page = await document.getPage(pageNumber);
      if (!active || !canvas.current) return;
      const target = canvas.current;
      const context = target.getContext("2d");
      if (!context) throw new Error("Le dessin du PDF n’est pas disponible.");
      const viewport = page.getViewport({ scale: 1.5 });
      target.width = Math.ceil(viewport.width);
      target.height = Math.ceil(viewport.height);
      task = page.render({ canvas: target, canvasContext: context, viewport });
      await task.promise;
      if (active) setPending(false);
    }
    void renderPage().catch(() => {
      if (active) {
        setError(
          "Impossible d’afficher cette page du PDF. Vous pouvez télécharger le document.",
        );
        setPending(false);
      }
    });
    return () => {
      active = false;
      task?.cancel();
    };
  }, [pdf, pageNumber]);
  return (
    <div className="contract-pdf-preview">
      <div className="contract-pdf-controls">
        <button
          type="button"
          className="secondary"
          disabled={!pdf || pending || pageNumber === 1}
          onClick={() => setPageNumber((page) => page - 1)}
        >
          Précédente
        </button>
        <span>
          {pdf
            ? `Page ${pageNumber} sur ${pdf.numPages}`
            : "Chargement du PDF…"}
        </span>
        <button
          type="button"
          className="secondary"
          disabled={!pdf || pending || pageNumber === pdf.numPages}
          onClick={() => setPageNumber((page) => page + 1)}
        >
          Suivante
        </button>
        <a
          href={url}
          download="modele-contrat.pdf"
          className="button-link secondary-link"
        >
          Télécharger
        </a>
      </div>
      <div className="contract-pdf-pages" aria-busy={pending}>
        {pending && <p role="status">Affichage du PDF…</p>}
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <canvas
          ref={canvas}
          role="img"
          aria-label={`Page ${pageNumber} du contrat en PDF`}
          hidden={Boolean(error) || !pdf}
        />
      </div>
    </div>
  );
}
