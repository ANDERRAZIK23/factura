# Invoice Management

Production-oriented invoice management monorepo built with Java 21, Spring Boot, PostgreSQL, Flyway, React, and TypeScript.

## Architecture

- `backend`: layered Spring Boot REST API. Flyway owns the PostgreSQL schema; Hibernate validates it.
- `frontend`: strict TypeScript React application built by Vite, with Vitest and Playwright test tooling.
- `storage/invoices`: runtime PDF root. Generated PDFs are ignored by Git.
- `scripts/test-all.sh`: cleanup-safe full verification entry point.

Detailed references: [architecture](docs/ARCHITECTURE.md), [operations](docs/OPERATIONS.md), and [security](docs/SECURITY.md).

Business modules are implemented incrementally. Backend services are authoritative for financial calculations, lifecycle transitions, numbering, authorization, snapshots, and PDF paths.

## Requirements

- Java 21
- Node.js 24 and npm 11
- Docker with Compose

## Configuration

Copy `.env.example` to `.env` and replace every example secret. Never commit `.env`.

```bash
cp .env.example .env
```

Required runtime values include the PostgreSQL password, a high-entropy JWT signing secret of at least 64 characters, explicit issuer and audience values, and the initial administrator name, email, and strong password. The administrator is created only when its normalized email is absent; its password is BCrypt-hashed and is never logged. Set `AUTH_COOKIE_SECURE=true` behind production HTTPS. PDF storage defaults to `./storage/invoices` and should be mounted on durable storage in production.

## Development

```bash
docker compose up -d --wait postgres
set -a && source .env && set +a
cd backend && ./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
cd frontend && npm ci && npm run dev
```

The public liveness endpoint is `/actuator/health`. API documentation is available at `/swagger-ui.html` only with the `dev` profile. Authentication uses short-lived JWT access tokens in browser memory and opaque, rotated refresh tokens in an HttpOnly cookie; only SHA-256 refresh-token hashes are stored. Run backend verification with `cd backend && ./mvnw clean verify`; run frontend checks with `npm run lint`, `npm run typecheck`, `npm run test:coverage`, and `npm run build`.

The complete stack command is:

```bash
./scripts/test-all.sh
```

The script always stops its temporary Compose infrastructure, including after failures.

To build and run the deployable stack after replacing all example secrets:

```bash
docker compose up -d --build --wait
```

Open `http://localhost:5173`. Only the frontend is published; Nginx serves the SPA and proxies `/api/` to the private backend service. Stop the stack with `docker compose down`. Named PostgreSQL and invoice-PDF volumes are retained unless explicitly removed.

## Client management (Phase 3)

The client module stores a UUID identifier, an immutable server-generated code, name, optional phone/address/email/ICE, active status, timestamps, authenticated creator/updater UUIDs, and an optimistic-lock version. Codes use `CLI-000001` through `CLI-999999`; PostgreSQL allocates them from a dedicated sequence, so concurrent requests cannot duplicate or reuse a code. ICE is optional, spaces are removed before storage, and a supplied value must be exactly 15 digits. A partial unique PostgreSQL index allows only one client per non-null ICE.

`ADMIN` and `INVOICE_MANAGER` users can list, view, create, update, activate, and deactivate clients. `VIEWER` users have read-only list/detail access. Status changes use a dedicated endpoint; there is intentionally no physical-delete endpoint.

Client API endpoints:

- `POST /api/v1/clients` creates an active client and returns `201` with a `Location` header.
- `GET /api/v1/clients/{id}` returns full details.
- `GET /api/v1/clients` lists and searches clients.
- `PUT /api/v1/clients/{id}` updates editable fields and requires the current `version`.
- `PATCH /api/v1/clients/{id}/status` activates or deactivates and requires the current `version`.

For example, create a client with `{"clientName":"BENELHOU FRERES SARL","phoneNumber":"+212 5 00 00 00 00","email":"contact@example.com","ice":"001527304000014"}`. Paginate and search with `GET /api/v1/clients?page=0&size=20&sort=clientName,asc&search=benelhou&active=true`; page sizes above 100 and sort fields outside the documented allowlist are rejected. To deactivate version 1, send `{"active":false,"version":1}` to the status endpoint.

Updates use optimistic locking: callers return the version they last read. A stale update returns `409 Conflict` and must be reloaded rather than overwritten. Duplicate ICE also returns a safe `409`; validation failures use RFC 7807 with a `fieldErrors` object. Development OpenAPI examples and schemas are available from `/swagger-ui.html` with the `dev` profile.

Run client and regression tests with:

