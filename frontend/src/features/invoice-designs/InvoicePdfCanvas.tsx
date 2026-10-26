import { useEffect, useRef, useState } from "react";
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentLoadingTask,
  type PDFDocumentProxy,
  type RenderTask,
} from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?worker&url";
import "./invoice-pdf-preview.css";

GlobalWorkerOptions.workerSrc = workerUrl;

export default function InvoicePdfCanvas({
  blob,
  page = 1,
  onLoad,
  onPageSize,
}: {
  blob: Blob;
  page?: number;
  onLoad?: (pages: number) => void;
  onPageSize?: (size: { width: number; height: number }) => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
  const [error, setError] = useState("");
  const [rendering, setRendering] = useState(true);
  useEffect(() => {
    let active = true;
    let task: PDFDocumentLoadingTask | undefined;
    setPdf(null);
    setError("");
    setRendering(true);
    void blob
      .arrayBuffer()
      .then((buffer) => {
        if (!active) return;
        task = getDocument({
          data: new Uint8Array(buffer),
          useSystemFonts: true,
        });
        return task.promise;
      })
      .then((document) => {
        if (active && document) {
          setPdf(document);
          onLoad?.(document.numPages);
        }
      })
      .catch(() => {
        if (active) {
          setError(
            "Impossible d’afficher le PDF. Téléchargez l’aperçu pour le consulter.",
          );
          setRendering(false);
        }
      });
    return () => {
      active = false;
      void task?.destroy();
    };
  }, [blob, onLoad]);
  useEffect(() => {
    if (!pdf) return;
    let active = true;
    let task: RenderTask | undefined;
    setError("");
    setRendering(true);
    void pdf
      .getPage(page)
      .then(async (documentPage) => {
        if (!active || !canvas.current) return;
        const target = canvas.current;
        const context = target.getContext("2d");
        if (!context) throw new Error("Canvas indisponible");
        const viewport = documentPage.getViewport({ scale: 1.5 });
        onPageSize?.({ width: viewport.width, height: viewport.height });
        target.width = Math.ceil(viewport.width);
        target.height = Math.ceil(viewport.height);
        task = documentPage.render({
          canvas: target,
          canvasContext: context,
          viewport,
        });
        await task.promise;
        if (active) setRendering(false);
      })
      .catch(() => {
        if (active) {
          setError(
            "Impossible d’afficher cette page. Téléchargez l’aperçu pour la consulter.",
          );
          setRendering(false);
        }
      });
    return () => {
      active = false;
      task?.cancel();
    };
  }, [pdf, page, onPageSize]);
  return (
    <>
      {rendering && (
        <div className="invoice-canvas-loading" role="status">
          Affichage du PDF…
        </div>
      )}
      {error && (
        <p className="invoice-canvas-error" role="alert">
          {error}
        </p>
      )}
      <canvas
        ref={canvas}
        className="invoice-pdf-canvas"
        aria-label={`Page ${page} de la facture en PDF`}
        hidden={Boolean(error)}
      />
    </>
  );
}
