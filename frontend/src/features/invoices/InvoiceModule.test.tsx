import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HttpResponse, delay, http } from "msw";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { App } from "../../App";
import type { UserRole } from "../../auth/types";
import { server } from "../../test/setup";
import type { ClientPage } from "../clients/types/client";
import type { ProductPage, ProductUnit } from "../products/types/product";
import type { Invoice, InvoicePage, InvoiceStatus } from "./types/invoice";
import { calculateInvoiceCreatePreview } from "./utils/invoiceCreatePreview";
import { calculateInvoicePreview } from "./utils/invoicePreview";

const pdfPreview = vi.hoisted(() => vi.fn());
vi.mock("../invoice-designs/InvoicePdfPreview", () => ({
  InvoicePdfPreview: ({ blob, title }: { blob: Blob; title: string }) => {
    pdfPreview(blob);
    return (
      <div role="group" aria-label={title}>
        PDF enregistré
      </div>
    );
  },
}));

const base = "http://127.0.0.1:8080/api/v1";
const storedPdf = "%PDF-1.4 recorded invoice with company design";
function pdfResponse() {
  return new HttpResponse(storedPdf, {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": 'attachment; filename="FA-2026-000001.pdf"',
    },
  });
}
const client = {
  id: "11111111-1111-4111-8111-111111111111",
  clientCode: "CLI-000001",
  clientName: "Atlas Client",
  phoneNumber: null,
  email: null,
  ice: "001122",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  version: 0,
};
const product = {
  id: "22222222-2222-4222-8222-222222222222",
  productCode: "PRD-000001",
  productName: "Blue Roll",
  unitPriceExcludingTax: 100,
  defaultVatRate: 20,
  unitPriceIncludingTax: 120,
  unit: "ROLL" as const,
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  version: 0,
};
const line = {
  id: "44444444-4444-4444-8444-444444444444",
  productId: product.id,
  productCode: product.productCode,
  productName: product.productName,
  productDescription: null,
  unit: product.unit,
  quantity: 2,
  unitPriceExcludingTax: 100,
  vatRate: 20,
  discountRate: 10,
  grossAmountExcludingTax: 200,
  discountAmount: 20,
  netAmountExcludingTax: 180,
  vatAmount: 36,
  totalIncludingTax: 216,
};
const invoice: Invoice = {
  id: "55555555-5555-4555-8555-555555555555",
  invoiceNumber: null,
  invoiceDate: "2026-08-04",
  client: { ...client, address: "Casablanca" },
  paymentMethod: "BANK_TRANSFER",
  customerOrderReference: "PO-42",
  subtotalExcludingTax: 200,
  totalDiscount: 20,
  taxableAmount: 180,
  totalVat: 36,
  totalIncludingTax: 216,
  amountInWords: null,
  paidAmount: 0,
  outstandingAmount: 216,
  dueDate: null,
  status: "DRAFT",
  lines: [line],
  lineCount: 1,
  createdBy: "actor-1",
  updatedBy: "actor-1",
  createdByName: "Samira Bennani",
  updatedByName: "Samira Bennani",
  validatorName: null,
  validatorUserId: null,
  createdAt: "2026-08-04T10:00:00Z",
  updatedAt: "2026-08-04T10:00:00Z",
  validatedAt: null,
  cancelledAt: null,
  pdfAvailable: false,
  pdfDownloadUrl: null,
  version: 0,
};
const invoicePage = (
  content: Invoice[] = [invoice],
  overrides: Partial<InvoicePage> = {},
): InvoicePage => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  first: true,
  last: true,
  ...overrides,
});
const clients: ClientPage = {
  content: [client],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
};
const products: ProductPage = {
  content: [product],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
};
const session = (role: UserRole) => ({
  accessToken: "token",
  tokenType: "Bearer",
  expiresIn: 900,
  user: {
    id: "actor-1",
    fullName: "Test User",
    email: "user@example.com",
    role,
  },
});