```bash
cd backend && ./mvnw clean verify
cd frontend && npm run lint && npm run typecheck && npm run test:run && npm run test:coverage && npm run build && npm run test:e2e
./scripts/test-all.sh
```

## Product management (Phase 4)

The product catalogue stores immutable server-generated product codes, names and optional descriptions, commercial HT prices, default VAT rates, units, active status, audit UUIDs, timestamps, and optimistic-lock versions. Supported units are `PIECE`, `KG`, `METER`, `ROLL`, `LITER`, `SERVICE`, and `OTHER`.

Migration V4 preserves the preliminary products table created by V1. It creates and seeds a dedicated PostgreSQL sequence from any existing valid `PRD-` codes, backfills missing legacy audit users, then adds named format, nonblank-name, financial, unit, and audit constraints plus catalogue indexes. It does not drop products or dependent contract/invoice foreign keys.

Codes use `PRD-000001`, `PRD-000002`, and continue without truncation beyond six digits. Prices and VAT rates are normalized with `BigDecimal`, scale 2, and `HALF_UP`; HT prices cannot be negative and VAT must remain between 0 and 100. TTC is not stored. Responses provide a read-only TTC preview calculated as `HT × (1 + VAT / 100)`.

`ADMIN` and `INVOICE_MANAGER` users can list, view, create, update, activate, and deactivate products. `VIEWER` users have read-only access. There is intentionally no physical-delete endpoint.

Product API endpoints:

- `POST /api/v1/products` creates an active product with a generated code and `Location` header.
- `GET /api/v1/products/{id}` returns product details and the TTC preview.
- `GET /api/v1/products` supports pagination, search, status, unit, price-range filters, and approved sorting.
- `PUT /api/v1/products/{id}` updates editable fields using the current `version`.
- `PATCH /api/v1/products/{id}/status` activates or deactivates using the current `version`.

For example, create a product with `{"productName":"Blue Roll","description":"Reference product","unitPriceExcludingTax":13.005,"defaultVatRate":19.995,"unit":"ROLL"}`. The stored values are `13.01` HT and `20.00` VAT, with a `15.61` TTC preview. Filter with `GET /api/v1/products?page=0&size=20&sort=unitPriceExcludingTax,desc&search=roll&active=true&unit=ROLL&minPrice=10&maxPrice=20`. Invalid filters, unknown sorts, and pages larger than 100 return RFC 7807 validation responses; stale updates return `409 Conflict`.

Run product and regression tests with the same backend, frontend, Playwright, and root commands shown above. The dedicated PostgreSQL tests cover V1-to-V4 data preservation, constraints, concurrent code generation, authorization, filters, optimistic locking, and financial normalization.

The frontend currently pins React Router 7.18.2. The active npm advisory affects React Router RSC action handling, while this application uses client-only `BrowserRouter` and no RSC/server actions. The audit database currently lists no secure React Router release; the older npm-suggested 7.11.0 version carries broader redirect, SSR, deserialization, and denial-of-service advisories, so it is intentionally not used.

## Company contract designer

Application administrators (`APP_ADMIN`) can open **Design des contrats** from the platform sidebar or **Design du contrat** from an enterprise profile. Each company has an independent template stored in PostgreSQL with an optimistic-lock version. Other roles cannot access the designer APIs.

The editor supports headings, fonts, sizes, bold/italic/underline/strike, colors, highlighting, alignment, lists, tables, page breaks, company logos, and A4/Letter page settings. Export uses the current document, including unsaved edits. Company fields resolve to the selected enterprise's coordinates; client and contract fields remain marked for completion. Export does not create or sign a business contract.

Endpoints are `GET` and `PUT /api/v1/companies/{companyId}/contract-design`, and `POST /api/v1/companies/{companyId}/contract-design/pdf`. The PDF endpoint does not persist changes. Documents are validated against a limited schema that rejects HTML, external resources, merged/nested tables, and excessive size. PDFs use standard Latin fonts and report unsupported characters explicitly.

Migration `V10__add_company_contract_designs.sql` adds the template table without changing existing records. Frontend tests are in `ContractDesignerPage.test.tsx`; backend tests cover document validation, PDF rendering, permissions, independent company templates, stale versions, and concurrent first saves in `ContractDesignIT`.

## Legacy pricing compatibility

Contract management is available as a company-scoped workspace with list, search, status and client filters, responsive details, creation, activation and cancellation. It uses the existing versioned contract APIs and preserves the DRAFT → ACTIVE → EXPIRED/CANCELLED lifecycle; viewers retain read-only access.

## Invoice calculations and numbering (Phase 6)

