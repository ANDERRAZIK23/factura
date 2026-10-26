import axios from "axios";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  useFieldArray,
  useForm,
  useWatch,
  type FieldErrors,
} from "react-hook-form";
import { Link } from "react-router-dom";
import { useClient, useClients } from "../../clients/hooks/useClients";
import type { Client, ClientSummary } from "../../clients/types/client";
import { useProducts } from "../../products/hooks/useProducts";
import type { ProductSummary, ProductUnit } from "../../products/types/product";
import {
  invoiceCreateSchema,
  type InvoiceCreateFormValues,
} from "../schemas/invoiceCreateSchema";
import {
  PAYMENT_METHODS,
  type Invoice,
  type InvoiceInput,
  type ProblemDetails,
} from "../types/invoice";
import {
  calculateInvoiceCreatePreview,
  type InvoiceCreateLinePreview,
} from "../utils/invoiceCreatePreview";
import {
  formatMoney,
  formatPaymentMethod,
  formatProductUnit,
} from "../../../utils/format";
import { ProductCombobox } from "./ProductCombobox";
import { Icon } from "../../../components/ui/Icon";
import { InvoiceClientDialog } from "./InvoiceClientDialog";
import "../../../components/ui/editor.css";

const today = () => new Date().toLocaleDateString("en-CA");
const money = (value: number) => value.toFixed(2);
const VAT_RATES = ["0.00", "7.00", "10.00", "14.00", "20.00"] as const;
const QUANTITY_STEPS: Record<ProductUnit, number> = {
  PIECE: 1,
  ROLL: 1,
  SERVICE: 1,
  KG: 0.001,
  METER: 0.001,
  LITER: 0.001,
  OTHER: 0.001,
};
const emptyValues: InvoiceCreateFormValues = {
  invoiceDate: today(),
  clientId: "",
  paymentMethod: "CASH",
  customerOrderReference: "",
  vatRate: "20.00",
  lines: [],
};

function valuesFor(invoice: Invoice): InvoiceCreateFormValues {
  return {
    invoiceDate: invoice.invoiceDate,
    clientId: invoice.client.id,
    paymentMethod:
      invoice.paymentMethod === "CHECK" ||
      invoice.paymentMethod === "BANK_TRANSFER"
        ? invoice.paymentMethod
        : "CASH",
    customerOrderReference: invoice.customerOrderReference ?? "",
    vatRate: (invoice.lines[0]?.vatRate ?? 20).toFixed(2),
    lines: invoice.lines.map((line) => ({
      productId: line.productId,
      quantity: String(line.quantity),
      unitPriceExcludingTax: line.unitPriceExcludingTax.toFixed(2),
      discountRate: line.discountRate.toFixed(2),
    })),
  };
}

