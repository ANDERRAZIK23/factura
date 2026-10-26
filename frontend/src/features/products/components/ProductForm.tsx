import { zodResolver } from "@hookform/resolvers/zod";
import axios from "axios";
import { useEffect, useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import {
  productSchema,
  type ProductFormValues,
} from "../schemas/productSchema";
import {
  PRODUCT_UNITS,
  type ProblemDetails,
  type Product,
  type ProductInput,
} from "../types/product";
import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import "../../../components/ui/editor.css";
import { formatMoney, formatProductUnit } from "../../../utils/format";

const emptyValues: ProductFormValues = {
  productName: "",
  description: "",
  unitPriceExcludingTax: "",
  defaultVatRate: "",
  unit: "PIECE",
};

export function ProductForm({
  product,
  onSubmit,
  onReload,
}: {
  product?: Product;
  onSubmit: (input: ProductInput) => Promise<void>;
  onReload?: () => Promise<void>;
}) {
  const [formError, setFormError] = useState("");
  const [stale, setStale] = useState(false);
  const submissionInFlight = useRef(false);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: emptyValues,
  });
  useEffect(() => {
    if (product)
      reset({
        productName: product.productName,
        description: product.description ?? "",
        unitPriceExcludingTax: product.unitPriceExcludingTax.toFixed(2),
        defaultVatRate: product.defaultVatRate.toFixed(2),
        unit: product.unit,
      });
  }, [product, reset]);
  const [price, vat, name, unit] = useWatch({
    control,
    name: ["unitPriceExcludingTax", "defaultVatRate", "productName", "unit"],
  });
  const numericPrice = Number(price);
  const numericVat = Number(vat);
  const validPrice =
    productSchema.shape.unitPriceExcludingTax.safeParse(price).success;
  const validVat = productSchema.shape.defaultVatRate.safeParse(vat).success;
  const preview =
    validPrice && validVat
      ? formatMoney(numericPrice * (1 + numericVat / 100))
      : "—";

  async function submit(values: ProductFormValues) {
    if (submissionInFlight.current) return;
    submissionInFlight.current = true;
    setFormError("");
    setStale(false);
    try {
      await onSubmit({
        productName: values.productName.trim(),
        description: values.description.trim() || undefined,
        unitPriceExcludingTax: Number(values.unitPriceExcludingTax),
        defaultVatRate: Number(values.defaultVatRate),
        unit: values.unit,
      });
    } catch (error) {
      if (!axios.isAxiosError<ProblemDetails>(error)) {
        setFormError(
          "Impossible d’enregistrer le produit. Veuillez réessayer.",
        );
        return;
      }
      const problem = error.response?.data;
      const fields = problem?.fieldErrors ?? problem?.errors;
      if (fields)
        Object.entries(fields).forEach(([field, message]) => {
          if (field in emptyValues)
            setError(field as keyof ProductFormValues, {
              type: "server",
              message,
            });
        });
      if (problem?.type?.endsWith("/product-concurrency")) {
        setStale(true);
        setFormError(
          problem.detail ??
            "Ce produit a été modifié par un autre utilisateur.",
        );
      } else if (!fields)
        setFormError(
          problem?.detail ??
            "Impossible d’enregistrer le produit. Veuillez réessayer.",
        );
    } finally {
      submissionInFlight.current = false;
    }
  }
  async function reload() {
    if (!onReload) return;
    await onReload();
    setStale(false);
    setFormError("");
  }
  return (
    <form
      className="product-form product-editor"
      onSubmit={handleSubmit(submit)}
      noValidate
      aria-busy={isSubmitting}
    >
      <div className="product-editor-layout">
        <fieldset className="editor-fields" disabled={isSubmitting}>
          <legend className="sr-only">Informations du produit</legend>
          <section
            className="editor-section"
            aria-labelledby="product-identity-title"
          >
            <header className="editor-section-header">
              <span className="editor-section-icon">
                <Icon name="product" size={20} />
              </span>
              <div>
                <h2 id="product-identity-title">Produit ou service</h2>
              </div>
            </header>
            <Field
              id="productName"
              label="Nom du produit"
              required
              error={errors.productName?.message}
            >
              <input
                id="productName"
                data-testid="product-name"
                autoFocus
                maxLength={200}
                placeholder="Ex. Rouleau de papier, prestation de conseil…"
                aria-required="true"
                aria-invalid={Boolean(errors.productName)}
                aria-describedby={
                  errors.productName ? "productName-error" : undefined
                }
                {...register("productName")}
              />
            </Field>
            <Field
              id="description"
              label="Description"
              error={errors.description?.message}
            >
              <textarea
                id="description"
                rows={4}
                maxLength={1000}
                placeholder="Caractéristiques, contenu ou détails de la prestation"
                aria-invalid={Boolean(errors.description)}
                aria-describedby={
                  errors.description ? "description-error" : "description-hint"
                }
                {...register("description")}
              />
              <small id="description-hint" className="editor-field-hint">
                Facultatif · 1 000 caractères maximum.
              </small>
            </Field>
            {product && (
              <div className="editor-generated-code">
                <span>Code produit</span>
                <strong>{product.productCode}</strong>
              </div>
            )}
          </section>
          <section
            className="editor-section"
            aria-labelledby="product-price-title"
          >
            <header className="editor-section-header">
              <span className="editor-section-icon">
                <Icon name="invoice" size={20} />
              </span>
              <div>
                <h2 id="product-price-title">Tarification et unité</h2>
              </div>
            </header>
            <div className="editor-field-grid">
              <Field
                id="unitPriceExcludingTax"
                label="Prix unitaire HT"
                required
                error={errors.unitPriceExcludingTax?.message}
              >
                <div className="editor-input-affix">
                  <input
                    id="unitPriceExcludingTax"
                    type="number"
                    min="0"
                    step="any"
                    inputMode="decimal"
                    placeholder="0,00"
                    aria-required="true"
                    aria-invalid={Boolean(errors.unitPriceExcludingTax)}
                    aria-describedby={
                      errors.unitPriceExcludingTax
                        ? "unitPriceExcludingTax-error"
                        : undefined
                    }
                    {...register("unitPriceExcludingTax")}
                  />
                  <span>MAD</span>
                </div>
              </Field>
              <Field
                id="defaultVatRate"
                label="Taux de TVA par défaut (%)"
                required
                error={errors.defaultVatRate?.message}
              >
                <div className="editor-input-affix">
                  <input
                    id="defaultVatRate"
                    type="number"
                    min="0"
                    max="100"
                    step="any"
                    inputMode="decimal"
                    placeholder="20"
                    aria-required="true"
                    aria-invalid={Boolean(errors.defaultVatRate)}
                    aria-describedby={
                      errors.defaultVatRate ? "defaultVatRate-error" : undefined
                    }
                    {...register("defaultVatRate")}
                  />
                  <span>%</span>
                </div>
              </Field>
              <Field
                id="unit"
                label="Unité"
                required
                error={errors.unit?.message}
              >
                <select
                  id="unit"
                  aria-required="true"
                  aria-invalid={Boolean(errors.unit)}
                  aria-describedby={
                    errors.unit ? "unit-error" : "product-unit-hint"
                  }
                  {...register("unit")}
                >
                  {PRODUCT_UNITS.map((value) => (
                    <option key={value} value={value}>
                      {formatProductUnit(value)}
                    </option>
                  ))}
                </select>
                <small id="product-unit-hint" className="editor-field-hint">
                  Choisissez « Service » pour une prestation.
                </small>
              </Field>
            </div>
          </section>
        </fieldset>
        <aside
          className="product-editor-preview"
          aria-label="Aperçu du produit"
        >
          <p className="editor-kicker">Aperçu du catalogue</p>
          <span className="client-preview-avatar">
            <Icon name="product" size={26} />
          </span>
          <h2>{name.trim() || "Votre nouvel article"}</h2>
          <p className="editor-field-hint">
            Prix pour une unité · {formatProductUnit(unit)}
          </p>
          <dl className="product-preview-prices">
            <div>
              <dt>Prix unitaire HT</dt>
              <dd>{validPrice ? formatMoney(numericPrice) : "—"}</dd>
            </div>
            <div>
              <dt>TVA</dt>
              <dd>
                {validVat ? `${numericVat.toLocaleString("fr-FR")} %` : "—"}
              </dd>
            </div>
          </dl>
          <div
            className="product-preview-total"
            aria-live="polite"
            aria-atomic="true"
          >
            <span>Prix unitaire TTC</span>
            <strong data-testid="ttc-preview">{preview}</strong>
          </div>
        </aside>
      </div>
      {formError && (
        <div role="alert" className="form-error">
          {formError}
          {stale && onReload && (
            <button className="link-button" type="button" onClick={reload}>
              Recharger les dernières données
            </button>
          )}
        </div>
      )}
      <footer className="editor-footer">
        <div className="editor-footer-actions">
          <Link
            className="button-link secondary-link"
            aria-disabled={isSubmitting || undefined}
            tabIndex={isSubmitting ? -1 : undefined}
            onClick={(event) => {
              if (isSubmitting) event.preventDefault();
            }}
            to={product ? `/products/${product.id}` : "/products"}
          >
            Annuler
          </Link>
          <button
            data-testid="product-submit"
            type="submit"
            disabled={isSubmitting}
          >
            <Icon name={product ? "edit" : "plus"} size={16} />
            {isSubmitting
              ? "Enregistrement…"
              : product
                ? "Enregistrer les modifications"
                : "Créer le produit"}
          </button>
        </div>
      </footer>
    </form>
  );
}

function Field({
  id,
  label,
  error,
  required,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children}
      {error && (
        <p className="field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