The backend is authoritative for invoice arithmetic. Quantities are normalized to scale 3; unit prices, VAT rates, discount rates, and every stored monetary result use `BigDecimal`, scale 2, and `HALF_UP`. For each line it calculates gross HT (`quantity × unit price`), discount, net taxable HT, VAT, then TTC. Invoice totals are the sums of the corresponding rounded line amounts, preventing frontend arithmetic or later PDF rendering from changing persisted values.

Final numbers are allocated only inside the invoice-validation transaction. They use `FA-{YEAR}-{SEQUENCE}`, restart independently each UTC calendar year, pad values through six digits without truncating larger values, and rely on the existing `invoice_sequences` PostgreSQL table. A single atomic upsert locks and increments the applicable annual row, so concurrent committed validations cannot receive the same number. Cancelling an invoice does not decrement or recycle its sequence.

Focused unit tests cover normalization, rounding boundaries, zero and full discounts, invalid ranges, precision overflow, totals, result immutability, number formatting, and invalid sequence results. The PostgreSQL integration test exercises concurrent annual allocation, year independence, and rollback with the surrounding validation transaction.

## Invoice REST APIs (Phase 7)

Invoices begin as editable `DRAFT` aggregates without a final number. A draft may be saved without lines, but validation requires an active client, at least one active product, valid quantities, a payment method, and valid commercial terms. New invoices use catalogue products with explicit editable prices, VAT, and discounts.

Validation runs in one transaction: it checks current references and commercial values, recalculates every amount, allocates the annual number, records the validator and timestamp, and captures client and product snapshots. `VALIDATED`, `PAID`, and numbered `CANCELLED` responses use those snapshots, so later catalogue changes cannot alter historical invoices. Validated business data cannot be edited or deleted. A validated invoice can become either `PAID` or `CANCELLED`; a paid invoice cannot be cancelled by this API. Drafts may also be cancelled without receiving a number. Every mutation uses optimistic locking.

Invoice API endpoints:

- `POST /api/v1/invoices` creates a draft and returns `201` with a `Location` header.
- `GET /api/v1/invoices/{id}` returns full invoice details and historical snapshots where applicable.
- `GET /api/v1/invoices` supports pagination, search, client/status/date filters, and allowlisted sorting.
- `PUT /api/v1/invoices/{id}` replaces an editable draft using its current `version`.
- `POST /api/v1/invoices/{id}/validate` validates, snapshots, and numbers the draft.
- `POST /api/v1/invoices/{id}/mark-paid` transitions a validated invoice to paid.
- `POST /api/v1/invoices/{id}/cancel` preserves and cancels an invoice.

`ADMIN` and `INVOICE_MANAGER` may mutate invoices; `VIEWER` is read-only. Validation, query, conflict, not-found, and stale-version failures use the existing RFC 7807 response contract. Migration V6 adds invoice financial/lifecycle constraints and search indexes while preserving and audit-backfilling legacy drafts.

## Invoice PDFs (Phase 8)

Validation now generates an A4 OpenPDF document from the immutable invoice snapshots. The document contains the configured company identity, invoice and client references, product lines, quantities, HT prices, discounts, VAT, TTC amounts, totals, the total written in French, generation time, and page numbers. Line tables are explicitly paginated and repeat their header on every page. Company values come from `COMPANY_NAME`, `COMPANY_ADDRESS`, `COMPANY_PHONE`, `COMPANY_EMAIL`, and `COMPANY_ICE`.

PDFs are stored below `INVOICE_PDF_STORAGE_PATH` as server-generated relative paths such as `2026/FA-2026-000097.pdf`. File publication is atomic and refuses overwrite. Absolute paths, traversal attempts, missing files, unsafe invoice numbers, symlink escapes, and empty documents are rejected. If validation later rolls back, transaction synchronization removes the published file so the database and storage do not diverge.

Every authenticated role may download an available PDF through `GET /api/v1/invoices/{id}/pdf`. The endpoint resolves only the database path, returns `application/pdf`, disables caching, and supplies a safe attachment filename. Clients cannot submit filesystem paths. A four-page sample exercising repeated headers and final totals is generated by the renderer test under `output/pdf/phase8-sample-invoice.pdf` for visual verification.

## Invoice frontend (Phase 9)

The React invoice module replaces the earlier placeholder with role-aware list, create, edit, and detail pages. The list supports debounced search, client/status/date filters, approved sorting, URL-backed pagination, and explicit loading, empty, retry, and read-only states. `ADMIN` and `INVOICE_MANAGER` users can prepare drafts and perform permitted lifecycle actions; `VIEWER` users can inspect all invoice data and download generated PDFs without seeing mutation controls.

