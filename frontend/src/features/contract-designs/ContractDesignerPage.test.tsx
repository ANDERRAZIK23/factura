import { HttpResponse, http } from "msw";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../../App";
import { server } from "../../test/setup";
import type { UserRole } from "../../auth/types";
import type {
  ContractDesign,
  ContractDesignSettings,
} from "./contractDesignApi";

vi.mock("./ContractPdfPreview", () => ({
  default: ({ url }: { url: string }) => (
    <div title="Aperçu PDF du contrat" data-url={url} />
  ),
}));

const base = "http://127.0.0.1:8080/api/v1";
const companies = [
  { id: "company-1", companyName: "Atlas SARL", companyCode: "ENT-000001" },
  { id: "company-2", companyName: "Beta SARL", companyCode: "ENT-000002" },
];
const settings = (title: string): ContractDesignSettings => ({
  title,
  paperFormat: "A4",
  orientation: "PORTRAIT",
  marginMm: 20,
  showLogo: true,
  pageNumbers: true,
  content: {
    type: "doc",
    content: [
      {
        type: "paragraph",
        content: [{ type: "text", text: "Conditions du contrat." }],
      },
    ],
  },
});
let designs: Record<string, ContractDesign>;
let writes: {
  companyId: string;
  settings: ContractDesignSettings;
  version: number | null;
}[];
let exports: ContractDesignSettings[];
const revoke = vi.fn();

beforeEach(() => {
  designs = Object.fromEntries(
    companies.map((company) => [
      company.id,
      {
        companyId: company.id,
        settings: settings(`Contrat ${company.companyName}`),
        version: null,
        customized: false,
        updatedAt: null,
      },
    ]),
  );
  writes = [];
  exports = [];
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: vi.fn(() => "blob:contract-preview"),
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: revoke,
  });
  revoke.mockClear();
  server.use(
    http.get(`${base}/companies`, () =>
      HttpResponse.json({ content: companies, totalElements: 2 }),
    ),
    http.get(`${base}/companies/:companyId/branding`, () =>
      HttpResponse.json({
        companyName: "Entreprise",
        logoDataUrl: null,
        updatedAt: null,
      }),
    ),
    http.get(`${base}/companies/:companyId/contract-design`, ({ params }) =>
      HttpResponse.json(designs[String(params.companyId)]),
    ),
    http.put(
      `${base}/companies/:companyId/contract-design`,
      async ({ request, params }) => {
        const body = (await request.json()) as {
          settings: ContractDesignSettings;
          version: number | null;
        };
        const id = String(params.companyId);
        writes.push({ companyId: id, ...body });
        designs[id] = {
          companyId: id,
          settings: body.settings,
          version: (designs[id].version ?? -1) + 1,
          customized: true,
          updatedAt: "2026-10-19T11:00:00Z",
        };
        return HttpResponse.json(designs[id]);
      },
    ),
    http.post(
      `${base}/companies/:companyId/contract-design/pdf`,
      async ({ request }) => {
        exports.push((await request.json()) as ContractDesignSettings);
        return new HttpResponse("%PDF-1.4 test", {
          headers: { "Content-Type": "application/pdf" },
        });
      },
    ),
  );
});