export function InvoiceCreateForm({
  invoice,
  onSubmit,
  onReload,
  documentKind = "invoice",
  cancelTo,
  submitDisabled = false,
}: {
  invoice?: Invoice;
  onSubmit: (input: InvoiceInput) => Promise<void>;
  onReload?: () => Promise<void>;
  documentKind?: "invoice" | "quotation";
  cancelTo?: string;
  submitDisabled?: boolean;
}) {
  const isQuotation = documentKind === "quotation";
  const [formError, setFormError] = useState("");
  const [stale, setStale] = useState(false);
  const [clientSearch, setClientSearch] = useState("");
  const [clientQuery, setClientQuery] = useState("");
  const [creatingClient, setCreatingClient] = useState(false);
  const [createdClient, setCreatedClient] = useState<Client | null>(null);
  const clientTrigger = useRef<HTMLButtonElement>(null);
  const [pendingRemoval, setPendingRemoval] = useState<number | null>(null);
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const submissionInFlight = useRef(false);
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<InvoiceCreateFormValues>({
    resolver: zodResolver(invoiceCreateSchema),
    defaultValues: invoice
      ? valuesFor(invoice)
      : { ...emptyValues, invoiceDate: today() },
  });
  const lineArray = useFieldArray({ control, name: "lines" });
  const clientId = useWatch({ control, name: "clientId" });
  const vatRate = useWatch({ control, name: "vatRate" });
  const watchedLines = useWatch({ control, name: "lines" });
  const lines = useMemo(() => watchedLines ?? [], [watchedLines]);
  const clients = useClients({
    page: 0,
    size: 100,
    sort: "clientName,asc",
    active: true,
    search: clientQuery || undefined,
  });
  const products = useProducts({
    page: 0,
    size: 100,
    sort: "productName,asc",
    active: true,
  });
  const clientDetails = useClient(clientId);
  const preview = useMemo(
    () => calculateInvoiceCreatePreview(lines, vatRate),
    [lines, vatRate],
  );
  const allClients = useMemo(() => {
    const loaded: ClientSummary[] = [...(clients.data?.content ?? [])];
    for (const candidate of [clientDetails.data, createdClient]) {
      if (candidate && !loaded.some((client) => client.id === candidate.id))
        loaded.unshift(candidate);
    }
    if (invoice && !loaded.some((client) => client.id === invoice.client.id))
      loaded.unshift({
        ...invoice.client,
        email: null,
        createdAt: "",
        updatedAt: "",
        version: 0,
      });
    return loaded;
  }, [clients.data, clientDetails.data, createdClient, invoice]);
  const allProducts = useMemo(
    () => products.data?.content ?? [],
    [products.data],
  );
  const selectedClientSummary = allClients.find(
    (client) => client.id === clientId,
  );
  const selectedClient =
    clientDetails.data ??
    (createdClient?.id === clientId ? createdClient : selectedClientSummary);
  const loadingOptions = clients.isPending || products.isPending;

  const vatOptions = useMemo(() => {
    const current = (invoice?.lines[0]?.vatRate ?? 20).toFixed(2);
    return VAT_RATES.includes(current as (typeof VAT_RATES)[number])
      ? [...VAT_RATES]
      : [current, ...VAT_RATES];
  }, [invoice]);

  useEffect(() => {
    if (invoice) reset(valuesFor(invoice));
  }, [invoice, reset]);

  const clientOptions = allClients;

  useEffect(() => {
    const timer = window.setTimeout(
      () => setClientQuery(clientSearch.trim()),
      250,
    );
    return () => window.clearTimeout(timer);
  }, [clientSearch]);

  function appendLine() {
    if (isSubmitting) return;
    lineArray.append({
      productId: "",
      quantity: "1",
      unitPriceExcludingTax: "",
      discountRate: "0.00",
    });
    setPendingRemoval(null);
  }

  // "Mode Excel": Entrée sur le dernier champ de la dernière ligne → ajouter une ligne
  function handleEnterAppend(index: number, event: React.KeyboardEvent) {
    if (event.key !== "Enter") return;
    if (index !== lineArray.fields.length - 1) return;
    event.preventDefault();
    lineArray.append({
      productId: "",
      quantity: "1",
      unitPriceExcludingTax: "",
      discountRate: "0.00",
    });
    setPendingRemoval(null);
    // Focus the new product combobox after React re-renders
    setTimeout(() => {
      const next = document.getElementById(`invoice-product-${index + 1}`);
      next?.focus();
    }, 50);
  }

  function handleDragStart(index: number, e: React.DragEvent) {
    setDraggedIndex(index);
    e.dataTransfer.effectAllowed = "move";
  }
  function handleDragOver(index: number, e: React.DragEvent) {
    e.preventDefault();
    if (draggedIndex === null || draggedIndex === index) return;
    lineArray.move(draggedIndex, index);
    setDraggedIndex(index);
  }
  function handleDragEnd() {
    setDraggedIndex(null);
  }

  function chooseProduct(
    index: number,
    productId: string,
    selected?: ProductSummary,
  ) {
    setValue(`lines.${index}.productId`, productId, {
      shouldDirty: true,
      shouldValidate: true,
    });
    const product =
      selected ?? allProducts.find((candidate) => candidate.id === productId);
    if (product) {
      setValue(
        `lines.${index}.unitPriceExcludingTax`,
        money(product.unitPriceExcludingTax),
        { shouldDirty: true, shouldValidate: true },
      );
      setValue(`lines.${index}.discountRate`, "0.00", {
        shouldDirty: true,
        shouldValidate: true,
      });
    }
  }

  function requestRemoval(index: number) {
    setPendingRemoval(index);
  }

  function confirmRemoval(index: number) {
    lineArray.remove(index);
    setPendingRemoval(null);
  }

  async function submit(values: InvoiceCreateFormValues) {
    if (submissionInFlight.current) return;
    submissionInFlight.current = true;
    setFormError("");
    setStale(false);
    const input: InvoiceInput = {
      invoiceDate: values.invoiceDate,
      clientId: values.clientId,
      paymentMethod: values.paymentMethod,
      customerOrderReference: values.customerOrderReference.trim() || undefined,
      lines: values.lines.map((line) => ({
        productId: line.productId,
        quantity: Number(line.quantity),
        unitPriceExcludingTax: Number(line.unitPriceExcludingTax),
        discountRate: Number(line.discountRate),
        vatRate: Number(values.vatRate),
      })),
    };

    try {
      await onSubmit(input);
    } catch (caught) {
      if (!axios.isAxiosError<ProblemDetails>(caught)) {
        setFormError(
          isQuotation
            ? "Impossible d’enregistrer le devis. Veuillez réessayer."
            : "Impossible d’enregistrer la facture. Veuillez réessayer.",
        );
        return;
      }
      const problem = caught.response?.data;
      const fields = problem?.fieldErrors ?? problem?.errors;
      if (fields) {
        Object.entries(fields).forEach(([field, message]) =>
          setError(field as keyof InvoiceCreateFormValues, {
            type: "server",
            message,
          }),
        );
      } else if (
        problem?.type?.endsWith("/invoice-concurrency") ||
        caught.response?.status === 409
      ) {
        setStale(true);
        setFormError(
          problem?.detail ??
            (isQuotation
              ? "Un autre utilisateur a modifié ce devis."
              : "Un autre utilisateur a modifié cette facture."),
        );
      } else {
        setFormError(
          problem?.detail ??
            (isQuotation
              ? "Impossible d’enregistrer le devis."
              : "Impossible d’enregistrer la facture."),
        );
      }
    } finally {
      submissionInFlight.current = false;
    }
  }

  const lineError =
    typeof errors.lines?.message === "string"
      ? errors.lines.message
      : undefined;

  async function reload() {
    if (!onReload) return;
    await onReload();
    setStale(false);
    setFormError("");
  }

  return (
    <>
      <form
        className="invoice-form invoice-create-form invoice-editor"
        data-testid="invoice-form"
        onSubmit={handleSubmit(submit)}
        noValidate
        aria-busy={isSubmitting}
      >
        <div className="editor-intro-note">
          <span className="status-badge invoice-draft">
            {isQuotation ? "Devis en préparation" : "Brouillon"}
          </span>

          <small>* Champs obligatoires</small>
        </div>
        <div className="invoice-form-layout">
          <fieldset
            className="invoice-form-main editor-fields"
            disabled={isSubmitting}
          >
            <legend className="sr-only">
              {isQuotation
                ? "Préparation du devis"
                : "Préparation de la facture"}
            </legend>
            <section
              className="form-section invoice-general-section"
              aria-labelledby="invoice-general-title"
            >
              <header className="editor-section-header">
                <span className="editor-step">01</span>
                <div>
                  <h2 id="invoice-general-title">Client et informations</h2>
                  <p>
                    {isQuotation
                      ? "Définissez le destinataire et les conditions de votre proposition."
                      : "Définissez le destinataire et les conditions de facturation."}
                  </p>
                </div>
              </header>
              <div className="invoice-client-block">
                <div className="invoice-create-client-tools">
                  <Field id="invoiceClientSearch" label="Rechercher un client">
                    <input
                      id="invoiceClientSearch"
                      type="search"
                      aria-label="Rechercher parmi les clients actifs"
                      value={clientSearch}
                      onChange={(event) => setClientSearch(event.target.value)}
                      placeholder="Nom, code ou ICE…"
                    />
                  </Field>
                  <Field
                    id="invoiceClient"
                    label="Client"
                    required
                    error={errors.clientId?.message}
                  >
                    <select
                      id="invoiceClient"
                      value={clientId}
                      aria-required="true"
                      aria-invalid={Boolean(errors.clientId)}
                      aria-describedby={
                        errors.clientId ? "invoiceClient-error" : undefined
                      }
                      {...register("clientId")}
                    >
                      <option value="">Sélectionner un client</option>
                      {clientOptions.map((client) => (
                        <option key={client.id} value={client.id}>
                          {client.clientName}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <button
                    ref={clientTrigger}
                    type="button"
                    className="secondary invoice-create-new-client"
                    onClick={() => setCreatingClient(true)}
                  >
                    <Icon name="plus" size={16} />
                    Nouveau client
                  </button>
                </div>
                {clients.isFetching && (
                  <p className="invoice-create-inline-state" role="status">
                    Recherche des clients…
                  </p>
                )}
                {clients.data &&
                  clients.data.totalElements > clients.data.content.length && (
                    <p className="editor-field-hint">
                      Affichage limité à {clients.data.content.length} clients.
                      Précisez votre recherche pour retrouver un autre client.
                    </p>
                  )}
                {clientId && clientDetails.isPending && (
                  <p className="invoice-create-inline-state" role="status">
                    Chargement des coordonnées du client…
                  </p>
                )}
                {clientId && clientDetails.isError && (
                  <p role="alert" className="field-error">
                    Coordonnées complètes indisponibles.{" "}
                    <button
                      type="button"
                      className="link-button"
                      onClick={() => void clientDetails.refetch()}
                    >
                      Réessayer
                    </button>
                  </p>
                )}
                {selectedClient ? (
                  <ClientDetails client={selectedClient} />
                ) : (
                  <div className="invoice-client-placeholder">
                    <Icon name="building" size={22} />
                    <span>
                      Les coordonnées du client sélectionné s’afficheront ici.
                    </span>
                  </div>
                )}
              </div>
              <div className="invoice-basics">
                <Field
                  id="invoiceDate"
                  label={
                    documentKind === "quotation"
                      ? "Date du devis"
                      : "Date de facture"
                  }
                  required
                  error={errors.invoiceDate?.message}
                >
                  <input
                    id="invoiceDate"
                    type="date"
                    aria-required="true"
                    aria-invalid={Boolean(errors.invoiceDate)}
                    aria-describedby={
                      errors.invoiceDate ? "invoiceDate-error" : undefined
                    }
                    {...register("invoiceDate")}
                  />
                </Field>
                <Field
                  id="invoicePayment"
                  label="Mode de paiement"
                  required
                  error={errors.paymentMethod?.message}
                >
                  <select
                    id="invoicePayment"
                    aria-required="true"
                    aria-invalid={Boolean(errors.paymentMethod)}
                    aria-describedby={
                      errors.paymentMethod ? "invoicePayment-error" : undefined
                    }
                    {...register("paymentMethod")}
                  >
                    {PAYMENT_METHODS.map((method) => (
                      <option key={method} value={method}>
                        {formatPaymentMethod(method)}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field
                  id="customerOrderReference"
                  label="Référence client"
                  error={errors.customerOrderReference?.message}
                >
                  <input
                    id="customerOrderReference"
                    maxLength={100}
                    aria-invalid={Boolean(errors.customerOrderReference)}
                    aria-describedby={
                      errors.customerOrderReference
                        ? "customerOrderReference-error"
                        : undefined
                    }
                    placeholder="Bon de commande (facultatif)"
                    {...register("customerOrderReference")}
                  />
                </Field>
              </div>
            </section>
            <section
              className="form-section invoice-lines"
              aria-labelledby="invoice-lines-title"
            >
              <header className="editor-section-header invoice-lines-heading">
                <span className="editor-step">02</span>
                <div>
                  <h2 id="invoice-lines-title">
                    Produits et services{" "}
                    <span className="editor-count">
                      {lineArray.fields.length}
                    </span>
                  </h2>
                  <p>
                    Choisissez vos articles et ajustez les quantités, prix et
                    remises.
                  </p>
                </div>
                <button
                  type="button"
                  className="secondary add-line"
                  data-testid="add-invoice-line"
                  disabled={products.isPending || products.isError}
                  onClick={appendLine}
                >
                  <Icon name="plus" size={16} />
                  Ajouter une ligne
                </button>
              </header>
              {lineArray.fields.length === 0 ? (
                <div className="invoice-lines-empty">
                  <span className="invoice-empty-icon">
                    <Icon name="product" size={26} />
                  </span>
                  <h3>Ajoutez votre premier article</h3>
                  <p>
                    Les prix du catalogue sont repris automatiquement et restent
                    modifiables.
                  </p>
                  <button
                    type="button"
                    className="secondary"
                    onClick={appendLine}
                    disabled={products.isPending || products.isError}
                  >
                    <Icon name="plus" size={16} />
                    Choisir un produit
                  </button>
                </div>
              ) : (
                <div className="invoice-create-lines-table">
                  <table>
                    <caption className="sr-only">
                      {isQuotation
                        ? "Lignes du devis en préparation"
                        : "Lignes de la facture en préparation"}
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">N°</th>
                        <th scope="col">Produit / service</th>
                        <th scope="col" className="numeric-cell">
                          Quantité
                        </th>
                        <th scope="col" className="numeric-cell">
                          Prix unit. HT
                        </th>
                        <th scope="col" className="numeric-cell">
                          Remise
                        </th>
                        <th scope="col" className="numeric-cell">
                          Total HT
                        </th>
                        <th scope="col">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {lineArray.fields.map((field, index) => (
                        <InvoiceCreateLine
                          key={field.id}
                          index={index}
                          products={allProducts}
                          storedProduct={invoice?.lines.find(
                            (line) =>
                              line.productId === lines[index]?.productId,
                          )}
                          errors={errors}
                          register={register}
                          value={lines[index]}
                          preview={preview.lines[index]}
                          pendingRemoval={pendingRemoval === index}
                          disabled={isSubmitting}
                          onProduct={(id, product) =>
                            chooseProduct(index, id, product)
                          }
                          onRequestRemoval={() => requestRemoval(index)}
                          onConfirmRemoval={() => confirmRemoval(index)}
                          onCancelRemoval={() => setPendingRemoval(null)}
                          onDragStart={(event) => handleDragStart(index, event)}
                          onDragOver={(event) => handleDragOver(index, event)}
                          onDragEnd={handleDragEnd}
                          onEnterAppend={(event) =>
                            handleEnterAppend(index, event)
                          }
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {lineArray.fields.length > 0 && (
                <p className="editor-field-hint">
                  Montants en MAD · Entrée dans le dernier champ de remise
                  ajoute une ligne.
                </p>
              )}
              {lineError && (
                <p role="alert" className="field-error">
                  {lineError}
                </p>
              )}
            </section>
            {(clients.isError || products.isError) && (
              <div role="alert" className="form-error">
                Impossible de charger{" "}
                {clients.isError ? "les clients" : "le catalogue produits"}.
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    if (clients.isError) void clients.refetch();
                    if (products.isError) void products.refetch();
                  }}
                >
                  Recharger les données
                </button>
              </div>
            )}
            {formError && (
              <div role="alert" className="form-error">
                {formError}
                {stale && onReload && (
                  <button
                    className="link-button"
                    type="button"
                    onClick={reload}
                  >
                    Recharger la facture actuelle
                  </button>
                )}
              </div>
            )}
          </fieldset>
          <aside
            className="invoice-compose-summary"
            aria-labelledby="invoice-summary-title"
          >
            <header className="editor-summary-header">
              <span className="editor-kicker">Avant enregistrement</span>
              <h2 id="invoice-summary-title">Récapitulatif</h2>
              <span className="editor-summary-currency">MAD</span>
            </header>
            <div className="editor-summary-client">
              <Icon name="building" size={19} />
              <div>
                <span>{isQuotation ? "Proposé à" : "Facturé à"}</span>
                <strong>
                  {selectedClient?.clientName ?? "Client à sélectionner"}
                </strong>
              </div>
            </div>
            <dl className="editor-totals">
              <div>
                <dt>Sous-total HT</dt>
                <dd>{formatMoney(preview.grossAmountExcludingTax)}</dd>
              </div>
              <div>
                <dt>Remises sur les lignes</dt>
                <dd>
                  {preview.discountAmount > 0 ? "− " : ""}
                  {formatMoney(preview.discountAmount)}
                </dd>
              </div>
              <div className="editor-net-total">
                <dt>Net HT</dt>
                <dd>{formatMoney(preview.netAmountExcludingTax)}</dd>
              </div>
            </dl>
            <div className="editor-vat">
              <Field
                id="invoiceVatRate"
                label="Taux de TVA"
                required
                error={errors.vatRate?.message}
              >
                <select
                  id="invoiceVatRate"
                  disabled={isSubmitting}
                  aria-required="true"
                  aria-invalid={Boolean(errors.vatRate)}
                  aria-describedby={
                    errors.vatRate ? "invoiceVatRate-error" : "invoice-vat-hint"
                  }
                  {...register("vatRate")}
                >
                  {vatOptions.map((rate) => (
                    <option key={rate} value={rate}>
                      {Number(rate).toLocaleString("fr-FR")} %
                    </option>
                  ))}
                </select>
              </Field>
              <p id="invoice-vat-hint">
                Appliqué à toutes les lignes après remise.
              </p>
            </div>
            <dl className="editor-totals">
              <div>
                <dt>Montant TVA</dt>
                <dd>{formatMoney(preview.vatAmount)}</dd>
              </div>
            </dl>
            <div className="editor-grand-total">
              <span>Total TTC estimé</span>
              <strong data-testid="invoice-preview-total">
                {formatMoney(preview.totalIncludingTax)}
              </strong>
              <small>
                {lineArray.fields.length}{" "}
                {lineArray.fields.length === 1 ? "ligne" : "lignes"}
                {isQuotation ? " dans ce devis" : " de facturation"}
              </small>
            </div>
          </aside>
        </div>
        <footer className="editor-footer">
          <div className="editor-footer-actions">
            <Link
              className="button-link secondary-link"
              to={
                cancelTo ?? (invoice ? `/invoices/${invoice.id}` : "/invoices")
              }
            >
              Annuler
            </Link>
            <button
              data-testid="invoice-submit"
              type="submit"
              disabled={isSubmitting || loadingOptions || submitDisabled}
            >
              <Icon name="invoice" size={17} />
              {isSubmitting
                ? "Enregistrement…"
                : invoice
                  ? "Enregistrer les modifications"
                  : documentKind === "quotation"
                    ? "Enregistrer le devis"
                    : "Enregistrer le brouillon"}
            </button>
          </div>
        </footer>
      </form>
      {creatingClient && (
        <InvoiceClientDialog
          onClose={() => {
            setCreatingClient(false);
            clientTrigger.current?.focus();
          }}
          onCreated={(client) => {
            setCreatedClient(client);
            setValue("clientId", client.id, {
              shouldDirty: true,
              shouldValidate: true,
            });
            setClientSearch("");
            setCreatingClient(false);
            clientTrigger.current?.focus();
          }}
        />
      )}
    </>
  );
}

function ClientDetails({
  client,
}: {
  client: ClientSummary & { address?: string | null };
}) {
  return (
    <dl
      className="details-grid invoice-create-client-details"
      aria-label="Coordonnées du client sélectionné"
    >
      <div>
        <dt>Nom / raison sociale</dt>
        <dd>{client.clientName}</dd>
      </div>
      <div>
        <dt>ICE</dt>
        <dd>{client.ice || "—"}</dd>
      </div>
      <div>
        <dt>Téléphone</dt>
        <dd>{client.phoneNumber || "—"}</dd>
      </div>
      <div>
        <dt>E-mail</dt>
        <dd>{client.email || "—"}</dd>
      </div>
      <div className="invoice-create-address">
        <dt>Adresse</dt>
        <dd>{client.address || "—"}</dd>
      </div>
    </dl>
  );
}

function InvoiceCreateLine({
  index,
  products,
  storedProduct,
  errors,
  register,
  value,
  preview,
  pendingRemoval,
  onProduct,
  onRequestRemoval,
  onConfirmRemoval,
  onCancelRemoval,
  onDragStart,
  onDragOver,
  onDragEnd,
  onEnterAppend,
  disabled,
}: {
  index: number;
  products: ProductSummary[];
  storedProduct?: Invoice["lines"][number];
  errors: FieldErrors<InvoiceCreateFormValues>;
  register: ReturnType<typeof useForm<InvoiceCreateFormValues>>["register"];
  value?: InvoiceCreateFormValues["lines"][number];
  preview?: InvoiceCreateLinePreview;
  pendingRemoval: boolean;
  disabled: boolean;
  onProduct: (id: string, product?: ProductSummary) => void;
  onRequestRemoval: () => void;
  onConfirmRemoval: () => void;
  onCancelRemoval: () => void;
  onDragStart?: (e: React.DragEvent) => void;
  onDragOver?: (e: React.DragEvent) => void;
  onDragEnd?: () => void;
  onEnterAppend?: (e: React.KeyboardEvent) => void;
}) {
  const [chosenProduct, setChosenProduct] = useState<ProductSummary>();
  const fieldErrors = errors.lines?.[index];
  const selectedProduct =
    chosenProduct?.id === value?.productId
      ? chosenProduct
      : (products.find((product) => product.id === value?.productId) ??
        storedProduct);
  const quantityStep = selectedProduct
    ? QUANTITY_STEPS[selectedProduct.unit]
    : 1;
  return (
    <tr
      data-testid={`invoice-line-${index}`}
      onDragOver={onDragOver}
      onDragEnd={onDragEnd}
    >
      <td data-label="Ligne">
        <span
          className="editor-line-number"
          draggable={!disabled}
          onDragStart={onDragStart}
          title="Glisser pour réorganiser"
        >
          {index + 1}
        </span>
      </td>
      <td data-label="Produit / service">
        <ProductCombobox
          id={`invoice-product-${index}`}
          label={`Produit ${index + 1}`}
          storedLabel={
            storedProduct
              ? `${storedProduct.productCode} — ${storedProduct.productName}`
              : undefined
          }
          disabled={disabled}
          products={products}
          value={value?.productId ?? ""}
          onChange={(id, product) => {
            setChosenProduct(product);
            onProduct(id, product);
          }}
          error={Boolean(fieldErrors?.productId)}
        />
        {selectedProduct && (
          <small className="invoice-create-product-meta">
            {selectedProduct.productCode} ·{" "}
            {formatProductUnit(selectedProduct.unit)}
          </small>
        )}
        {fieldErrors?.productId?.message && (
          <p
            id={`invoice-product-${index}-error`}
            role="alert"
            className="field-error"
          >
            {fieldErrors.productId.message}
          </p>
        )}
      </td>
      <td data-label="Quantité" className="invoice-create-numeric-input">
        <input
          id={`invoice-quantity-${index}`}
          aria-label={`Quantité ${index + 1}`}
          aria-invalid={Boolean(fieldErrors?.quantity)}
          aria-describedby={
            fieldErrors?.quantity
              ? `invoice-quantity-${index}-error`
              : undefined
          }
          type="number"
          min={quantityStep}
          step={quantityStep}
          {...register(`lines.${index}.quantity`)}
        />
        {fieldErrors?.quantity?.message && (
          <p
            id={`invoice-quantity-${index}-error`}
            role="alert"
            className="field-error"
          >
            {fieldErrors.quantity.message}
          </p>
        )}
      </td>
      <td
        data-label="Prix unitaire HT"
        className="invoice-create-numeric-input"
      >
        <input
          id={`invoice-price-${index}`}
          aria-label={`Prix unitaire ${index + 1}`}
          aria-invalid={Boolean(fieldErrors?.unitPriceExcludingTax)}
          aria-describedby={
            fieldErrors?.unitPriceExcludingTax
              ? `invoice-price-${index}-error`
              : undefined
          }
          type="number"
          min="0"
          step="0.01"
          {...register(`lines.${index}.unitPriceExcludingTax`)}
        />
        {fieldErrors?.unitPriceExcludingTax?.message && (
          <p
            id={`invoice-price-${index}-error`}
            role="alert"
            className="field-error"
          >
            {fieldErrors.unitPriceExcludingTax.message}
          </p>
        )}
      </td>
      <td data-label="Remise" className="invoice-create-numeric-input">
        <div className="invoice-create-rate-input">
          <input
            id={`invoice-discount-${index}`}
            aria-label={`Taux de remise ${index + 1}`}
            aria-invalid={Boolean(fieldErrors?.discountRate)}
            aria-describedby={
              fieldErrors?.discountRate
                ? `invoice-discount-${index}-error`
                : undefined
            }
            type="number"
            min="0"
            max="100"
            step="0.01"
            onKeyDown={onEnterAppend}
            {...register(`lines.${index}.discountRate`)}
          />
          <span>%</span>
        </div>
        {fieldErrors?.discountRate?.message && (
          <p
            id={`invoice-discount-${index}-error`}
            role="alert"
            className="field-error"
          >
            {fieldErrors.discountRate.message}
          </p>
        )}
      </td>
      <td
        data-label="Total HT"
        className="invoice-create-line-total"
        data-testid={`invoice-line-total-${index}`}
      >
        <strong>{formatMoney(preview?.netAmountExcludingTax ?? 0)}</strong>
        {(preview?.discountAmount ?? 0) > 0 && (
          <small>− {formatMoney(preview?.discountAmount ?? 0)} de remise</small>
        )}
      </td>
      <td data-label="Actions">
        <div className="invoice-create-line-actions">
          {pendingRemoval ? (
            <>
              <button
                type="button"
                className="danger compact-button"
                onClick={onConfirmRemoval}
              >
                Confirmer
              </button>
              <button
                type="button"
                className="link-button"
                onClick={onCancelRemoval}
              >
                Annuler
              </button>
            </>
          ) : (
            <button
              type="button"
              className="danger invoice-line-remove"
              aria-label={`Supprimer la ligne ${index + 1}`}
              title="Supprimer la ligne"
              onClick={onRequestRemoval}
            >
              <Icon name="trash" size={16} />
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}

function Field({
  id,
  label,
  required,
  error,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && " *"}
      </label>
      {children}
      {error && (
        <p id={`${id}-error`} role="alert" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