Draft preparation uses the active product catalogue directly. Selecting a product fills its default HT price and VAT while keeping price, VAT, discount, quantity, and payment method editable. The form prevents duplicate product selection, validates quantities and rates, handles RFC 7807 field errors and stale versions, and displays line and invoice previews using the same rounding boundaries as the backend. These values are clearly marked as previews: the Spring service remains authoritative and recalculates all financial data.

The detail page exposes commercial references, complete lines, authoritative totals, audit dates, lifecycle status, and the amount in words. Draft validation, payment confirmation, and cancellation require explicit confirmation dialogs. Validation explains that numbering, snapshots, recalculation, and PDF generation are final. Validated invoices expose an authenticated blob download so the in-memory access token is retained; immutable invoices are never routed to an edit form.

Component tests cover invoice loading/error/empty states, filtering and pagination, role restrictions, form validation, line addition/removal, editable quantity/VAT/discount previews, normalized API requests, RFC 7807 errors, lifecycle confirmations, and viewer PDF access. Playwright workflow coverage remains intentionally assigned to Phase 10.

## End-to-end invoice workflows (Phase 10)

The Playwright suite now exercises the complete invoice lifecycle against the real React application, Spring Boot API, PostgreSQL schema, authentication, and PDF filesystem. Its serial invoice workflow creates a client and two products through the UI, prepares a two-line draft from catalogue values, edits its commercial terms, verifies quantity/VAT/discount/TTC previews, validates and numbers the invoice, downloads the generated PDF, checks its `%PDF-` signature, finds the invoice in the filtered list, and confirms that validated data cannot be edited.

Additional scenarios verify that `INVOICE_MANAGER` receives a clear validation error for an empty draft and can cancel it, that `VIEWER` can inspect and download an invoice without mutation controls or API permissions, and that an administrator can mark a validated invoice as paid while its number, lines, and PDF remain preserved. Playwright retains traces on first retry plus screenshots and videos on failure. The full test runner stores generated E2E PDFs under `backend/target/e2e-invoice-pdfs`, preventing files from one isolated database run from colliding with another.

Run only the E2E suite after starting PostgreSQL and the backend:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

Run every backend, frontend, integration, and Playwright check with isolated PostgreSQL and PDF storage:

```bash
./scripts/test-all.sh
```

## Invoice and PDF workflow

Drafts do not receive final numbers. Validation will transactionally recalculate totals, allocate `FA-{YEAR}-{SEQUENCE}`, capture historical client/product terms, generate an OpenPDF document, and persist only a normalized server-controlled relative path. Back up PostgreSQL and the configured PDF directory together so database records and files remain consistent.

## Free invoice layout

In **Design des factures**, `ADMIN` edits the shared model for their company; `APP_ADMIN` can select any company. The same design applies to every current and future client of that company. No client selection or existing client is required. **Placement libre** converts the current settings into movable, resizable blocks. **Modèle à cadres** starts with framed information blocks and a grid for the articles. Add rectangles or Latin text, change fills, borders and text formatting, use snapping or precise percentages, zoom, and undo/redo. Mandatory invoice blocks cannot be deleted. The PDF shown on the sheet uses the server renderer with a fictional client; composition guides are excluded from the exported PDF.

The workshop provides a block list, properties panel, measurements in percentages or millimetres, and six page alignment commands (side alignments use 6% margins). Custom rectangles and text can be duplicated or deleted with undo support. **Aperçu** hides editing guides; **Agrandir l’atelier** opens a keyboard-accessible dialog with saving, PDF download, and Escape to return without losing edits. Ctrl/Cmd S saves the shared model; Ctrl/Cmd Z, Shift Z and D apply undo, redo and custom-block duplication while working on the canvas. PDF.js renders both automatic and free layouts with page navigation; totals and amount-in-words guides appear only on the last page.

`InvoiceDesignSettings.layout` is nullable for compatibility with existing flowing templates. When present, it stores up to 40 blocks with coordinates in thousandths of the full A4/Letter page from its top left. Geometry, colors, identifiers, unique required blocks and text lengths are validated at the API boundary. Saving also renders a sample to detect undersized blocks. Fixed content must fit its box; errors identify the box to enlarge. Article rows paginate within their box, with repeated headers and no split rows; a row taller than the available box requires increasing its height or width. Company, client, metadata, decorative blocks and footer repeat on each page, while totals and the amount in words appear on the final page. Intentional overlaps are permitted. A real invoice with longer content may require enlarging a box before validation.

