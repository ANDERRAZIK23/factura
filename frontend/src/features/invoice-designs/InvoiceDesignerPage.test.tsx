import { HttpResponse, http } from "msw";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../../App";
import { server } from "../../test/setup";
import type { UserRole } from "../../auth/types";
import type { InvoiceDesign, InvoiceDesignSettings } from "./invoiceDesignApi";

vi.mock("./InvoicePdfCanvas", () => ({
  default: () => <canvas aria-label="Page 1 de la facture en PDF" />,
}));

const base = "http://127.0.0.1:8080/api/v1";
const defaults: InvoiceDesignSettings = {
  template: "MODERN",
  accentColor: "#3346a8",
  fontFamily: "SANS",
  fontSize: 10,
  paperFormat: "A4",
  showLogo: true,
  logoPosition: "LEFT",
  clientPosition: "RIGHT",
  showProductCode: true,
  showDescription: true,
  showUnit: true,
  showDiscount: false,
  showVat: true,
  showAmountInWords: true,
  footerText: "",
};
const createUrl = vi.fn(() => "blob:invoice-preview");
const revokeUrl = vi.fn();
beforeAll(() => {
  Object.defineProperties(HTMLDialogElement.prototype, {
    showModal: {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.open = true;
      },
    },
    close: {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.open = false;
        this.dispatchEvent(new Event("close"));
      },
    },
  });
  // jsdom does not implement Blob.text; preserve real blob error decoding.
  if (!Blob.prototype.text)
    Blob.prototype.text = function () {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(this);
      });
    };
});
beforeEach(() => {
  createUrl.mockClear();
  revokeUrl.mockClear();
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: createUrl,
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: revokeUrl,
  });
});
function setup(role: UserRole = "ADMIN") {
  const company = {
    id: "company-1",
    companyName: "Atlas SARL",
    companyCode: "ENT-001",
    enabled: true,
    version: 0,
  };
  const records = new Map<string, InvoiceDesign>();
  const previews: {
    companyId: string | null;
    settings: InvoiceDesignSettings;
  }[] = [];
  const saves: {
    companyId: string | null;
    settings: InvoiceDesignSettings;
    version: number | null;
  }[] = [];
  server.use(
    http.post(`${base}/auth/refresh`, () =>
      HttpResponse.json({
        accessToken: "token",
        tokenType: "Bearer",
        expiresIn: 900,
        user: {
          id: "admin",
          companyId: "company-1",
          fullName: "Admin",
          email: "admin@example.com",
          role,
        },
      }),
    ),
    http.get(`${base}/company`, () => HttpResponse.json(company)),
    http.get(`${base}/company/branding`, () =>
      HttpResponse.json({
        companyName: "Atlas SARL",
        logoDataUrl: null,
        updatedAt: null,
      }),
    ),
    http.get(`${base}/companies`, () =>
      HttpResponse.json({
        content: [
          company,
          { ...company, id: "company-2", companyName: "Beta SARL" },
        ],
        totalElements: 2,
      }),
    ),
    http.get(`${base}/companies/:id/branding`, () =>
      HttpResponse.json({
        companyName: "Beta SARL",
        logoDataUrl: null,
        updatedAt: null,
      }),
    ),
    http.get(`${base}/invoice-designs`, ({ request }) =>
      HttpResponse.json(
        records.get(new URL(request.url).searchParams.get("companyId")!) ?? {
          companyId: new URL(request.url).searchParams.get("companyId"),
          settings: defaults,
          version: null,
          updatedAt: null,
          customized: false,
        },
      ),
    ),
    http.put(`${base}/invoice-designs`, async ({ request }) => {
      const body = (await request.json()) as {
        settings: InvoiceDesignSettings;
        version: number | null;
      };
      const companyId = new URL(request.url).searchParams.get("companyId")!;
      saves.push({ ...body, companyId });
      const updated: InvoiceDesign = {
        ...body,
        companyId,
        version: body.version === null ? 0 : body.version + 1,
        customized: true,
        updatedAt: "2026-09-07T12:00:00Z",
      };
      records.set(companyId, updated);
      return HttpResponse.json(updated);
    }),
    http.post(`${base}/invoice-designs/preview`, async ({ request }) => {
      previews.push({
        companyId: new URL(request.url).searchParams.get("companyId"),
        settings: (await request.json()) as InvoiceDesignSettings,
      });
      return new HttpResponse(new TextEncoder().encode("%PDF-1.4 example"), {
        headers: { "Content-Type": "application/pdf" },
      });
    }),
  );
  const query = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const view = render(
    <QueryClientProvider client={query}>
      <MemoryRouter initialEntries={["/invoice-designer"]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...view, previews, saves, records, user: userEvent.setup() };
}
async function waitForDesigner() {
  await screen.findByRole("button", { name: "Classique" });
  expect(screen.queryByLabelText("Client du modèle")).not.toBeInTheDocument();
  expect(
    screen.queryByLabelText("Rechercher un client"),
  ).not.toBeInTheDocument();
}

describe("invoice designer", () => {
  it("saves from the enlarged workshop and preserves edits and focus when closing it", async () => {
    const { user, saves } = setup();
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    await user.click(
      screen.getByRole("button", { name: "Agrandir l’atelier" }),
    );
    const dialog = screen.getByRole("dialog", {
      name: "Atelier de facture agrandi",
    });
    await user.click(
      within(dialog).getByRole("button", { name: "Aligner à droite" }),
    );
    await waitFor(
      () =>
        expect(
          within(dialog).getByRole("button", {
            name: "Appliquer à tous les clients",
          }),
        ).toBeEnabled(),
      { timeout: 2500 },
    );
    await user.click(
      within(dialog).getByRole("button", {
        name: "Appliquer à tous les clients",
      }),
    );
    expect(
      await within(dialog).findByText(
        "Modèle enregistré pour tous les clients de Atlas SARL.",
      ),
    ).toBeVisible();
    expect(saves).toHaveLength(1);
    fireEvent(dialog, new Event("cancel", { cancelable: true }));
    expect(
      screen.queryByRole("dialog", { name: "Atelier de facture agrandi" }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(52);
    expect(
      screen.getByRole("button", { name: "Agrandir l’atelier" }),
    ).toHaveFocus();
  });

  it("shows save conflicts inside the enlarged workshop without losing the composition", async () => {
    const { user } = setup();
    server.use(
      http.put(`${base}/invoice-designs`, () =>
        HttpResponse.json(
          { detail: "Le modèle a été modifié." },
          { status: 409 },
        ),
      ),
    );
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    await user.click(
      screen.getByRole("button", { name: "Agrandir l’atelier" }),
    );
    const dialog = screen.getByRole("dialog", {
      name: "Atelier de facture agrandi",
    });
    await user.click(
      within(dialog).getByRole("button", { name: "Aligner à droite" }),
    );
    await waitFor(
      () =>
        expect(
          within(dialog).getByRole("button", {
            name: "Appliquer à tous les clients",
          }),
        ).toBeEnabled(),
      { timeout: 2500 },
    );
    await user.click(
      within(dialog).getByRole("button", {
        name: "Appliquer à tous les clients",
      }),
    );
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Le modèle a été modifié.",
    );
    expect(within(dialog).getByLabelText("Position X (%)")).toHaveValue(52);
  });

  it("aligns blocks to the page and lets users edit physical measurements", async () => {
    const { user } = setup();
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    await user.click(screen.getByRole("button", { name: "Aligner à droite" }));
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(52);
    await user.click(
      screen.getByRole("button", { name: "Centrer horizontalement" }),
    );
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(29);
    await user.selectOptions(screen.getByLabelText("Unité des mesures"), "mm");
    expect(screen.getByLabelText("Position X (mm)")).toHaveValue(60.9);
    fireEvent.change(screen.getByLabelText("Position X (mm)"), {
      target: { value: "21" },
    });
    await user.selectOptions(screen.getByLabelText("Unité des mesures"), "%");
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(10);
    await user.click(screen.getByRole("button", { name: "Aperçu" }));
    expect(screen.queryByLabelText("Position X (%)")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Déplacer Entreprise" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Composer" }));
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(10);
  });

  it("duplicates and deletes custom elements with undo, while keeping financial blocks", async () => {
    const { user } = setup();
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    await user.click(screen.getByRole("button", { name: "Texte" }));
    fireEvent.change(screen.getByLabelText("Texte du bloc"), {
      target: { value: "Conditions de règlement" },
    });
    await user.click(screen.getByRole("button", { name: "Dupliquer le bloc" }));
    expect(screen.getByLabelText("Texte du bloc")).toHaveValue(
      "Conditions de règlement",
    );
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(8);
    expect(
      screen.getByRole("option", { name: "Texte libre 2" }),
    ).toBeInTheDocument();
    screen.getByRole("button", { name: "Déplacer Texte libre 2" }).focus();
    await user.keyboard("{Delete}");
    expect(
      screen.queryByRole("option", { name: "Texte libre 2" }),
    ).not.toBeInTheDocument();
    screen.getByRole("button", { name: "Déplacer Entreprise" }).focus();
    await user.keyboard("{Control>}z{/Control}");
    expect(
      screen.getByRole("option", { name: "Texte libre 2" }),
    ).toBeInTheDocument();
    await user.keyboard("{Delete}");
    expect(
      screen.getByRole("option", { name: "Entreprise" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^Blocs/ }));
    await user.click(
      within(screen.getByRole("list", { name: "Blocs du document" })).getByRole(
        "button",
        { name: "Totaux" },
      ),
    );
    expect(screen.getByLabelText("Bloc sélectionné")).toHaveValue("totals");
    expect(
      screen.queryByRole("button", { name: "Supprimer le bloc" }),
    ).not.toBeInTheDocument();
  });

  it("saves the shared model with Ctrl S without triggering a browser save", async () => {
    const { user, saves } = setup();
    await waitForDesigner();
    await user.type(
      screen.getByLabelText("Pied de page"),
      "Merci pour votre confiance",
    );
    await user.keyboard("{Control>}s{/Control}");
    await waitFor(() => expect(saves).toHaveLength(1));
    expect(saves[0]).toMatchObject({
      companyId: "company-1",
      settings: { footerText: "Merci pour votre confiance" },
      version: null,
    });
  });

  it("drags and resizes a block using the sheet scale and keeps one undo step per gesture", async () => {
    const { user } = setup();
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    const sheet = screen
      .getByRole("region", { name: "Composition libre de la facture" })
      .querySelector(".invoice-free-sheet")!;
    vi.spyOn(sheet, "getBoundingClientRect").mockReturnValue({
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      right: 600,
      bottom: 800,
      width: 600,
      height: 800,
      toJSON: () => ({}),
    });
    function gesture(button: HTMLElement, dx: number, dy: number) {
      Object.defineProperties(button, {
        setPointerCapture: { configurable: true, value: vi.fn() },
        hasPointerCapture: { configurable: true, value: () => true },
        releasePointerCapture: { configurable: true, value: vi.fn() },
      });
      for (const [type, x, y] of [
        ["pointerdown", 100, 100],
        ["pointermove", 100 + dx, 100 + dy],
        ["pointerup", 100 + dx, 100 + dy],
      ] as const) {
        fireEvent(
          button,
          Object.assign(new Event(type, { bubbles: true }), {
            pointerId: 1,
            button: 0,
            clientX: x,
            clientY: y,
          }),
        );
      }
    }
    gesture(
      screen.getByRole("button", { name: "Déplacer Entreprise" }),
      60,
      80,
    );
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(16);
    expect(screen.getByLabelText("Position Y (%)")).toHaveValue(22);
    gesture(
      screen.getByRole("button", { name: "Redimensionner Entreprise" }),
      60,
      56,
    );
    expect(screen.getByLabelText("Largeur (%)")).toHaveValue(52);
    expect(screen.getByLabelText("Hauteur (%)")).toHaveValue(20);
    await user.click(screen.getByRole("button", { name: "Annuler" }));
    expect(screen.getByLabelText("Largeur (%)")).toHaveValue(42);
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(16);
    await user.click(screen.getByRole("button", { name: "Annuler" }));
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(6);
  });

  it("protects an unsaved composition when following a sidebar link", async () => {
    const { user } = setup();
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    await user.click(screen.getByRole("link", { name: "Clients" }));
    const dialog = screen.getByRole("dialog", {
      name: "Quitter le modèle de facture ?",
    });
    await user.click(within(dialog).getByRole("button", { name: "Annuler" }));
    expect(
      screen.getByRole("region", { name: "Composition libre de la facture" }),
    ).toBeVisible();
  });

  it("saves free positions, rectangles and custom text and restores the composition", async () => {
    const { user, saves } = setup("APP_ADMIN");
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    const company = screen.getByRole("button", { name: "Déplacer Entreprise" });
    company.focus();
    await user.keyboard("{ArrowRight}{ArrowDown}");
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(6.1);
    expect(screen.getByLabelText("Position Y (%)")).toHaveValue(12.1);
    expect(
      screen.queryByRole("button", { name: "Supprimer le bloc" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Rectangle" }));
    fireEvent.change(screen.getByLabelText("Position X (%)"), {
      target: { value: "20" },
    });
    fireEvent.change(screen.getByLabelText("Couleur du fond"), {
      target: { value: "#fef3c7" },
    });
    await user.click(screen.getByRole("button", { name: "Texte" }));
    fireEvent.change(screen.getByLabelText("Texte du bloc"), {
      target: { value: "Merci pour votre confiance" },
    });
    await user.click(screen.getByRole("button", { name: "Annuler" }));
    expect(screen.getByLabelText("Texte du bloc")).toHaveValue("Votre texte");
    await user.click(screen.getByRole("button", { name: "Rétablir" }));
    expect(screen.getByLabelText("Texte du bloc")).toHaveValue(
      "Merci pour votre confiance",
    );
    await waitFor(
      () =>
        expect(
          screen.getByRole("button", { name: "Appliquer à tous les clients" }),
        ).toBeEnabled(),
      { timeout: 2500 },
    );
    await user.click(
      screen.getByRole("button", { name: "Appliquer à tous les clients" }),
    );
    await waitFor(() => expect(saves).toHaveLength(1));
    expect(saves[0].settings.layout?.blocks).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ kind: "COMPANY", x: 61, y: 121 }),
        expect.objectContaining({
          kind: "RECTANGLE",
          x: 200,
          backgroundColor: "#fef3c7",
        }),
        expect.objectContaining({
          kind: "TEXT",
          text: "Merci pour votre confiance",
        }),
      ]),
    );
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle"),
      "company-2",
    );
    await screen.findByRole("heading", { name: "Beta SARL" });
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle"),
      "company-1",
    );
    expect(
      await screen.findByRole("region", {
        name: "Composition libre de la facture",
      }),
    ).toBeVisible();
    expect(screen.getByLabelText("Position X (%)")).toHaveValue(6.1);
  });

  it("requires confirmation before replacing a free composition with a framed preset", async () => {
    const { user } = setup();
    await waitForDesigner();
    await user.click(screen.getByRole("button", { name: "Placement libre" }));
    await user.click(screen.getByRole("button", { name: "Texte" }));
    await user.click(screen.getByRole("button", { name: "Modèle à cadres" }));
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Annuler",
      }),
    );
    expect(screen.getByLabelText("Texte du bloc")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Modèle à cadres" }));
    await user.click(
      screen.getByRole("button", { name: "Remplacer la disposition" }),
    );
    await user.selectOptions(
      screen.getByLabelText("Bloc sélectionné"),
      "company",
    );
    expect(screen.getByLabelText("Épaisseur du cadre")).toHaveValue("1");
    expect(
      screen.queryByRole("button", { name: /^Déplacer Texte libre/ }),
    ).not.toBeInTheDocument();
  });

  it.each<UserRole>(["VIEWER", "INVOICE_MANAGER"])(
    "denies the designer to %s",
    async (role) => {
      setup(role);
      expect(
        await screen.findByRole("heading", { name: "Accès refusé" }),
      ).toBeVisible();
      expect(
        screen.queryByRole("link", { name: "Design des factures" }),
      ).not.toBeInTheDocument();
    },
  );

  it("previews and saves the shared company settings with its version", async () => {
    const { user, previews, saves, unmount } = setup();
    await waitForDesigner();
    expect(
      screen.queryByLabelText("Entreprise du modèle"),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Classique" }));
    fireEvent.change(screen.getByLabelText("Couleur principale"), {
      target: { value: "#243c83" },
    });
    await user.selectOptions(screen.getByLabelText("Bloc client"), "LEFT");
    await user.click(screen.getByLabelText("Remise par ligne"));
    await user.type(
      screen.getByLabelText("Pied de page"),
      "Merci pour votre confiance",
    );
    await waitFor(
      () =>
        expect(previews.at(-1)?.settings.footerText).toBe(
          "Merci pour votre confiance",
        ),
      { timeout: 2500 },
    );
    expect(previews.at(-1)).toMatchObject({
      companyId: "company-1",
      settings: {
        template: "CLASSIC",
        accentColor: "#243c83",
        clientPosition: "LEFT",
        showDiscount: true,
      },
    });
    expect(
      await screen.findByRole("group", { name: "Aperçu PDF de Atlas SARL" }),
    ).toBeVisible();
    expect(
      await screen.findByLabelText("Page 1 de la facture en PDF"),
    ).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Appliquer à tous les clients" }),
    );
    expect(
      await screen.findByText(
        "Modèle enregistré pour tous les clients de Atlas SARL.",
      ),
    ).toBeVisible();
    expect(saves[0]).toMatchObject({
      companyId: "company-1",
      version: null,
      settings: previews.at(-1)!.settings,
    });
    expect(
      screen.getByRole("button", { name: "Appliquer à tous les clients" }),
    ).toBeDisabled();
    await user.type(screen.getByLabelText("Pied de page"), ".");
    await user.click(
      screen.getByRole("button", { name: "Appliquer à tous les clients" }),
    );
    await waitFor(() => expect(saves).toHaveLength(2));
    expect(saves[1].version).toBe(0);
    unmount();
    expect(revokeUrl).toHaveBeenCalledWith("blob:invoice-preview");
  });

  it("requires explicit discard before switching companies with unsaved edits", async () => {
    const { user, saves } = setup("APP_ADMIN");
    await waitForDesigner();
    await user.type(
      screen.getByLabelText("Pied de page"),
      "Modification locale",
    );
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle"),
      "company-2",
    );
    const dialog = screen.getByRole("dialog", {
      name: "Changer d’entreprise ?",
    });
    expect(screen.getByLabelText("Entreprise du modèle")).toHaveValue(
      "company-1",
    );
    await user.click(within(dialog).getByRole("button", { name: "Annuler" }));
    expect(screen.getByLabelText("Pied de page")).toHaveValue(
      "Modification locale",
    );
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle"),
      "company-2",
    );
    await user.click(
      screen.getByRole("button", { name: "Changer sans enregistrer" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Beta SARL" }),
    ).toBeVisible();
    expect(screen.getByLabelText("Pied de page")).toHaveValue("");
    expect(saves).toHaveLength(0);
  });

  it("keeps edits on a conflict and reloads the recorded version explicitly", async () => {
    const { user, records } = setup();
    await waitForDesigner();
    server.use(
      http.put(`${base}/invoice-designs`, () =>
        HttpResponse.json(
          { detail: "Le modèle a été modifié." },
          { status: 409 },
        ),
      ),
    );
    await user.type(screen.getByLabelText("Pied de page"), "Mon texte");
    await user.click(
      screen.getByRole("button", { name: "Appliquer à tous les clients" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Le modèle a été modifié.",
    );
    expect(screen.getByLabelText("Pied de page")).toHaveValue("Mon texte");
    records.set("company-1", {
      companyId: "company-1",
      settings: { ...defaults, footerText: "Texte enregistré" },
      version: 4,
      customized: true,
      updatedAt: null,
    });
    await user.click(
      screen.getByRole("button", { name: "Recharger le modèle enregistré" }),
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Pied de page")).toHaveValue(
        "Texte enregistré",
      ),
    );
    expect(
      screen.getByRole("button", { name: "Appliquer à tous les clients" }),
    ).toBeDisabled();
  });

  it("lets APP_ADMIN save a design inside another company", async () => {
    const { user, saves } = setup("APP_ADMIN");
    await screen.findByRole("option", { name: "Beta SARL" });
    await user.selectOptions(
      screen.getByLabelText("Entreprise du modèle"),
      "company-2",
    );
    await user.click(
      await screen.findByRole("button", {
        name: "Appliquer à tous les clients",
      }),
    );
    await waitFor(() => expect(saves).toHaveLength(1));
    expect(saves[0]).toMatchObject({
      companyId: "company-2",
    });
  });

  it("shows a recoverable preview error", async () => {
    const { user } = setup();
    let failed = true;
    server.use(
      http.post(`${base}/invoice-designs/preview`, () =>
        failed
          ? new HttpResponse(null, { status: 500 })
          : new HttpResponse(new TextEncoder().encode("%PDF-1.4"), {
              headers: { "Content-Type": "application/pdf" },
            }),
      ),
    );
    await waitForDesigner();
    expect(
      await screen.findByRole(
        "heading",
        { name: "Aperçu indisponible" },
        { timeout: 2500 },
      ),
    ).toBeVisible();
    failed = false;
    await user.click(
      screen.getByRole("button", { name: "Réessayer l’aperçu" }),
    );
    expect(
      await screen.findByRole(
        "group",
        { name: "Aperçu PDF de Atlas SARL" },
        { timeout: 2500 },
      ),
    ).toBeVisible();
  });
});
