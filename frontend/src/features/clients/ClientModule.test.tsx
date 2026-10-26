import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HttpResponse, delay, http } from "msw";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { App } from "../../App";
import type { UserRole } from "../../auth/types";
import { server } from "../../test/setup";
import type { Client, ClientPage } from "./types/client";

const base = "http://127.0.0.1:8080/api/v1";
const client: Client = {
  id: "11111111-1111-1111-1111-111111111111",
  clientCode: "CLI-000001",
  clientName: "Atlas SARL",
  phoneNumber: "+212 5 00",
  address: "Casablanca",
  email: "sales@atlas.ma",
  ice: "001527304000014",
  active: true,
  createdAt: "2026-08-01T10:00:00Z",
  updatedAt: "2026-08-01T10:00:00Z",
  createdBy: "actor-1",
  updatedBy: "actor-1",
  version: 0,
};
const page = (
  content: Client[] = [client],
  overrides: Partial<ClientPage> = {},
): ClientPage => ({
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

function handlers(role: UserRole = "ADMIN") {
  server.use(
    http.post(`${base}/auth/refresh`, () => HttpResponse.json(session(role))),
    http.get(`${base}/clients`, () => HttpResponse.json(page())),
    http.get(`${base}/clients/:id`, () => HttpResponse.json(client)),
    http.get(`${base}/clients/:id/portal-access`, () =>
      HttpResponse.json({ active: false, expiresAt: null }),
    ),
  );
}

function renderApp(
  path: string,
  role: UserRole = "ADMIN",
  overrides: Parameters<typeof server.use> = [],
) {
  handlers(role);
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

describe("client list", () => {
  it("shows loading, a successful responsive list and authorized actions", async () => {
    server.use(
      http.get(`${base}/clients`, async () => {
        await delay(80);
        return HttpResponse.json(page());
      }),
    );
    const query = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    server.use(
      http.post(`${base}/auth/refresh`, () =>
        HttpResponse.json(session("ADMIN")),
      ),
    );
    render(
      <QueryClientProvider client={query}>
        <MemoryRouter initialEntries={["/clients"]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText("Chargement des clients…")).toBeVisible();
    expect(await screen.findByText("Atlas SARL")).toBeVisible();
    expect(screen.getByTestId("create-client")).toBeVisible();
    expect(screen.getByRole("link", { name: "Modifier" })).toBeVisible();
  });

  it("shows empty and error states with retry", async () => {
    renderApp("/clients", "ADMIN", [
      http.get(`${base}/clients`, () => HttpResponse.json(page([]))),
    ]);
    expect(await screen.findByText("Aucun client trouvé")).toBeVisible();
    server.resetHandlers();
    let fail = true;
    server.use(
      http.post(`${base}/auth/refresh`, () =>
        HttpResponse.json(session("ADMIN")),
      ),
      http.get(`${base}/clients`, () =>
        fail
          ? HttpResponse.json({}, { status: 500 })
          : HttpResponse.json(page()),
      ),
    );
    const query = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={query}>
        <MemoryRouter initialEntries={["/clients"]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("Impossible de charger les clients"),
    ).toBeVisible();
    fail = false;
    await userEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    expect(await screen.findByText("Atlas SARL")).toBeVisible();
  });

  it("debounces search and keeps search, status, pagination and sort in the request URL", async () => {
    const urls: string[] = [];
    server.use(
      http.post(`${base}/auth/refresh`, () =>
        HttpResponse.json(session("ADMIN")),
      ),
      http.get(`${base}/clients`, ({ request }) => {
        urls.push(request.url);
        return HttpResponse.json(
          page([client], { totalPages: 2, last: false }),
        );
      }),
    );
    const query = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={query}>
        <MemoryRouter initialEntries={["/clients"]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText("Atlas SARL");
    await user.type(screen.getByLabelText("Rechercher des clients"), "atlas");
    await waitFor(() =>
      expect(urls).toEqual(
        expect.arrayContaining([expect.stringContaining("search=atlas")]),
      ),
    );
    expect(urls.at(-1)).toContain("page=0");
    await user.selectOptions(screen.getByLabelText("Statut"), "false");
    await waitFor(() =>
      expect(urls.some((url) => url.includes("active=false"))).toBe(true),
    );
    await user.selectOptions(
      screen.getByLabelText("Trier par"),
      "createdAt,desc",
    );
    await waitFor(() =>
      expect(urls.some((url) => url.includes("sort=createdAt,desc"))).toBe(
        true,
      ),
    );
    await user.click(screen.getByRole("button", { name: "Suivant" }));
    await waitFor(() =>
      expect(urls.some((url) => url.includes("page=1"))).toBe(true),
    );
  });

  it("hides mutation actions from viewers while retaining list and details links", async () => {
    renderApp("/clients", "VIEWER");
    expect(await screen.findByText("Atlas SARL")).toBeVisible();
    expect(screen.queryByTestId("create-client")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Modifier" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voir" })).toBeVisible();
  });
});

describe("client form", () => {
  it("validates required name, invalid email and invalid ICE next to fields", async () => {
    renderApp("/clients/new");
    const user = userEvent.setup();
    await screen.findByTestId("client-create-page");
    await user.type(screen.getByLabelText("E-mail"), "bad");
    await user.type(screen.getByLabelText("ICE"), "123");
    await user.click(screen.getByTestId("client-submit"));
    expect(
      await screen.findByText(
        "Le nom du client doit contenir au moins 2 caractères",
      ),
    ).toBeVisible();
    expect(
      screen.getByText("Saisissez une adresse e-mail valide"),
    ).toBeVisible();
    expect(
      screen.getByText("L’ICE doit contenir exactement 15 chiffres"),
    ).toBeVisible();
  });

  it("creates once, trims values, redirects and shows a success toast", async () => {
    let calls = 0;
    let submitted: Record<string, string> = {};
    renderApp("/clients/new", "ADMIN", [
      http.post(`${base}/clients`, async ({ request }) => {
        calls++;
        submitted = (await request.json()) as Record<string, string>;
        await delay(80);
        return HttpResponse.json(client, { status: 201 });
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("client-create-page");
    await user.type(screen.getByLabelText(/^Nom du client/), "  Atlas SARL  ");
    await user.type(screen.getByLabelText("E-mail"), " SALES@ATLAS.MA ");
    await user.type(screen.getByLabelText("ICE"), "001 527 304 000 014");
    const submit = screen.getByTestId("client-submit");
    await user.click(submit);
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(await screen.findByText("Client créé avec succès.")).toBeVisible();
    expect(calls).toBe(1);
    expect(submitted).toMatchObject({
      clientName: "Atlas SARL",
      email: "sales@atlas.ma",
      ice: "001527304000014",
    });
  });

  it("maps server field validation and duplicate ICE errors to their fields", async () => {
    let duplicate = false;
    renderApp("/clients/new", "ADMIN", [
      http.post(`${base}/clients`, () =>
        duplicate
          ? HttpResponse.json(
              {
                type: `${base}/problems/duplicate-client-ice`,
                status: 409,
                detail: "A client with this ICE already exists",
              },
              { status: 409 },
            )
          : HttpResponse.json(
              {
                status: 400,
                fieldErrors: { clientName: "Server rejected name" },
              },
              { status: 400 },
            ),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("client-create-page");
    await user.type(screen.getByLabelText(/^Nom du client/), "Valid Name");
    await user.click(screen.getByTestId("client-submit"));
    expect(await screen.findByText("Server rejected name")).toBeVisible();
    duplicate = true;
    await user.clear(screen.getByLabelText("ICE"));
    await user.type(screen.getByLabelText("ICE"), "001527304000014");
    await user.click(screen.getByTestId("client-submit"));
    expect(
      await screen.findByText("A client with this ICE already exists"),
    ).toBeVisible();
  });

  it("loads and updates a client successfully without an editable code field", async () => {
    let current = client;
    renderApp(`/clients/${client.id}/edit`, "ADMIN", [
      http.get(`${base}/clients/:id`, () => HttpResponse.json(current)),
      http.put(`${base}/clients/:id`, async ({ request }) => {
        current = {
          ...current,
          ...((await request.json()) as object),
          version: 1,
        } as Client;
        return HttpResponse.json(current);
      }),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("client-edit-page");
    expect(screen.getAllByText("CLI-000001")[0]).toBeVisible();
    expect(screen.queryByLabelText("Code client")).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText(/^Nom du client/));
    await user.type(screen.getByLabelText(/^Nom du client/), "Updated Atlas");
    await user.click(screen.getByTestId("client-submit"));
    expect(
      await screen.findByText("Client mis à jour avec succès."),
    ).toBeVisible();
    expect(
      await screen.findByRole("heading", { name: "Updated Atlas" }),
    ).toBeVisible();
  });

  it("shows stale-version guidance and reloads the latest data", async () => {
    let gets = 0;
    renderApp(`/clients/${client.id}/edit`, "ADMIN", [
      http.get(`${base}/clients/:id`, () => {
        gets++;
        return HttpResponse.json({
          ...client,
          clientName: gets > 1 ? "Latest Atlas" : client.clientName,
          version: gets > 1 ? 2 : 0,
        });
      }),
      http.put(`${base}/clients/:id`, () =>
        HttpResponse.json(
          {
            type: `${base}/problems/client-concurrency`,
            status: 409,
            detail: "This client was modified by another user.",
          },
          { status: 409 },
        ),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByDisplayValue("Atlas SARL");
    await user.click(screen.getByTestId("client-submit"));
    expect(
      await screen.findByText("This client was modified by another user."),
    ).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Recharger les dernières données" }),
    );
    expect(await screen.findByDisplayValue("Latest Atlas")).toBeVisible();
  });
});

describe("client details and routing", () => {
  it.each(["ADMIN", "INVOICE_MANAGER"] as UserRole[])(
    "%s sees edit and status actions",
    async (role) => {
      renderApp(`/clients/${client.id}`, role);
      expect(await screen.findByTestId("client-details-page")).toBeVisible();
      expect(
        screen.getByRole("link", { name: "Modifier le client" }),
      ).toBeVisible();
      expect(screen.getByTestId("details-status")).toBeVisible();
      expect(screen.getByText("sales@atlas.ma")).toBeVisible();
      expect(screen.getByText("actor-1")).toBeVisible();
    },
  );

  it("renders viewer details read-only and denies viewer create/edit routes", async () => {
    renderApp(`/clients/${client.id}`, "VIEWER");
    await screen.findByTestId("client-details-page");
    expect(
      screen.queryByRole("link", { name: "Modifier le client" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("details-status")).not.toBeInTheDocument();
    renderApp("/clients/new", "VIEWER");
    expect(
      await screen.findByRole("heading", { name: "Accès refusé" }),
    ).toBeVisible();
    renderApp(`/clients/${client.id}/edit`, "VIEWER");
    expect(
      await screen.findByRole("heading", { name: "Accès refusé" }),
    ).toBeVisible();
  });

  it("shows a not-found state", async () => {
    renderApp("/clients/missing", "ADMIN", [
      http.get(`${base}/clients/:id`, () =>
        HttpResponse.json({}, { status: 404 }),
      ),
    ]);
    expect(
      await screen.findByRole("heading", { name: "Client introuvable" }),
    ).toBeVisible();
  });

  it.each([
    { active: true, action: "Désactiver" },
    { active: false, action: "Activer" },
  ])("confirms $action and applies status", async ({ active, action }) => {
    const current = { ...client, active };
    renderApp(`/clients/${client.id}`, "ADMIN", [
      http.get(`${base}/clients/:id`, () => HttpResponse.json(current)),
      http.patch(`${base}/clients/:id/status`, async ({ request }) =>
        HttpResponse.json({
          ...current,
          ...((await request.json()) as object),
          version: 1,
        }),
      ),
    ]);
    const user = userEvent.setup();
    await screen.findByTestId("client-details-page");
    await user.click(screen.getByTestId("details-status"));
    expect(
      screen.getByRole("heading", { name: `${action} le client ?` }),
    ).toBeVisible();
    await user.click(screen.getByTestId("confirm-status"));
    await waitFor(() =>
      expect(
        screen.getAllByText(active ? "Inactif" : "Actif").length,
      ).toBeGreaterThan(0),
    );
  });
});
