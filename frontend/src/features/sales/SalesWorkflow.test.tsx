import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../../App";
import { server } from "../../test/setup";
import type { Invoice } from "../invoices/types/invoice";
import type { Quotation, Recurrence } from "./salesApi";

const base = "http://127.0.0.1:8080/api/v1";
const client = {
  id: "11111111-1111-4111-8111-111111111111",
  clientCode: "CLI-000001",
  clientName: "Atlas Client",
  address: "Rabat",
  phoneNumber: null,
  ice: null,
  active: true,
};
const invoice: Invoice = {
  id: "55555555-5555-4555-8555-555555555555",
  invoiceNumber: "FA-2026-000001",
  invoiceDate: "2026-08-01",
  client,
  paymentMethod: "BANK_TRANSFER",
  customerOrderReference: "CMD-42",
  status: "VALIDATED",
  subtotalExcludingTax: 200,
  totalDiscount: 20,
  taxableAmount: 180,
  totalVat: 36,
  totalIncludingTax: 216,
  paidAmount: 0,
  outstandingAmount: 216,
  dueDate: "2026-08-02",
  lineCount: 1,
  amountInWords: "Deux cent seize dirhams",
  pdfAvailable: false,
  pdfDownloadUrl: null,
  createdBy: "actor",
  updatedBy: "actor",
  createdByName: "Samira",
  updatedByName: "Samira",
  validatorName: "Samira",
  validatorUserId: "actor",
  createdAt: "2026-08-01T10:00:00Z",
  updatedAt: "2026-08-01T10:00:00Z",
  validatedAt: "2026-08-01T11:00:00Z",
  cancelledAt: null,
  version: 0,
  lines: [
    {
      id: "line",
      productId: "product",
      productCode: "PRD-000001",
      productName: "Papier",
      productDescription: null,
      unit: "PIECE",
      quantity: 2,
      unitPriceExcludingTax: 100,
      vatRate: 20,
      discountRate: 10,
      grossAmountExcludingTax: 200,
      discountAmount: 20,
      netAmountExcludingTax: 180,
      vatAmount: 36,
      totalIncludingTax: 216,
    },
  ],
};
const quote: Quotation = {
  id: "q1",
  quoteNumber: "DV-2026-000001",
  issueDate: "2026-08-01",
  validUntil: "2099-12-31",
  status: "OPEN",
  invoiceId: null,
  version: 2,
  terms: {
    clientId: client.id,
    client: { name: client.clientName, address: client.address, ice: null },
    lines: invoice.lines.map((l) => ({ ...l, amounts: l })),
    totals: { taxableAmount: 180, totalVat: 36, totalIncludingTax: 216 },
  },
};
const paged = <T,>(content: T[]) => ({
  content,
  page: {
    number: 0,
    size: 20,
    totalPages: content.length ? 1 : 0,
    totalElements: content.length,
  },
});
function mount(path: string) {
  server.use(
    http.post(`${base}/auth/refresh`, () =>
      HttpResponse.json({
        accessToken: "staff-token",
        tokenType: "Bearer",
        expiresIn: 900,
        user: {
          id: "actor",
          fullName: "Samira",
          email: "staff@example.com",
          role: "ADMIN",
        },
      }),
    ),
    http.get(`${base}/company`, () =>
      HttpResponse.json({ companyName: "Atlas" }),
    ),
    http.get(`${base}/company/branding`, () =>
      HttpResponse.json({ companyName: "Atlas", logoDataUrl: null }),
    ),
    http.get(`${base}/invoices/:id/payments`, () => HttpResponse.json([])),
    http.get(`${base}/invoices/:id`, () => HttpResponse.json(invoice)),
  );
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    ),
    queryClient,
  };
}
beforeEach(() => {
  window.sessionStorage.clear();
  window.history.replaceState(null, "", "/");
});
afterEach(() => {
  window.sessionStorage.clear();
  window.history.replaceState(null, "", "/");
});

