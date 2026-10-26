import { HttpResponse, delay, http } from "msw";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { server } from "./test/setup";
import type { UserRole } from "./auth/types";

const base = "http://127.0.0.1:8080/api/v1";
const session = (role: UserRole = "ADMIN") => ({
  accessToken: "access-token",
  tokenType: "Bearer",
  expiresIn: 900,
  user: {
    id: "1",
    companyId: "company-1",
    fullName: "Test User",
    email: "user@example.com",
    role,
  },
});

function renderApp(path = "/login") {
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
function workspaceHandlers() {
  const emptyPage = {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };
  server.use(
    http.get(`${base}/company`, () =>
      HttpResponse.json({
        id: "company-1",
        companyCode: "ENT-000001",
        companyName: "Atlas SARL",
        enabled: true,
        version: 0,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      }),
    ),
    http.get(`${base}/company/branding`, () =>
      HttpResponse.json({
        companyName: "Atlas SARL",
        logoDataUrl: null,
        updatedAt: null,
      }),
    ),
    http.get(`${base}/clients`, () => HttpResponse.json(emptyPage)),
    http.get(`${base}/products`, () => HttpResponse.json(emptyPage)),
    http.get(`${base}/invoices`, () => HttpResponse.json(emptyPage)),
  );
}
function unauthenticated() {
  workspaceHandlers();
  server.use(
    http.post(
      `${base}/auth/refresh`,
      () => new HttpResponse(null, { status: 401 }),
    ),
  );
}
function restored(role: UserRole = "ADMIN") {
  workspaceHandlers();
  server.use(
    http.post(`${base}/auth/refresh`, () => HttpResponse.json(session(role))),
  );
}

describe("authentication UI", () => {
  it("can collapse and reopen desktop navigation", async () => {
    restored();
    const user = userEvent.setup();
    renderApp("/dashboard");
    const collapse = await screen.findByRole("button", {
      name: "Réduire la navigation",
    });
    await user.click(collapse);
    const expand = screen.getByRole("button", {
      name: "Développer la navigation",
    });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    await user.click(expand);
    expect(
      screen.getByRole("button", { name: "Réduire la navigation" }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("opens quick navigation by keyboard, filters pages and respects viewer access", async () => {
    restored("VIEWER");
    const user = userEvent.setup();
    renderApp("/dashboard");
    await screen.findByRole("button", { name: /Accès rapide/ });
    await user.keyboard("{Control>}k{/Control}");
    const dialog = screen.getByRole("dialog", { name: "Accès rapide" });
    const search = within(dialog).getByRole("textbox", {
      name: "Rechercher une page",
    });
    expect(search).toHaveFocus();
    expect(
      within(dialog).queryByRole("link", {
        name: /Utilisateurs|Profil de l’entreprise|Entreprises/,
      }),
    ).not.toBeInTheDocument();
    await user.type(search, "factures");
    expect(within(dialog).getAllByRole("link")).toHaveLength(2);
    await user.click(
      within(dialog).getByRole("link", { name: /^Factures Ventes$/ }),
    );
    expect(await screen.findByTestId("invoices-page")).toBeVisible();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.keyboard("{Control>}k{/Control}");
    await user.type(
      screen.getByRole("textbox", { name: "Rechercher une page" }),
      "introuvable",
    );
    expect(
      within(screen.getByRole("dialog")).getByRole("status"),
    ).toHaveTextContent("Aucune page");
  });

  it("renders login and validates required and invalid email fields", async () => {
    unauthenticated();
    const user = userEvent.setup();
    renderApp();
    await screen.findByRole("heading", { name: "Bon retour parmi nous" });
    await user.click(screen.getByTestId("login-submit"));
    expect(
      await screen.findByText("Saisissez une adresse e-mail valide"),
    ).toBeVisible();
    expect(screen.getByText("Le mot de passe est requis")).toBeVisible();
    await user.type(screen.getByLabelText("E-mail"), "invalid");
    await user.type(screen.getByLabelText("Mot de passe"), "x");
    await user.click(screen.getByTestId("login-submit"));
    expect(
      await screen.findByText("Saisissez une adresse e-mail valide"),
    ).toBeVisible();
  });

  it("logs in and redirects to the dashboard", async () => {
    unauthenticated();
    server.use(
      http.post(`${base}/auth/login`, () => HttpResponse.json(session())),
    );
    const user = userEvent.setup();
    renderApp();
    await screen.findByRole("heading", { name: "Bon retour parmi nous" });
    await user.type(screen.getByLabelText("E-mail"), "admin@example.com");
    await user.type(screen.getByLabelText("Mot de passe"), "Password123!");
    await user.click(screen.getByTestId("login-submit"));
    expect(
      await screen.findByRole("heading", { name: "Tableau de bord" }),
    ).toBeVisible();
  });

  it("shows invalid credentials and network errors", async () => {
    unauthenticated();
    let networkFailure = false;
    server.use(
      http.post(`${base}/auth/login`, () =>
        networkFailure
          ? HttpResponse.error()
          : HttpResponse.json({}, { status: 401 }),
      ),
    );
    const user = userEvent.setup();
    renderApp();
    await screen.findByRole("heading", { name: "Bon retour parmi nous" });
    const email = screen.getByLabelText("E-mail");
    const password = screen.getByLabelText("Mot de passe");
    await user.type(email, "admin@example.com");
    await user.type(password, "wrong");
    await user.click(screen.getByTestId("login-submit"));
    expect(
      await screen.findByText("Adresse e-mail ou mot de passe incorrect"),
    ).toBeVisible();
    networkFailure = true;
    await user.click(screen.getByTestId("login-submit"));
    expect(
      await screen.findByText(
        "Impossible de contacter le serveur. Veuillez réessayer.",
      ),
    ).toBeVisible();
  });

  it("disables the submit button while login is pending", async () => {
    unauthenticated();
    server.use(
      http.post(`${base}/auth/login`, async () => {
        await delay(100);
        return HttpResponse.json(session());
      }),
    );
    const user = userEvent.setup();
    renderApp();
    await screen.findByRole("heading", { name: "Bon retour parmi nous" });
    await user.type(screen.getByLabelText("E-mail"), "admin@example.com");
    await user.type(screen.getByLabelText("Mot de passe"), "Password123!");
    await user.click(screen.getByTestId("login-submit"));
    expect(screen.getByTestId("login-submit")).toBeDisabled();
    expect(
      await screen.findByRole("heading", { name: "Tableau de bord" }),
    ).toBeVisible();
  });

  it("redirects a protected route when restoration fails", async () => {
    unauthenticated();
    renderApp("/dashboard");
    expect(
      await screen.findByRole("heading", { name: "Bon retour parmi nous" }),
    ).toBeVisible();
  });

  it("restores a session and protects role navigation", async () => {
    restored("VIEWER");
    renderApp("/dashboard");
    expect(
      await screen.findByRole("heading", { name: "Tableau de bord" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "Utilisateurs" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Profil de l’entreprise" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Contrats" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Factures" })).toBeVisible();
  });

  it("does not expose the removed contracts workspace", async () => {
    restored();
    renderApp("/contracts");
    expect(
      await screen.findByRole("heading", { name: "Page introuvable" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "Contrats" }),
    ).not.toBeInTheDocument();
  });

  it("redirects a restored non-admin away from the Users route", async () => {
    restored("INVOICE_MANAGER");
    renderApp("/users");
    expect(
      await screen.findByRole("heading", { name: "Accès refusé" }),
    ).toBeVisible();
  });

  it("shows admin links and logs out", async () => {
    restored();
    server.use(
      http.post(
        `${base}/auth/logout`,
        () => new HttpResponse(null, { status: 204 }),
      ),
    );
    const user = userEvent.setup();
    renderApp("/dashboard");
    expect(
      await screen.findByRole("link", { name: "Utilisateurs" }),
    ).toBeVisible();
    await user.click(screen.getByTestId("logout"));
    expect(
      await screen.findByRole("heading", { name: "Bon retour parmi nous" }),
    ).toBeVisible();
  });

  it("shows platform company administration only to APP_ADMIN", async () => {
    restored("APP_ADMIN");
    server.use(
      http.get(`${base}/companies`, () =>
        HttpResponse.json({
          content: [
            {
              id: "company-1",
              companyCode: "ENT-000001",
              companyName: "Atlas SARL",
              enabled: true,
              version: 0,
            },
          ],
          totalElements: 1,
        }),
      ),
    );
    renderApp("/companies");
    expect(
      await screen.findByRole("heading", { name: "Entreprises" }),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: "Entreprises" })).toBeVisible();
    expect(await screen.findByText("Atlas SARL")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Entreprise actuelle" }),
    ).toBeDisabled();
  });

  it("loads the Users page for administrators", async () => {
    restored();
    server.use(
      http.get(`${base}/users`, () =>
        HttpResponse.json({ content: [session().user] }),
      ),
    );
    renderApp("/users");
    const usersPage = await screen.findByTestId("users-page");
    expect(usersPage).toBeVisible();
    expect(
      await within(usersPage).findByText("user@example.com"),
    ).toBeVisible();
  });

  it("keeps company selection attached to its record and clears it on filtering", async () => {
    restored("APP_ADMIN");
    server.use(
      http.get(`${base}/companies`, () =>
        HttpResponse.json({
          content: [
            {
              id: "alpha",
              companyName: "Alpha SARL",
              companyCode: "ENT-001",
              enabled: true,
            },
            {
              id: "beta",
              companyName: "Beta SARL",
              companyCode: "ENT-002",
              enabled: true,
            },
          ],
          totalElements: 2,
        }),
      ),
    );
    renderApp("/companies");
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("checkbox", {
        name: "Sélectionner l'entreprise Alpha SARL",
      }),
    );
    await user.click(screen.getByRole("button", { name: "Entreprise" }));
    await user.click(screen.getByRole("button", { name: "Entreprise" }));
    expect(
      screen.getByRole("checkbox", {
        name: "Sélectionner l'entreprise Alpha SARL",
      }),
    ).toBeChecked();
    expect(
      screen.getByRole("checkbox", {
        name: "Sélectionner l'entreprise Beta SARL",
      }),
    ).not.toBeChecked();
    await user.type(
      screen.getByLabelText("Rechercher des entreprises"),
      "Beta",
    );
    expect(
      screen.getByRole("checkbox", {
        name: "Sélectionner l'entreprise Beta SARL",
      }),
    ).not.toBeChecked();
    expect(
      screen.queryByRole("button", { name: /Exporter/ }),
    ).not.toBeInTheDocument();
  });

  it("shows a recoverable user-directory error instead of an empty directory", async () => {
    restored();
    let failed = true;
    server.use(
      http.get(`${base}/users`, () =>
        failed
          ? HttpResponse.json({}, { status: 500 })
          : HttpResponse.json({
              content: [{ ...session().user, enabled: true }],
            }),
      ),
    );
    renderApp("/users");
    expect(
      await screen.findByRole("heading", {
        name: "Impossible de charger les utilisateurs",
      }),
    ).toBeVisible();
    expect(
      screen.queryByText("Aucun utilisateur trouvé"),
    ).not.toBeInTheDocument();
    failed = false;
    await userEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    expect(
      await within(screen.getByTestId("users-page")).findByText(
        "user@example.com",
      ),
    ).toBeVisible();
    await userEvent.selectOptions(screen.getByLabelText("Statut"), "inactive");
    expect(await screen.findByText("Aucun utilisateur trouvé")).toBeVisible();
    await userEvent.click(
      screen.getByRole("button", { name: "Réinitialiser" }),
    );
    expect(
      within(screen.getByTestId("users-page")).getByText("user@example.com"),
    ).toBeVisible();
  });

  it("shows Factura as the app brand and keeps company branding in the workspace context", async () => {
    restored();
    server.use(
      http.get(`${base}/company/branding`, () =>
        HttpResponse.json({
          companyName: "Atlas SARL",
          logoDataUrl: "data:image/png;base64,iVBORw0KGgo=",
          updatedAt: "2026-08-08T00:00:00Z",
        }),
      ),
    );
    const view = renderApp("/dashboard");
    const brand = await screen.findByRole("link", {
      name: "Tableau de bord Factura",
    });
    expect(within(brand).getByRole("img", { name: "Factura" })).toBeVisible();
    await waitFor(() => {
      expect(
        view.container.querySelector(".sidebar-context strong"),
      ).toHaveTextContent("Atlas SARL");
      expect(
        view.container.querySelector(".sidebar-context-mark img"),
      ).toHaveAttribute("src", "data:image/png;base64,iVBORw0KGgo=");
    });
  });

  it("lets APP_ADMIN manage users inside the selected company workspace", async () => {
    restored("APP_ADMIN");
    const company = {
      id: "company-2",
      companyCode: "ENT-000002",
      companyName: "Beta SARL",
      address: null,
      phone: null,
      email: null,
      ice: null,
      enabled: true,
      version: 0,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };
    server.use(
      http.get(`${base}/companies/company-2`, () => HttpResponse.json(company)),
      http.get(`${base}/companies/company-2/branding`, () =>
        HttpResponse.json({
          companyName: "Beta SARL",
          logoDataUrl: null,
          updatedAt: null,
        }),
      ),
      http.get(`${base}/companies/company-2/users`, () =>
        HttpResponse.json({
          content: [
            {
              ...session("VIEWER").user,
              id: "viewer-2",
              companyId: "company-2",
              fullName: "Beta Viewer",
              email: "viewer@beta.ma",
              role: "VIEWER",
              enabled: true,
            },
          ],
        }),
      ),
    );
    renderApp("/companies/company-2");
    expect(
      await screen.findByRole("heading", { name: "Beta SARL" }),
    ).toBeVisible();
    const manager = await screen.findByTestId("company-users-manager");
    expect(within(manager).getByText("viewer@beta.ma")).toBeVisible();
    expect(
      within(manager).getByRole("button", { name: "Créer l’utilisateur" }),
    ).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Importer un logo" }),
    ).toBeVisible();
  });
});