function open(
  role: UserRole = "APP_ADMIN",
  path = "/contract-designer?companyId=company-1",
) {
  server.use(
    http.post(`${base}/auth/refresh`, () =>
      HttpResponse.json({
        accessToken: "test-token",
        tokenType: "Bearer",
        expiresIn: 900,
        user: {
          id: "user-1",
          fullName: "Admin Application",
          email: "admin@example.com",
          role,
        },
      }),
    ),
  );
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

describe("contract designer", () => {
  it("opens the selected company's document and saves its rich content and page settings", async () => {
    const user = userEvent.setup();
    open();
    const title = await screen.findByRole(
      "textbox",
      {
        name: "Titre du modèle",
      },
      { timeout: 5000 },
    );
    expect(title).toHaveValue("Contrat Atlas SARL");
    expect(
      screen.getByRole("textbox", { name: "Contenu du contrat" }),
    ).toHaveTextContent("Conditions du contrat.");
    await user.clear(title);
    await user.type(title, "Conditions Atlas 2026");
    await user.click(screen.getByRole("tab", { name: "Mise en page" }));
    await user.selectOptions(screen.getByLabelText("Marges du document"), "25");
    await user.selectOptions(
      screen.getByLabelText("Orientation du document"),
      "LANDSCAPE",
    );
    await user.click(screen.getByRole("button", { name: "Enregistrer" }));
    expect(
      await screen.findByText("Modèle enregistré pour Atlas SARL."),
    ).toBeVisible();
    expect(writes).toHaveLength(1);
    expect(writes[0]).toMatchObject({
      companyId: "company-1",
      version: null,
      settings: {
        title: "Conditions Atlas 2026",
        marginMm: 25,
        orientation: "LANDSCAPE",
      },
    });
    expect(writes[0].settings.content.content?.[0].content?.[0].text).toBe(
      "Conditions du contrat.",
    );
    await user.clear(title);
    await user.type(title, "Deuxième version");
    await user.click(screen.getByRole("button", { name: "Enregistrer" }));
    await screen.findByText("Modèle enregistré pour Atlas SARL.");
    expect(writes[1].version).toBe(0);
  });

  it("confirms unsaved edits before switching companies and loads each independent model", async () => {
    const user = userEvent.setup();
    open();
    const title = await screen.findByLabelText("Titre du modèle");
    await user.type(title, " modifié");
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle de contrat"),
      "company-2",
    );
    let dialog = screen.getByRole("dialog", { name: "Changer d’entreprise ?" });
    await user.click(within(dialog).getByRole("button", { name: "Annuler" }));
    expect(title).toHaveValue("Contrat Atlas SARL modifié");
    expect(
      screen.getByLabelText("Entreprise du modèle de contrat"),
    ).toHaveValue("company-1");
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle de contrat"),
      "company-2",
    );
    dialog = screen.getByRole("dialog", { name: "Changer d’entreprise ?" });
    await user.click(
      within(dialog).getByRole("button", { name: "Changer sans enregistrer" }),
    );
    expect(await screen.findByDisplayValue("Contrat Beta SARL")).toBeVisible();
    expect(writes).toHaveLength(0);
  });

  it("protects unsaved edits when navigating through the sidebar", async () => {
    const user = userEvent.setup();
    open();
    const title = await screen.findByLabelText("Titre du modèle");
    await user.type(title, " à conserver");
    await user.click(screen.getByRole("link", { name: "Entreprises" }));
    const dialog = screen.getByRole("dialog", {
      name: "Quitter le modèle de contrat ?",
    });
    await user.click(within(dialog).getByRole("button", { name: "Annuler" }));
    expect(title).toHaveValue("Contrat Atlas SARL à conserver");
    expect(screen.getByTestId("contract-designer-page")).toBeVisible();
    expect(writes).toHaveLength(0);
  });

  it("preserves edits after a version conflict and reloads only after confirmation", async () => {
    const user = userEvent.setup();
    server.use(
      http.put(`${base}/companies/:companyId/contract-design`, () =>
        HttpResponse.json(
          { detail: "Le modèle a été modifié par un autre administrateur." },
          { status: 409 },
        ),
      ),
    );
    open();
    const title = await screen.findByLabelText("Titre du modèle");
    await user.type(title, " à conserver");
    await user.click(screen.getByRole("button", { name: "Enregistrer" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "autre administrateur",
    );
    expect(title).toHaveValue("Contrat Atlas SARL à conserver");
    designs["company-1"] = {
      ...designs["company-1"],
      settings: settings("Version enregistrée ailleurs"),
      version: 1,
      customized: true,
    };
    await user.click(
      screen.getByRole("button", { name: "Recharger le modèle enregistré" }),
    );
    const dialog = screen.getByRole("dialog", {
      name: "Recharger le modèle enregistré ?",
    });
    expect(title).toHaveValue("Contrat Atlas SARL à conserver");
    await user.click(within(dialog).getByRole("button", { name: "Recharger" }));
    expect(
      await screen.findByDisplayValue("Version enregistrée ailleurs"),
    ).toBeVisible();
  });

  it("previews current unsaved content as PDF without saving and releases the blob on close", async () => {
    const user = userEvent.setup();
    open();
    await user.type(
      await screen.findByLabelText("Titre du modèle"),
      " en cours",
    );
    await user.click(screen.getByRole("button", { name: "Aperçu PDF" }));
    const dialog = await screen.findByRole("dialog", {
      name: "Aperçu PDF du contrat",
    });
    expect(within(dialog).getByTitle("Aperçu PDF du contrat")).toHaveAttribute(
      "data-url",
      "blob:contract-preview",
    );
    expect(exports[0].title).toBe("Contrat Atlas SARL en cours");
    expect(writes).toHaveLength(0);
    await user.click(
      within(dialog).getByRole("button", { name: "Fermer l’aperçu PDF" }),
    );
    expect(revoke).toHaveBeenCalledWith("blob:contract-preview");
  });

  it("shows an explicit loading error and retries without creating a new document", async () => {
    let fails = true;
    server.use(
      http.get(`${base}/companies/:companyId/contract-design`, () =>
        fails
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json(designs["company-1"]),
      ),
    );
    const user = userEvent.setup();
    open();
    expect(
      await screen.findByRole("heading", {
        name: "Impossible de charger le modèle",
      }),
    ).toBeVisible();
    fails = false;
    await user.click(screen.getByRole("button", { name: "Réessayer" }));
    expect(await screen.findByDisplayValue("Contrat Atlas SARL")).toBeVisible();
    expect(writes).toHaveLength(0);
  });

  it.each(["ADMIN", "INVOICE_MANAGER", "VIEWER"] as const)(
    "denies %s access to the application-admin designer",
    async (role) => {
      open(role);
      expect(
        await screen.findByRole("heading", { name: "Accès refusé" }),
      ).toBeVisible();
      expect(
        screen.queryByTestId("contract-designer-page"),
      ).not.toBeInTheDocument();
    },
  );
});