Settings are saved per company using version checks through `GET/PUT /api/v1/invoice-designs?companyId=…`; `POST /api/v1/invoice-designs/preview?companyId=…` previews unsaved settings. Company scope defaults to the authenticated user's company; company administrators cannot access another company's model. New validated invoices for all clients use that layout; previously generated PDFs remain preserved. Migration V11 promotes the latest client model per company (timestamp descending, client UUID ascending for ties), preserving its settings, version and audit fields. The original client records remain available for recovery and are no longer read by the application. The contract designer remains separate.

## Devis, règlements, échéances, récurrences et portail client

Les pages **Devis**, **Échéances et relances** et **Factures récurrentes** complètent la facturation. Chaque entreprise conserve ses propres données. Les comptes `VIEWER` peuvent consulter les listes mais ne peuvent ni créer ni modifier les documents, règlements ou accès client.

- Un devis conserve un instantané de ses coordonnées, articles, prix, remises et TVA. Son PDF porte un numéro `DV-année-séquence` propre à l’entreprise. **Créer la facture** prépare un brouillon avec les conditions enregistrées, à vérifier puis valider. Une conversion répétée renvoie la même facture. Les devis expirés ou annulés ne peuvent pas être convertis.
- Le détail d’une facture affiche son échéance, le montant reçu et le solde restant. Les règlements partiels sont limités au solde. **Marquer comme payée** enregistre le règlement du solde. Une correction exige un motif et conserve la saisie initiale ; elle ne réalise aucun remboursement bancaire. Une facture avec des règlements actifs ne peut pas être annulée.
- Les retards utilisent les échéances renseignées, et non l’âge de la facture. **Préparer une relance** conserve un brouillon de message avec le solde du moment. L’utilisateur peut l’adapter et le copier dans sa messagerie. Aucun message n’est envoyé automatiquement.
- **Créer une récurrence**, depuis une facture, enregistre les articles et conditions à reprendre. Les fréquences disponibles sont mensuelle, trimestrielle, semestrielle et annuelle. Les jours de fin de mois sont calculés depuis la date initiale, sans dérive après février. Le traitement automatique prépare des brouillons, sans les valider ni les envoyer. Une erreur met la récurrence en pause et affiche sa cause ; un gestionnaire autorisé peut la reprendre. Les verrous, versions et contraintes d’unicité empêchent la préparation en double d’une période.
- La fiche client permet de créer un lien personnel valable 7, 30 ou 90 jours. Remplacer ou révoquer le lien désactive le précédent. Le portail expose uniquement les factures numérotées, leurs PDF enregistrés et le suivi des règlements du client concerné. Il ne donne aucun accès à l’espace interne et ne permet pas d’effectuer des paiements. Les secrets aléatoires de 256 bits sont conservés hachés côté serveur, transmis dans un en-tête dédié et exclus des URL des requêtes. Le navigateur garde le secret en session dans l’onglet ; **Fermer cet espace** le retire.

La migration V12 ajoute ces données sans modifier les numéros ni les PDF historiques. Les anciennes factures `PAID` sont reprises comme soldées, avec une écriture d’historique dont la date réelle de règlement reste explicitement inconnue.

Les API correspondantes sont `/api/v1/quotations`, `/api/v1/recurring-invoices`, `/api/v1/collections/overdue`, `/api/v1/invoices/{id}/payments`, `/due-date`, `/reminders`, `/api/v1/clients/{id}/portal-access` et `/api/v1/portal/invoices`. Le traitement des récurrences vérifie les échéances toutes les heures par défaut : `RECURRING_INVOICE_JOB_ENABLED=true` et `RECURRING_INVOICE_JOB_CRON="0 10 * * * *"` permettent de le configurer.

## Development data and security

The environment-supplied initial administrator is created idempotently at startup. Production startup fails clearly when its required values are absent. Production deployments should use TLS, `AUTH_COOKIE_SECURE=true`, a high-entropy JWT secret, restricted CORS origins, database backups, PDF directory backups, and secret injection from the deployment platform.

## Production readiness (Phase 11)

The backend and frontend use multi-stage images and run as non-root users. Compose starts PostgreSQL, waits for database and API health, stores database and PDF data in separate durable volumes, exposes only the frontend, applies read-only filesystems to application containers, drops Linux capabilities, and prevents privilege escalation. Nginx adds browser security headers, immutable caching for hashed assets, no-store handling for the SPA shell, and same-origin API proxying.

The initial administrator password is now checked against the same 12-to-128-character upper/lower/digit/symbol policy as API-created users. The final security review and remaining operational risks are recorded in [docs/SECURITY.md](docs/SECURITY.md). Deployment, backup, restore, health, logs, upgrade, and rollback procedures are in [docs/OPERATIONS.md](docs/OPERATIONS.md).