describe("sales workflows", () => {
  it("converts a quote in one click and opens the resulting draft", async () => {
    let converted = false;
    server.use(
      http.get(`${base}/quotations/q1`, () =>
        HttpResponse.json({
          ...quote,
          status: converted ? "CONVERTED" : "OPEN",
          invoiceId: converted ? invoice.id : null,
        }),
      ),
      http.post(`${base}/quotations/q1/convert`, async ({ request }) => {
        expect(await request.json()).toEqual({ version: 2 });
        converted = true;
        return HttpResponse.json({
          ...invoice,
          status: "DRAFT",
          invoiceNumber: null,
        });
      }),
    );
    mount("/quotes/q1");
    server.use(
      http.get(`${base}/invoices/:id`, () =>
        HttpResponse.json({
          ...invoice,
          status: "DRAFT",
          invoiceNumber: null,
          validatedAt: null,
        }),
      ),
    );
    await userEvent
      .setup()
      .click(await screen.findByRole("button", { name: "Créer la facture" }));
    expect(
      await screen.findByRole("heading", { name: "Brouillon de facture" }),
    ).toBeVisible();
    expect(converted).toBe(true);
  });
  it("records a partial payment with the current version and refreshes the remaining balance", async () => {
    mount(`/invoices/${invoice.id}`);
    let current = invoice;
    let received: unknown;
    server.use(
      http.get(`${base}/invoices/:id`, () => HttpResponse.json(current)),
      http.post(`${base}/invoices/:id/payments`, async ({ request }) => {
        received = await request.json();
        current = {
          ...invoice,
          paidAmount: 50,
          outstandingAmount: 166,
          version: 1,
        };
        return HttpResponse.json(current);
      }),
    );
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Enregistrer un règlement" }),
    );
    await user.clear(screen.getByLabelText("Montant reçu (MAD)"));
    await user.type(screen.getByLabelText("Montant reçu (MAD)"), "50");
    await user.selectOptions(
      screen.getByLabelText("Mode de règlement"),
      "BANK_TRANSFER",
    );
    await user.type(screen.getByLabelText("Référence du règlement"), "VIR-42");
    await user.click(
      screen.getByRole("button", { name: "Confirmer le règlement" }),
    );
    await waitFor(() =>
      expect(received).toMatchObject({
        version: 0,
        amount: 50,
        paymentMethod: "BANK_TRANSFER",
        reference: "VIR-42",
      }),
    );
    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: "Confirmer le règlement" }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getByText(/166,00/)).toBeVisible();
  });
  it("shows a payment rejection while preserving the entered amount", async () => {
    mount(`/invoices/${invoice.id}`);
    server.use(
      http.post(`${base}/invoices/:id/payments`, () =>
        HttpResponse.json(
          { detail: "La facture a été modifiée. Rechargez la page" },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Enregistrer un règlement" }),
    );
    await user.selectOptions(
      screen.getByLabelText("Mode de règlement"),
      "CASH",
    );
    await user.click(
      screen.getByRole("button", { name: "Confirmer le règlement" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "La facture a été modifiée",
    );
    expect(screen.getByLabelText("Montant reçu (MAD)")).toHaveValue(216);
  });
  it("opens a reminder draft with the server's remaining balance without sending a message", async () => {
    server.use(
      http.get(`${base}/collections/overdue`, () =>
        HttpResponse.json(
          paged([{ ...invoice, paidAmount: 50, outstandingAmount: 166 }]),
        ),
      ),
      http.post(`${base}/invoices/:id/reminders`, () =>
        HttpResponse.json({
          id: "r1",
          recipient: "client@example.com",
          subject: "Rappel FA-2026-000001",
          body: "Solde restant : 166.00 MAD",
          createdAt: "2026-08-04T10:00:00Z",
        }),
      ),
    );
    mount("/collections");
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Préparer la relance" }),
    );
    const dialog = await screen.findByRole("dialog", {
      name: "Préparer la relance",
    });
    expect(within(dialog).getByLabelText("Message")).toHaveValue(
      "Solde restant : 166.00 MAD",
    );
    expect(within(dialog).getByText(/client@example.com/)).toBeVisible();
    await user.click(
      within(dialog).getByRole("button", { name: "Copier le message" }),
    );
    expect(await within(dialog).findByRole("status")).toHaveTextContent(
      "Message copié",
    );
  });
  it("creates a monthly recurring draft model from an existing invoice", async () => {
    let model: Recurrence | null = null;
    let received: unknown;
    server.use(
      http.post(`${base}/recurring-invoices`, async ({ request }) => {
        received = await request.json();
        model = {
          id: "rec1",
          title: "Facturation mensuelle",
          terms: quote.terms,
          anchorDate: "2026-10-20",
          nextDate: "2026-10-20",
          endDate: null,
          intervalMonths: 1,
          paymentTermsDays: 30,
          enabled: true,
          lastInvoiceId: null,
          lastError: null,
          version: 0,
        };
        return HttpResponse.json(model);
      }),
      http.get(`${base}/recurring-invoices`, () =>
        HttpResponse.json(paged(model ? [model] : [])),
      ),
    );
    mount(`/recurring-invoices/new?invoiceId=${invoice.id}`);
    await userEvent
      .setup()
      .click(
        await screen.findByRole("button", { name: "Créer la récurrence" }),
      );
    expect(
      await screen.findByRole("heading", { name: "Factures récurrentes" }),
    ).toBeVisible();
    expect(received).toMatchObject({
      invoiceId: invoice.id,
      intervalMonths: 1,
      paymentTermsDays: 30,
    });
    expect(await screen.findByText("Facturation mensuelle")).toBeVisible();
  });
  it("opens the client portal without restoring an internal staff session", async () => {
    const staff = vi.fn(() => new HttpResponse(null, { status: 401 }));
    const secret = "A".repeat(43);
    window.history.replaceState(null, "", `/portal#${secret}`);
    mount("/portal");
    server.use(
      http.post(`${base}/auth/refresh`, staff),
      http.get(`${base}/portal/invoices`, ({ request }) => {
        expect(request.headers.get("Authorization")).toBe(`Portal ${secret}`);
        return HttpResponse.json({
          companyName: "Atlas Fournitures",
          clientName: "Client personnel",
          invoices: [invoice],
          page: 0,
          hasNext: false,
          totalOutstanding: 216,
        });
      }),
    );
    expect(
      await screen.findByRole("heading", { name: "Atlas Fournitures" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("navigation", { name: "Navigation principale" }),
    ).not.toBeInTheDocument();
    expect(staff).not.toHaveBeenCalled();
    expect(window.location.hash).toBe("");
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Fermer cet espace" }));
    expect(window.sessionStorage.getItem("client-portal-token")).toBeNull();
    expect(screen.queryByText("FA-2026-000001")).not.toBeInTheDocument();
  });
  it("explains when a portal link has expired", async () => {
    sessionStorage.setItem("client-portal-token", "A".repeat(43));
    server.use(
      http.get(`${base}/portal/invoices`, () =>
        HttpResponse.json({ detail: "Lien expiré" }, { status: 401 }),
      ),
    );
    mount("/portal");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Ce lien est invalide, expiré ou indisponible",
    );
  });
  it("discards an in-flight PDF when the client closes the portal", async () => {
    sessionStorage.setItem("client-portal-token", "A".repeat(43));
    let releaseResponse!: () => void;
    const responseReady = new Promise<void>((resolve) => {
      releaseResponse = resolve;
    });
    let pdfStarted = false;
    let pdfResponded = false;
    server.use(
      http.get(`${base}/portal/invoices`, () =>
        HttpResponse.json({
          companyName: "Atlas Fournitures",
          clientName: "Client personnel",
          invoices: [{ ...invoice, pdfAvailable: true }],
          page: 0,
          hasNext: false,
          totalOutstanding: 216,
        }),
      ),
      http.get(`${base}/portal/invoices/:id/payments`, () =>
        HttpResponse.json([]),
      ),
      http.get(`${base}/portal/invoices/:id/pdf`, async () => {
        pdfStarted = true;
        await responseReady;
        pdfResponded = true;
        return new HttpResponse("%PDF-1.4 client invoice", {
          headers: { "Content-Type": "application/pdf" },
        });
      }),
    );
    const { queryClient } = mount("/portal");
    const user = userEvent.setup();
    try {
      await user.click(
        await screen.findByRole("button", {
          name: `Consulter le PDF ${invoice.invoiceNumber}`,
        }),
      );
      await waitFor(() => expect(pdfStarted).toBe(true));
      await user.click(
        screen.getByRole("button", { name: "Fermer cet espace" }),
      );
      expect(sessionStorage.getItem("client-portal-token")).toBeNull();
    } finally {
      releaseResponse();
    }
    await waitFor(() => expect(pdfResponded).toBe(true));
    await waitFor(() =>
      expect(queryClient.getMutationCache().getAll()).toHaveLength(0),
    );
    expect(
      queryClient
        .getQueriesData({ queryKey: ["client-portal"] })
        .every(([, data]) => data === undefined),
    ).toBe(true);
    expect(
      screen.getByRole("heading", {
        name: "Bienvenue dans votre espace client",
      }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "Télécharger le PDF" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(invoice.invoiceNumber!)).not.toBeInTheDocument();
  });
});
