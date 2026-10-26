import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HttpResponse, delay, http } from "msw";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { App } from "../../App";
import type { UserRole } from "../../auth/types";
import { server } from "../../test/setup";
import type { Product, ProductPage } from "./types/product";

const base = "http://127.0.0.1:8080/api/v1";
const product: Product = {
  id: "22222222-2222-2222-2222-222222222222",
  productCode: "PRD-000001",
  productName: "Blue Roll",
  description: "Reference product",
  unitPriceExcludingTax: 13,
  defaultVatRate: 20,
  unitPriceIncludingTax: 15.6,
  unit: "ROLL",
  active: true,
  createdAt: "2026-08-01T10:00:00Z",
  updatedAt: "2026-08-01T10:00:00Z",
  createdBy: "actor-1",
  updatedBy: "actor-1",
  version: 0,
};
const page = (
  content: Product[] = [product],
  overrides: Partial<ProductPage> = {},
): ProductPage => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  first: true,
  last: true,
  ...overrides,
});
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
    http.get(`${base}/products`, () => HttpResponse.json(page())),
    http.get(`${base}/products/:id`, () => HttpResponse.json(product)),
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

describe("product list", () => {
  it("can recover from an empty page and change the number of rows", async () => {
    const urls: URL[] = [];
    renderApp("/products?page=4", "ADMIN", [
      http.get(`${base}/products`, ({ request }) => {
        const url = new URL(request.url);
        urls.push(url);
        const currentPage = Number(url.searchParams.get("page"));
        const size = Number(url.searchParams.get("size"));
        return HttpResponse.json(
          page(currentPage ? [] : [product], {
            page: currentPage,
            size,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }),
    ]);
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Revenir à la première page" }),
    );
    expect(await screen.findByText("Blue Roll")).toBeVisible();
    await user.selectOptions(screen.getByLabelText("Lignes par page"), "50");
    await waitFor(() =>
      expect(
        urls.some(
          (url) =>
            url.searchParams.get("size") === "50" &&
            url.searchParams.get("page") === "0",
        ),
      ).toBe(true),
    );
  });
  it("shows loading and a responsive successful list with authorized actions", async () => {
    renderApp("/products", "ADMIN", [
      http.get(`${base}/products`, async () => {
        await delay(80);
        return HttpResponse.json(page());
      }),
    ]);
    expect(await screen.findByText("Chargement des produits…")).toBeVisible();
    expect(await screen.findByText("Blue Roll")).toBeVisible();
    expect(screen.getByTestId("create-product")).toBeVisible();
    expect(screen.getByRole("link", { name: "Modifier" })).toBeVisible();
    expect(screen.getByText(/15,60/)).toBeVisible();
  });

  it("shows empty and error states with retry", async () => {
    renderApp("/products", "ADMIN", [
      http.get(`${base}/products`, () => HttpResponse.json(page([]))),
    ]);
    expect(await screen.findByText("Aucun produit trouvé")).toBeVisible();
    server.resetHandlers();
    let fail = true;
    renderApp("/products", "ADMIN", [
      http.get(`${base}/products`, () =>
        fail
          ? HttpResponse.json({}, { status: 500 })
          : HttpResponse.json(page()),
      ),
    ]);
    expect(
      await screen.findByText("Impossible de charger les produits"),
    ).toBeVisible();
    fail = false;
    await userEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    expect(await screen.findByText("Blue Roll")).toBeVisible();
  });

  it("synchronizes debounced search, filters, sorting, prices and pagination with the URL", async () => {
    const urls: string[] = [];
    renderApp("/products", "ADMIN", [
      http.get(`${base}/products`, ({ request }) => {
        urls.push(request.url);
        return HttpResponse.json(
          page([product], { totalPages: 2, last: false }),
        );
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByText("Blue Roll");
    await user.type(screen.getByLabelText("Rechercher des produits"), "roll");
    await waitFor(() =>
      expect(urls.some((url) => url.includes("search=roll"))).toBe(true),
    );
    await user.selectOptions(screen.getByLabelText("Statut"), "false");
    await user.selectOptions(screen.getByLabelText("Unité"), "ROLL");
    await user.type(screen.getByLabelText("Prix minimum"), "10");
    await user.type(screen.getByLabelText("Prix maximum"), "20");
    await user.selectOptions(
      screen.getByLabelText("Trier par"),
      "unitPriceExcludingTax,desc",
    );
    await waitFor(() =>
      expect(
        urls.some(
          (url) =>
            url.includes("active=false") &&
            url.includes("unit=ROLL") &&
            url.includes("minPrice=10") &&
            url.includes("maxPrice=20") &&
            url.includes("sort=unitPriceExcludingTax,desc"),
        ),
      ).toBe(true),
    );
    await user.click(screen.getByRole("button", { name: "Suivant" }));
    await waitFor(() =>
      expect(urls.some((url) => url.includes("page=1"))).toBe(true),
    );
  });

  it("keeps viewers read-only", async () => {
    renderApp("/products", "VIEWER");
    expect(await screen.findByText("Blue Roll")).toBeVisible();
    expect(screen.queryByTestId("create-product")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Modifier" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voir" })).toBeVisible();
  });
});

describe("product form", () => {
  it("validates required and invalid financial fields", async () => {
    renderApp("/products/new");
    const user = userEvent.setup();
    await screen.findByTestId("product-create-page");
    await user.type(screen.getByLabelText("Prix unitaire HT *"), "-1");
    await user.type(
      screen.getByLabelText("Taux de TVA par défaut (%) *"),
      "101",
    );
    await user.click(screen.getByTestId("product-submit"));
    expect(
      await screen.findByText("Le nom du produit est requis"),
    ).toBeVisible();
    expect(
      screen.getByText("Le prix unitaire HT doit être positif ou nul"),
    ).toBeVisible();
    expect(
      screen.getByText(
        "Le taux de TVA par défaut doit être compris entre 0 et 100",
      ),
    ).toBeVisible();
    expect(screen.getByTestId("ttc-preview")).toHaveTextContent("—");
  });

  it("shows a live TTC preview and creates only once with trimmed values", async () => {
    let calls = 0;
    let submitted: Record<string, unknown> = {};
    let releaseResponse!: () => void;
    const responseReady = new Promise<void>((resolve) => {
      releaseResponse = resolve;
    });
    renderApp("/products/new", "ADMIN", [
      http.post(`${base}/products`, async ({ request }) => {
        calls++;
        submitted = (await request.json()) as Record<string, unknown>;
        await responseReady;
        return HttpResponse.json(product, { status: 201 });
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("product-create-page");
    await user.type(screen.getByLabelText(/Nom du produit/), "  Blue Roll  ");
    await user.type(
      screen.getByLabelText("Description"),
      "  Reference product  ",
    );
    await user.type(screen.getByLabelText("Prix unitaire HT *"), "13");
    await user.type(
      screen.getByLabelText("Taux de TVA par défaut (%) *"),
      "20",
    );
    await user.selectOptions(screen.getByLabelText("Unité *"), "ROLL");
    expect(screen.getByTestId("ttc-preview")).toHaveTextContent(/15,60/);
    const submit = screen.getByTestId("product-submit");
    try {
      await user.click(submit);
      expect(submit).toBeDisabled();
      await user.click(submit);
      await waitFor(() => expect(calls).toBe(1));
    } finally {
      releaseResponse();
    }
    expect(await screen.findByText("Produit créé avec succès.")).toBeVisible();
    expect(calls).toBe(1);
    expect(submitted).toMatchObject({
      productName: "Blue Roll",
      description: "Reference product",
      unitPriceExcludingTax: 13,
      defaultVatRate: 20,
      unit: "ROLL",
    });
  });

  it("maps backend field errors", async () => {
    renderApp("/products/new", "ADMIN", [
      http.post(`${base}/products`, () =>
        HttpResponse.json(
          {
            status: 400,
            fieldErrors: { productName: "Server rejected product name" },
          },
          { status: 400 },
        ),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("product-create-page");
    await user.type(screen.getByLabelText(/Nom du produit/), "Valid");
    await user.type(screen.getByLabelText("Prix unitaire HT *"), "1");
    await user.type(screen.getByLabelText("Taux de TVA par défaut (%) *"), "0");
    await user.click(screen.getByTestId("product-submit"));
    expect(
      await screen.findByText("Server rejected product name"),
    ).toBeVisible();
  });

  it("loads and updates without an editable code", async () => {
    let current = product;
    renderApp(`/products/${product.id}/edit`, "ADMIN", [
      http.get(`${base}/products/:id`, () => HttpResponse.json(current)),
      http.put(`${base}/products/:id`, async ({ request }) => {
        current = {
          ...current,
          ...((await request.json()) as object),
          productName: "Updated Roll",
          version: 1,
        } as Product;
        return HttpResponse.json(current);
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("product-edit-page");
    expect(screen.getByText("PRD-000001")).toBeVisible();
    expect(screen.queryByLabelText("Code produit")).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText(/Nom du produit/));
    await user.type(screen.getByLabelText(/Nom du produit/), "Updated Roll");
    await user.click(screen.getByTestId("product-submit"));
    expect(
      await screen.findByText("Produit mis à jour avec succès."),
    ).toBeVisible();
    expect(
      await screen.findByRole("heading", { name: "Updated Roll" }),
    ).toBeVisible();
  });

  it("handles stale versions and reloads current data", async () => {
    let gets = 0;
    renderApp(`/products/${product.id}/edit`, "ADMIN", [
      http.get(`${base}/products/:id`, () => {
        gets++;
        return HttpResponse.json({
          ...product,
          productName: gets > 1 ? "Latest Roll" : product.productName,
          version: gets > 1 ? 2 : 0,
        });
      }),
      http.put(`${base}/products/:id`, () =>
        HttpResponse.json(
          {
            type: `${base}/problems/product-concurrency`,
            status: 409,
            detail: "This product was modified by another user.",
          },
          { status: 409 },
        ),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByDisplayValue("Blue Roll");
    await user.click(screen.getByTestId("product-submit"));
    expect(
      await screen.findByText("This product was modified by another user."),
    ).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Recharger les dernières données" }),
    );
    expect(await screen.findByDisplayValue("Latest Roll")).toBeVisible();
  });
});

describe("product details and routing", () => {
  it.each(["ADMIN", "INVOICE_MANAGER"] as UserRole[])(
    "%s can edit and change product status",
    async (role) => {
      renderApp(`/products/${product.id}`, role);
      expect(await screen.findByTestId("product-details-page")).toBeVisible();
      expect(
        screen.getByRole("link", { name: "Modifier le produit" }),
      ).toBeVisible();
      expect(
        screen.getByRole("button", { name: /^(Désactiver|Activer)$/ }),
      ).toBeVisible();
      expect(screen.getByText("Reference product")).toBeVisible();
    },
  );
  it("renders viewer details read-only and denies mutation routes", async () => {
    renderApp(`/products/${product.id}`, "VIEWER");
    await screen.findByTestId("product-details-page");
    expect(
      screen.queryByRole("link", { name: "Modifier le produit" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^(Désactiver|Activer)$/ }),
    ).not.toBeInTheDocument();
    renderApp("/products/new", "VIEWER");
    expect(
      await screen.findByRole("heading", { name: "Accès refusé" }),
    ).toBeVisible();
    renderApp(`/products/${product.id}/edit`, "VIEWER");
    expect(
      await screen.findByRole("heading", { name: "Accès refusé" }),
    ).toBeVisible();
  });
  it("shows a not-found state", async () => {
    renderApp("/products/missing", "ADMIN", [
      http.get(`${base}/products/:id`, () =>
        HttpResponse.json({}, { status: 404 }),
      ),
    ]);
    expect(
      await screen.findByRole("heading", { name: "Produit introuvable" }),
    ).toBeVisible();
  });
  it.each([
    { active: true, action: "Désactiver" },
    { active: false, action: "Activer" },
  ])("confirms $action and changes status", async ({ active, action }) => {
    const current = { ...product, active };
    renderApp(`/products/${product.id}`, "ADMIN", [
      http.get(`${base}/products/:id`, () => HttpResponse.json(current)),
      http.patch(`${base}/products/:id/status`, async ({ request }) =>
        HttpResponse.json({
          ...current,
          ...((await request.json()) as object),
          version: 1,
        }),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("product-details-page");
    await user.click(screen.getByRole("button", { name: action }));
    expect(
      screen.getByRole("heading", { name: `${action} le produit ?` }),
    ).toBeVisible();
    await user.click(screen.getByTestId("confirm-product-status"));
    await waitFor(() =>
      expect(
        screen.getAllByText(active ? "Inactif" : "Actif").length,
      ).toBeGreaterThan(0),
    );
  });
});