function renderApp(
  path: string,
  role: UserRole = "ADMIN",
  overrides: Parameters<typeof server.use> = [],
) {
  server.use(
    http.post(`${base}/auth/refresh`, () => HttpResponse.json(session(role))),
    http.get(`${base}/invoices`, () => HttpResponse.json(invoicePage())),
    http.get(`${base}/invoices/:id`, () => HttpResponse.json(invoice)),
    http.get(`${base}/invoices/:id/pdf`, pdfResponse),
    http.get(`${base}/invoices/:id/payments`, () => HttpResponse.json([])),
    http.get(`${base}/clients`, () => HttpResponse.json(clients)),
    http.get(`${base}/clients/:id`, () =>
      HttpResponse.json({
        ...client,
        address: "Casablanca",
        createdBy: "actor-1",
        updatedBy: "actor-1",
      }),
    ),
    http.get(`${base}/products`, () => HttpResponse.json(products)),
  );
  server.use(...overrides);
  const query = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={query}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("invoice preview", () => {
  it("rounds discount and VAT at the same monetary boundaries as the server preview", () => {
    expect(
      calculateInvoicePreview([
        {
          quantity: 3,
          unitPriceExcludingTax: 10.555,
          discountRate: 5,
          vatRate: 20,
        },
      ]),
    ).toMatchObject({
      grossAmountExcludingTax: 31.67,
      discountAmount: 1.58,
      netAmountExcludingTax: 30.09,
      vatAmount: 6.02,
      totalIncludingTax: 36.11,
    });
    expect(
      calculateInvoicePreview([
        {
          quantity: "",
          unitPriceExcludingTax: 10,
          discountRate: 0,
          vatRate: 20,
        },
      ]).totalIncludingTax,
    ).toBe(0);
  });
  it("applies one global VAT rate to every discounted line", () => {
    expect(
      calculateInvoiceCreatePreview(
        [
          { quantity: 2, unitPriceExcludingTax: 100, discountRate: 10 },
          { quantity: 1, unitPriceExcludingTax: 50, discountRate: 0 },
        ],
        10,
      ),
    ).toMatchObject({
      grossAmountExcludingTax: 250,
      discountAmount: 20,
      netAmountExcludingTax: 230,
      vatAmount: 23,
      totalIncludingTax: 253,
    });
  });
});

describe("invoice list", () => {
  it("shows loading, financial columns and manager actions", async () => {
    renderApp("/invoices", "ADMIN", [
      http.get(`${base}/invoices`, async () => {
        await delay(60);
        return HttpResponse.json(invoicePage());
      }),
    ]);
    expect(await screen.findByText("Chargement des factures…")).toBeVisible();
    expect(await screen.findByText("Atlas Client")).toBeVisible();
    expect(within(screen.getByRole("table")).getByText(/216,00/)).toBeVisible();
    expect(screen.getByTestId("create-invoice")).toBeVisible();
    expect(screen.getByRole("link", { name: "Modifier" })).toBeVisible();
  });
  it("supports empty, retry, URL filters and pagination", async () => {
    renderApp("/invoices", "ADMIN", [
      http.get(`${base}/invoices`, () => HttpResponse.json(invoicePage([]))),
    ]);
    expect(await screen.findByText("Aucune facture trouvée")).toBeVisible();
    cleanup();
    server.resetHandlers();
    let fail = true;
    const urls: string[] = [];
    renderApp("/invoices", "ADMIN", [
      http.get(`${base}/invoices`, ({ request }) => {
        urls.push(request.url);
        return fail
          ? HttpResponse.json({}, { status: 500 })
          : HttpResponse.json(
              invoicePage([invoice], { totalPages: 2, last: false }),
            );
      }),
    ]);
    expect(
      await screen.findByText("Impossible de charger les factures"),
    ).toBeVisible();
    fail = false;
    await userEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    const user = userEvent.setup();
    await screen.findByText("Atlas Client");
    await user.type(screen.getByLabelText("Rechercher des factures"), "PO-42");
    await user.selectOptions(
      screen.getByLabelText("Statut de la facture"),
      "DRAFT",
    );
    await user.type(
      screen.getByLabelText("Date de facture à partir du"),
      "2026-08-01",
    );
    await user.selectOptions(
      screen.getByLabelText("Tri des factures"),
      "totalIncludingTax,desc",
    );
    await waitFor(() =>
      expect(
        urls.some(
          (url) =>
            url.includes("search=PO-42") &&
            url.includes("status=DRAFT") &&
            url.includes("invoiceDateFrom=2026-08-01") &&
            url.includes("sort=totalIncludingTax,desc"),
        ),
      ).toBe(true),
    );
    await user.click(screen.getByRole("button", { name: "Suivant" }));
    await waitFor(() =>
      expect(urls.some((url) => url.includes("page=1"))).toBe(true),
    );
  });
  it("keeps the viewer list read-only", async () => {
    renderApp("/invoices", "VIEWER");
    expect(await screen.findByText("Atlas Client")).toBeVisible();
    expect(screen.queryByTestId("create-invoice")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Modifier" }),
    ).not.toBeInTheDocument();
  });
});

describe("invoice form", () => {
  it.each<[ProductUnit, number, string]>([
    ["PIECE", 2, "240,00 MAD"],
    ["ROLL", 2, "240,00 MAD"],
    ["SERVICE", 2, "240,00 MAD"],
    ["KG", 1.001, "120,12 MAD"],
    ["METER", 1.001, "120,12 MAD"],
    ["LITER", 1.001, "120,12 MAD"],
    ["OTHER", 1.001, "120,12 MAD"],
  ])(
    "increments quantities for %s and recalculates the amount",
    async (unit, nextQuantity, total) => {
      renderApp("/invoices/new", "ADMIN", [
        http.get(`${base}/products`, () =>
          HttpResponse.json({ ...products, content: [{ ...product, unit }] }),
        ),
      ]);
      const user = userEvent.setup();
      const addLine = await screen.findByTestId("add-invoice-line");
      await waitFor(() => expect(addLine).toBeEnabled());
      await user.click(addLine);
      await user.click(screen.getByRole("combobox", { name: "Produit 1" }));
      await user.click(
        await screen.findByRole("option", { name: /Blue Roll/ }),
      );
      const quantity = screen.getByRole<HTMLInputElement>("spinbutton", {
        name: "Quantité 1",
      });
      expect(quantity.value).toBe("1");
      quantity.stepUp();
      fireEvent.input(quantity);
      expect(quantity).toHaveValue(nextQuantity);
      expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
        total,
      );
      quantity.stepDown();
      fireEvent.input(quantity);
      expect(quantity).toHaveValue(1);
      expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
        "120,00 MAD",
      );
    },
  );

  it("updates the increment when changing units without replacing the entered quantity", async () => {
    renderApp("/invoices/new", "ADMIN", [
      http.get(`${base}/products`, () =>
        HttpResponse.json({
          ...products,
          content: [
            { ...product, unit: "KG" },
            {
              ...product,
              id: "88888888-8888-4888-8888-888888888888",
              productName: "Article à la pièce",
              unit: "PIECE",
            },
          ],
        }),
      ),
    ]);
    const user = userEvent.setup();
    const addLine = await screen.findByTestId("add-invoice-line");
    await waitFor(() => expect(addLine).toBeEnabled());
    await user.click(addLine);
    const picker = screen.getByRole("combobox", { name: "Produit 1" });
    await user.click(picker);
    await user.click(await screen.findByRole("option", { name: /Blue Roll/ }));
    const quantity = screen.getByRole<HTMLInputElement>("spinbutton", {
      name: "Quantité 1",
    });
    await user.clear(quantity);
    await user.type(quantity, "5");
    await user.click(picker);
    await user.click(
      await screen.findByRole("option", { name: /Article à la pièce/ }),
    );
    expect(quantity).toHaveValue(5);
    quantity.stepUp();
    fireEvent.input(quantity);
    expect(quantity).toHaveValue(6);
    expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
      "720,00 MAD",
    );
  });

  it("keeps the stored product identity when editing a draft outside the active catalogue", async () => {
    renderApp(`/invoices/${invoice.id}/edit`, "ADMIN", [
      http.get(`${base}/products`, () =>
        HttpResponse.json({ ...products, content: [], totalElements: 0 }),
      ),
    ]);
    const picker = await screen.findByRole("combobox", { name: "Produit 1" });
    expect(picker).toHaveValue("PRD-000001 — Blue Roll");
    expect(screen.getByLabelText("Prix unitaire 1")).toHaveValue(100);
    expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
      "216,00 MAD",
    );
    const quantity = screen.getByRole<HTMLInputElement>("spinbutton", {
      name: "Quantité 1",
    });
    quantity.stepUp();
    fireEvent.input(quantity);
    expect(quantity).toHaveValue(3);
    expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
      "324,00 MAD",
    );
  });

  it("creates a client inline while preserving the invoice fields and lines", async () => {
    const newClient = {
      ...client,
      id: "66666666-6666-4666-8666-666666666666",
      clientName: "Nouveau partenaire",
      clientCode: "CLI-000002",
      ice: null,
    };
    let calls = 0;
    renderApp("/invoices/new", "ADMIN", [
      http.post(`${base}/clients`, async () => {
        calls++;
        await delay(60);
        return HttpResponse.json(newClient, { status: 201 });
      }),
      http.get(`${base}/clients/${newClient.id}`, () =>
        HttpResponse.json(newClient),
      ),
    ]);
    const user = userEvent.setup();
    const addLine = await screen.findByTestId("add-invoice-line");
    await waitFor(() => expect(addLine).toBeEnabled());
    await user.type(screen.getByLabelText("Référence client"), "BC-2026-42");
    await user.click(addLine);
    await user.click(screen.getByRole("combobox", { name: "Produit 1" }));
    await user.click(await screen.findByRole("option", { name: /Blue Roll/ }));
    await user.clear(screen.getByLabelText("Quantité 1"));
    await user.type(screen.getByLabelText("Quantité 1"), "2");
    await user.click(screen.getByRole("button", { name: "Nouveau client" }));
    const dialog = screen.getByRole("dialog", { name: "Nouveau client" });
    await user.type(
      within(dialog).getByLabelText("Nom du client *"),
      newClient.clientName,
    );
    await user.dblClick(within(dialog).getByTestId("client-submit"));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(calls).toBe(1);
    expect(screen.getByLabelText("Client *")).toHaveValue(newClient.id);
    expect(screen.getByLabelText("Référence client")).toHaveValue("BC-2026-42");
    expect(screen.getByLabelText("Quantité 1")).toHaveValue(2);
    expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
      "240,00 MAD",
    );
    await user.keyboard("{Control>}k{/Control}");
    expect(screen.getByRole("dialog", { name: "Accès rapide" })).toBeVisible();
    expect(screen.queryByTestId("invoice-line-1")).not.toBeInTheDocument();
  });

  it("searches the server catalogue and applies a product selected by keyboard", async () => {
    const remoteProduct = {
      ...product,
      id: "77777777-7777-4777-8777-777777777777",
      productName: "Service Premium",
      productCode: "PRD-000102",
      unitPriceExcludingTax: 275,
      unit: "KG" as const,
    };
    renderApp("/invoices/new", "ADMIN", [
      http.get(`${base}/products`, ({ request }) =>
        HttpResponse.json({
          ...products,
          content:
            new URL(request.url).searchParams.get("search") === "Premium"
              ? [remoteProduct]
              : [product],
        }),
      ),
    ]);
    const user = userEvent.setup();
    const addLine = await screen.findByTestId("add-invoice-line");
    await waitFor(() => expect(addLine).toBeEnabled());
    await user.click(addLine);
    const picker = screen.getByRole("combobox", { name: "Produit 1" });
    await user.type(picker, "Premium");
    await screen.findByRole("option", { name: /Service Premium/ });
    await user.keyboard("{ArrowDown}{Enter}");
    expect(picker).toHaveValue("PRD-000102 — Service Premium");
    expect(screen.getByLabelText("Prix unitaire 1")).toHaveValue(275);
    expect(screen.getByTestId("invoice-preview-total")).toHaveTextContent(
      "330,00 MAD",
    );
    await user.click(picker);
    expect(picker).toHaveAttribute("aria-expanded", "true");
    await user.keyboard("{Escape}");
    expect(picker).toHaveValue("PRD-000102 — Service Premium");
    const quantity = screen.getByRole<HTMLInputElement>("spinbutton", {
      name: "Quantité 1",
    });
    quantity.stepUp();
    fireEvent.input(quantity);
    expect(quantity).toHaveValue(1.001);
  });

  it("validates required fields and line boundaries", async () => {
    renderApp("/invoices/new");
    const user = userEvent.setup();
    await screen.findByTestId("invoice-create-page");
    await user.clear(screen.getByLabelText("Date de facture *"));
    await user.click(screen.getByTestId("add-invoice-line"));
    await user.clear(screen.getByLabelText("Quantité 1"));
    await user.type(screen.getByLabelText("Quantité 1"), "0");
    await user.click(screen.getByTestId("invoice-submit"));
    expect(
      await screen.findByText("La date de facture est requise"),
    ).toBeVisible();
    expect(screen.getByText("Le client est requis")).toBeVisible();
    expect(screen.getByText("Le produit est requis")).toBeVisible();
    expect(
      screen.getByText("La quantité doit être supérieur à zéro"),
    ).toBeVisible();
  });
  it("adds/removes a catalogue line and applies one global VAT rate", async () => {
    renderApp("/invoices/new");
    const user = userEvent.setup();
    await screen.findByTestId("invoice-create-page");
    expect(screen.queryByLabelText("Contrat actif")).not.toBeInTheDocument();
    expect(
      screen.getByLabelText("Rechercher parmi les clients actifs"),
    ).toBeVisible();
    expect(
      screen.getByLabelText("Mode de paiement *").querySelectorAll("option"),
    ).toHaveLength(3);
    expect(screen.getByLabelText("Mode de paiement *")).toHaveTextContent(
      "Espèces",
    );
    expect(screen.getByLabelText("Mode de paiement *")).toHaveTextContent(
      "Chèque",
    );
    expect(screen.getByLabelText("Mode de paiement *")).toHaveTextContent(
      "Virement bancaire",
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Client *")).toContainHTML(client.id),
    );
    await user.selectOptions(screen.getByLabelText("Client *"), client.id);
    expect(await screen.findByText("Casablanca")).toBeVisible();
    await user.click(screen.getByTestId("add-invoice-line"));
    await user.click(screen.getByRole("combobox", { name: "Produit 1" }));
    await user.click(await screen.findByRole("option", { name: /Blue Roll/ }));
    expect(screen.getByLabelText("Prix unitaire 1")).toHaveValue(100);
    expect(screen.getByLabelText("Prix unitaire 1")).not.toHaveAttribute(
      "readonly",
    );
    expect(screen.queryByLabelText("Taux de TVA 1")).not.toBeInTheDocument();
    expect(screen.getByTestId("invoice-line-total-0")).toHaveTextContent(
      "100,00 MAD",
    );
    await user.clear(screen.getByLabelText("Quantité 1"));
    await user.type(screen.getByLabelText("Quantité 1"), "2");
    await user.clear(screen.getByLabelText("Taux de remise 1"));
    await user.type(screen.getByLabelText("Taux de remise 1"), "10");
    await user.selectOptions(screen.getByLabelText("Taux de TVA *"), "10.00");
    expect(screen.getByTestId("invoice-line-total-0")).toHaveTextContent(
      "180,00 MAD",
    );
    expect(screen.getByText("198,00 MAD")).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Supprimer la ligne 1" }),
    );
    expect(screen.getByTestId("invoice-line-0")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Confirmer" }));
    expect(screen.queryByTestId("invoice-line-0")).not.toBeInTheDocument();
  });
  it("creates one normalized draft and handles API validation details", async () => {
    let calls = 0;
    let payload: Record<string, unknown> = {};
    let releaseResponse!: () => void;
    const responseReady = new Promise<void>((resolve) => {
      releaseResponse = resolve;
    });
    renderApp("/invoices/new", "ADMIN", [
      http.post(`${base}/invoices`, async ({ request }) => {
        calls++;
        payload = (await request.json()) as Record<string, unknown>;
        await responseReady;
        return HttpResponse.json(invoice, { status: 201 });
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("invoice-create-page");
    await waitFor(() =>
      expect(screen.getByLabelText("Client *")).toContainHTML(client.id),
    );
    await user.selectOptions(screen.getByLabelText("Client *"), client.id);
    await user.click(screen.getByTestId("add-invoice-line"));
    await user.click(screen.getByRole("combobox", { name: "Produit 1" }));
    await user.click(await screen.findByRole("option", { name: /Blue Roll/ }));
    const submit = screen.getByTestId("invoice-submit");
    try {
      await user.dblClick(submit);
      expect(submit).toBeDisabled();
      await waitFor(() => expect(calls).toBe(1));
    } finally {
      releaseResponse();
    }
    expect(
      await screen.findByText("Facture brouillon créée avec succès."),
    ).toBeVisible();
    expect(calls).toBe(1);
    expect(payload).toMatchObject({
      clientId: client.id,
      paymentMethod: "CASH",
      lines: [
        {
          productId: product.id,
          quantity: 1,
          unitPriceExcludingTax: 100,
          vatRate: 20,
          discountRate: 0,
        },
      ],
    });
    cleanup();
    server.resetHandlers();
    renderApp("/invoices/new", "ADMIN", [
      http.post(`${base}/invoices`, () =>
        HttpResponse.json(
          { status: 400, fieldErrors: { invoiceDate: "Server rejected date" } },
          { status: 400 },
        ),
      ),
    ]);
    const second = userEvent.setup();
    await waitFor(() =>
      expect(screen.getByLabelText("Client *")).toContainHTML(client.id),
    );
    await second.selectOptions(screen.getByLabelText("Client *"), client.id);
    await second.click(screen.getByTestId("add-invoice-line"));
    await second.click(screen.getByRole("combobox", { name: "Produit 1" }));
    await second.click(
      await screen.findByRole("option", { name: /Blue Roll/ }),
    );
    await second.click(screen.getByTestId("invoice-submit"));
    expect(await screen.findByText("Server rejected date")).toBeVisible();
  });
  it("loads an editable draft and recovers explicitly from an optimistic-lock conflict", async () => {
    let gets = 0;
    renderApp(`/invoices/${invoice.id}/edit`, "ADMIN", [
      http.get(`${base}/invoices/:id`, () => {
        gets++;
        return HttpResponse.json({
          ...invoice,
          customerOrderReference:
            gets > 1 ? "LATEST-PO" : invoice.customerOrderReference,
          version: gets > 1 ? 2 : 0,
        });
      }),
      http.put(`${base}/invoices/:id`, () =>
        HttpResponse.json(
          {
            type: `${base}/problems/invoice-concurrency`,
            status: 409,
            detail: "Another user modified this invoice.",
          },
          { status: 409 },
        ),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("invoice-edit-page");
    expect(screen.getByDisplayValue("PO-42")).toBeVisible();
    expect(screen.getByLabelText("Mode de paiement *")).toHaveValue(
      "BANK_TRANSFER",
    );
    await user.click(screen.getByTestId("invoice-submit"));
    expect(
      await screen.findByText("Another user modified this invoice."),
    ).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Recharger la facture actuelle" }),
    );
    expect(await screen.findByDisplayValue("LATEST-PO")).toBeVisible();
  });
  it("denies viewer access to invoice mutation routes", async () => {
    renderApp("/invoices/new", "VIEWER");
    expect(
      await screen.findByRole("heading", { name: "Accès refusé" }),
    ).toBeVisible();
  });
});

describe("invoice details and lifecycle", () => {
  it.each(["VALIDATED", "PAID", "CANCELLED"] as const)(
    "renders the stored PDF bytes for a %s invoice without a second HTML template",
    async (status) => {
      renderApp(`/invoices/${invoice.id}`, "VIEWER", [
        http.get(`${base}/invoices/:id`, () =>
          HttpResponse.json({
            ...invoice,
            invoiceNumber: "FA-2026-000001",
            status,
            pdfAvailable: true,
          }),
        ),
      ]);
      expect(
        await screen.findByRole("group", { name: "PDF de la facture" }),
      ).toBeVisible();
      const blob = pdfPreview.mock.lastCall?.[0] as Blob;
      const bytes = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsText(blob);
      });
      expect(bytes).toBe(storedPdf);
      expect(screen.queryByText("NOTRE ENTREPRISE")).not.toBeInTheDocument();
      expect(
        screen.queryByText("123 Avenue Mohammed V"),
      ).not.toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
    },
  );

  it("keeps draft review data and waits for validation before fetching an official PDF", async () => {
    const fetchPdf = vi.fn(pdfResponse);
    renderApp(`/invoices/${invoice.id}`, "ADMIN", [
      http.get(`${base}/invoices/:id/pdf`, fetchPdf),
    ]);
    expect(
      await screen.findByRole("heading", { name: "Récapitulatif" }),
    ).toBeVisible();
    expect(
      screen.getByText(/Le PDF définitif sera disponible après validation/),
    ).toBeVisible();
    expect(screen.getByRole("table")).toHaveTextContent("Blue Roll");
    expect(fetchPdf).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("button", { name: "Télécharger PDF" }),
    ).not.toBeInTheDocument();
  });

  it("reports a PDF fetch failure and lets the user retry", async () => {
    let requests = 0;
    renderApp(`/invoices/${invoice.id}`, "VIEWER", [
      http.get(`${base}/invoices/:id`, () =>
        HttpResponse.json({
          ...invoice,
          invoiceNumber: "FA-2026-000001",
          status: "VALIDATED",
          pdfAvailable: true,
        }),
      ),
      http.get(`${base}/invoices/:id/pdf`, async () => {
        requests++;
        await delay(30);
        return requests === 1
          ? new HttpResponse(null, { status: 503 })
          : pdfResponse();
      }),
    ]);
    expect(
      await screen.findByText("Chargement du PDF de la facture…"),
    ).toBeVisible();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Impossible de charger le PDF",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Réessayer" }));
    expect(
      await screen.findByRole("group", { name: "PDF de la facture" }),
    ).toBeVisible();
    expect(requests).toBe(2);
  });

  it.each(["ADMIN", "VIEWER"] as const)(
    "shows actual audit names instead of UUIDs to %s",
    async (role) => {
      const creatorId = "251d67ac-2ec5-46c9-b50c-94d7fc7f6ef9";
      renderApp(`/invoices/${invoice.id}`, role, [
        http.get(`${base}/invoices/:id`, () =>
          HttpResponse.json({
            ...invoice,
            createdBy: creatorId,
            createdByName: "Samira Bennani",
            updatedBy: "11111111-2222-4333-8444-555555555555",
            updatedByName: "Youssef Amrani",
            updatedAt: "2026-08-05T10:00:00Z",
            validatorName: "Nadia El Alami",
            validatedAt: "2026-08-05T09:00:00Z",
          }),
        ),
      ]);
      expect(await screen.findByText("Par Samira Bennani")).toBeVisible();
      expect(screen.getByText("Par Youssef Amrani")).toBeVisible();
      expect(screen.getByText("Par Nadia El Alami")).toBeVisible();
      expect(screen.queryByText(`Par ${creatorId}`)).not.toBeInTheDocument();
    },
  );

  it("uses an explicit unavailable label when the audit user no longer exists", async () => {
    renderApp(`/invoices/${invoice.id}`, "VIEWER", [
      http.get(`${base}/invoices/:id`, () =>
        HttpResponse.json({ ...invoice, createdByName: null }),
      ),
    ]);
    expect(
      await screen.findByText("Par Utilisateur indisponible"),
    ).toBeVisible();
    expect(
      screen.queryByText(`Par ${invoice.createdBy}`),
    ).not.toBeInTheDocument();
  });

  it("confirms validation and removes draft editing afterward", async () => {
    const validated = {
      ...invoice,
      invoiceNumber: "FA-2026-000001",
      status: "VALIDATED" as const,
      validatedAt: "2026-08-04T11:00:00Z",
      pdfAvailable: true,
      pdfDownloadUrl: `/api/v1/invoices/${invoice.id}/pdf`,
      amountInWords: "Deux cent seize dirhams",
      version: 1,
    };
    renderApp(`/invoices/${invoice.id}`, "ADMIN", [
      http.post(`${base}/invoices/:id/validate`, () =>
        HttpResponse.json(validated),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByRole("heading", { name: "Brouillon de facture" });
    await user.click(
      screen.getByRole("button", { name: "Valider la facture" }),
    );
    expect(screen.getByText(/attribue le numéro définitif/)).toBeVisible();
    await user.click(screen.getByTestId("confirm-invoice-action"));
    expect(
      await screen.findByRole("heading", { name: "Facture FA-2026-000001" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "Modifier le brouillon" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Télécharger PDF" }),
    ).toBeVisible();
    expect(
      await screen.findByRole("group", { name: "PDF de la facture" }),
    ).toBeVisible();
  });
  it.each([
    {
      initial: "VALIDATED",
      action: "Marquer comme payée",
      result: "PAID",
      label: "Payée",
    },
    {
      initial: "DRAFT",
      action: "Annuler",
      result: "CANCELLED",
      label: "Annulée",
    },
  ] as const)(
    "moves $initial to $result after confirmation",
    async ({ initial, action, result, label }) => {
      const current = {
        ...invoice,
        status: initial as InvoiceStatus,
        invoiceNumber: initial === "VALIDATED" ? "FA-2026-000001" : null,
      };
      const endpoint =
        action === "Marquer comme payée" ? "mark-paid" : "cancel";
      renderApp(`/invoices/${invoice.id}`, "ADMIN", [
        http.get(`${base}/invoices/:id`, () => HttpResponse.json(current)),
        http.post(`${base}/invoices/:id/${endpoint}`, () =>
          HttpResponse.json({ ...current, status: result, version: 1 }),
        ),
      ]);
      const user = userEvent.setup();
      await screen.findByRole("heading", {
        name: /Facture FA-|Brouillon de facture/,
      });
      await user.click(
        screen.getByRole("button", {
          name: action === "Annuler" ? /^Annuler la facture/ : action,
        }),
      );
      await user.click(screen.getByTestId("confirm-invoice-action"));
      expect((await screen.findAllByText(label))[0]).toBeVisible();
    },
  );
  it("refreshes the payment ledger when marking the remaining balance paid", async () => {
    let settled = false;
    const current = {
      ...invoice,
      status: "VALIDATED" as const,
      invoiceNumber: "FA-2026-000001",
      paidAmount: 40,
      outstandingAmount: 176,
    };
    const deposit = {
      id: "deposit-1",
      amount: 40,
      paymentDate: "2026-08-03",
      paymentMethod: "BANK_TRANSFER",
      reference: "Acompte initial",
      entryType: "PAYMENT",
      reversedAt: null,
      reversalReason: null,
    };
    renderApp(`/invoices/${invoice.id}`, "ADMIN", [
      http.get(`${base}/invoices/:id`, () => HttpResponse.json(current)),
      http.get(`${base}/invoices/:id/payments`, () =>
        HttpResponse.json(
          settled
            ? [
                {
                  ...deposit,
                  id: "settlement-1",
                  amount: 176,
                  reference: "Règlement du solde",
                  entryType: "SETTLEMENT",
                },
                deposit,
              ]
            : [deposit],
        ),
      ),
      http.post(`${base}/invoices/:id/mark-paid`, () => {
        settled = true;
        return HttpResponse.json({
          ...current,
          status: "PAID",
          paidAmount: 216,
          outstandingAmount: 0,
          version: 1,
        });
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByText(/Acompte initial/);
    await user.click(
      screen.getByRole("button", { name: "Marquer comme payée" }),
    );
    await user.click(screen.getByTestId("confirm-invoice-action"));
    expect(await screen.findByText(/Règlement du solde/)).toBeVisible();
  });
  it("lets viewers inspect and download PDFs but denies mutation routes", async () => {
    const current = {
      ...invoice,
      status: "VALIDATED" as const,
      invoiceNumber: "FA-2026-000001",
      pdfAvailable: true,
    };
    const createUrl = vi.fn(() => "blob:test");
    const revokeUrl = vi.fn();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: createUrl,
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: revokeUrl,
    });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => undefined);
    renderApp(`/invoices/${invoice.id}`, "VIEWER", [
      http.get(`${base}/invoices/:id`, () => HttpResponse.json(current)),
      http.get(
        `${base}/invoices/:id/pdf`,
        () =>
          new HttpResponse(new Blob(["pdf"]), {
            headers: {
              "Content-Type": "application/pdf",
              "Content-Disposition":
                'attachment; filename="FA-2026-000001.pdf"',
            },
          }),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByRole("heading", { name: "Facture FA-2026-000001" });
    expect(
      screen.queryByRole("button", { name: "Marquer comme payée" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Télécharger PDF" }));
    await waitFor(() => expect(createUrl).toHaveBeenCalled());
    expect(click).toHaveBeenCalled();
    expect(revokeUrl).toHaveBeenCalled();
    click.mockRestore();
  });
  it("shows not-found, API action errors and redirects immutable edit routes", async () => {
    renderApp("/invoices/missing", "ADMIN", [
      http.get(`${base}/invoices/:id`, () =>
        HttpResponse.json({}, { status: 404 }),
      ),
    ]);
    expect(
      await screen.findByRole("heading", {
        name: "Impossible de charger la facture",
      }),
    ).toBeVisible();
    cleanup();
    server.resetHandlers();
    renderApp(`/invoices/${invoice.id}`, "ADMIN", [
      http.post(`${base}/invoices/:id/validate`, () =>
        HttpResponse.json({ detail: "Client is inactive" }, { status: 409 }),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByRole("heading", { name: "Brouillon de facture" });
    await user.click(
      screen.getByRole("button", { name: "Valider la facture" }),
    );
    await user.click(screen.getByTestId("confirm-invoice-action"));
    expect(await screen.findByText("Client is inactive")).toBeVisible();
  });
});
