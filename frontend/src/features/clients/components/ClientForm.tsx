import { zodResolver } from "@hookform/resolvers/zod";
import axios from "axios";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import "../../../components/ui/editor.css";
import { useForm, useWatch } from "react-hook-form";
import { clientSchema, type ClientFormValues } from "../schemas/clientSchema";
import type { Client, ClientInput, ProblemDetails } from "../types/client";

const emptyValues: ClientFormValues = {
  clientName: "",
  phoneNumber: "",
  address: "",
  email: "",
  ice: "",
};

export function ClientForm({
  client,
  onSubmit,
  onReload,
  onCancel,
}: {
  client?: Client;
  onSubmit: (input: ClientInput) => Promise<void>;
  onReload?: () => Promise<void>;
  onCancel?: () => void;
}) {
  const [formError, setFormError] = useState("");
  const [stale, setStale] = useState(false);
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ClientFormValues>({
    resolver: zodResolver(clientSchema),
    defaultValues: emptyValues,
  });

  const name = useWatch({ control, name: "clientName" });
  const email = useWatch({ control, name: "email" });
  const ice = useWatch({ control, name: "ice" });

  useEffect(() => {
    if (client)
      reset({
        clientName: client.clientName,
        phoneNumber: client.phoneNumber ?? "",
        address: client.address ?? "",
        email: client.email ?? "",
        ice: client.ice ?? "",
      });
  }, [client, reset]);

  async function submit(values: ClientFormValues) {
    setFormError("");
    setStale(false);
    const optional = (value: string) => value.trim() || undefined;
    try {
      await onSubmit({
        clientName: values.clientName.trim(),
        phoneNumber: optional(values.phoneNumber),
        address: optional(values.address),
        email: optional(values.email)?.toLowerCase(),
        ice: optional(values.ice)?.replace(/\s/g, ""),
      });
    } catch (error) {
      if (!axios.isAxiosError<ProblemDetails>(error)) {
        setFormError("Impossible d’enregistrer le client. Veuillez réessayer.");
        return;
      }
      const problem = error.response?.data;
      const fields = problem?.fieldErrors ?? problem?.errors;
      if (fields)
        Object.entries(fields).forEach(([field, message]) => {
          if (field in emptyValues)
            setError(field as keyof ClientFormValues, {
              type: "server",
              message,
            });
        });
      if (problem?.type?.endsWith("/duplicate-client-ice"))
        setError(
          "ice",
          {
            type: "server",
            message: problem.detail ?? "Un client avec cet ICE existe déjà",
          },
          { shouldFocus: true },
        );
      else if (
        problem?.type?.endsWith("/client-concurrency") ||
        error.response?.status === 409
      ) {
        setStale(true);
        setFormError(
          problem?.detail ??
            "Ce client a été modifié par un autre utilisateur.",
        );
      } else if (!fields)
        setFormError(
          problem?.detail ??
            "Impossible d’enregistrer le client. Veuillez réessayer.",
        );
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
      className="client-form client-editor"
      onSubmit={handleSubmit(submit)}
      noValidate
      aria-busy={isSubmitting}
    >
      <div className="client-editor-layout">
        <fieldset className="editor-fields" disabled={isSubmitting}>
          <legend className="sr-only">Informations du client</legend>
          <section
            className="editor-section"
            aria-labelledby="client-identity-title"
          >
            <header className="editor-section-header">
              <span className="editor-section-icon">
                <Icon name="building" size={20} />
              </span>
              <div>
                <h2 id="client-identity-title">Identité du client</h2>
                <p>Les informations qui apparaîtront sur vos factures.</p>
              </div>
            </header>
            <div className="editor-field-grid">
              <FormField
                id="clientName"
                label="Nom du client"
                error={errors.clientName?.message}
                required
                className="form-field-wide"
              >
                <input
                  id="clientName"
                  data-testid="client-name"
                  autoFocus
                  autoComplete="organization"
                  placeholder="Nom de l’entreprise ou du particulier"
                  maxLength={200}
                  aria-required="true"
                  aria-invalid={Boolean(errors.clientName)}
                  aria-describedby={
                    errors.clientName ? "clientName-error" : undefined
                  }
                  {...register("clientName")}
                />
              </FormField>
              <FormField id="ice" label="ICE" error={errors.ice?.message}>
                <input
                  id="ice"
                  inputMode="numeric"
                  placeholder="15 chiffres"
                  aria-invalid={Boolean(errors.ice)}
                  aria-describedby={errors.ice ? "ice-error" : "ice-hint"}
                  {...register("ice")}
                />
                <small id="ice-hint" className="editor-field-hint">
                  Identifiant commun de l’entreprise, si disponible.
                </small>
              </FormField>
              {client && (
                <div className="editor-generated-code">
                  <span>Code client</span>
                  <strong>{client.clientCode}</strong>
                </div>
              )}
            </div>
          </section>
          <section
            className="editor-section"
            aria-labelledby="client-contact-title"
          >
            <header className="editor-section-header">
              <span className="editor-section-icon">
                <Icon name="client" size={20} />
              </span>
              <div>
                <h2 id="client-contact-title">Coordonnées de facturation</h2>
                <p>Complétez les coordonnées utiles à votre équipe.</p>
              </div>
            </header>
            <div className="editor-field-grid">
              <FormField
                id="email"
                label="E-mail"
                error={errors.email?.message}
              >
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="contact@entreprise.ma"
                  maxLength={254}
                  aria-invalid={Boolean(errors.email)}
                  aria-describedby={errors.email ? "email-error" : undefined}
                  {...register("email")}
                />
              </FormField>
              <FormField
                id="phoneNumber"
                label="Numéro de téléphone"
                error={errors.phoneNumber?.message}
              >
                <input
                  id="phoneNumber"
                  type="tel"
                  autoComplete="tel"
                  placeholder="+212 5 00 00 00 00"
                  maxLength={30}
                  aria-invalid={Boolean(errors.phoneNumber)}
                  aria-describedby={
                    errors.phoneNumber ? "phoneNumber-error" : undefined
                  }
                  {...register("phoneNumber")}
                />
              </FormField>
              <FormField
                id="address"
                label="Adresse"
                error={errors.address?.message}
                className="form-field-wide"
              >
                <textarea
                  id="address"
                  rows={3}
                  autoComplete="street-address"
                  placeholder="Numéro, rue, ville et code postal"
                  maxLength={500}
                  aria-invalid={Boolean(errors.address)}
                  aria-describedby={
                    errors.address ? "address-error" : undefined
                  }
                  {...register("address")}
                />
              </FormField>
            </div>
          </section>
        </fieldset>
        <aside
          className="client-editor-preview"
          aria-label="Aperçu de la fiche client"
        >
          <p className="editor-kicker">Aperçu de la fiche</p>
          <span className="client-preview-avatar" aria-hidden="true">
            {name.trim().slice(0, 2).toUpperCase() || (
              <Icon name="building" size={26} />
            )}
          </span>
          <h2>{name.trim() || "Votre nouveau client"}</h2>
          <p className="client-preview-email">
            {email.trim() || "Coordonnées à compléter"}
          </p>
          <dl>
            {client && (
              <div>
                <dt>Code client</dt>
                <dd>{client.clientCode}</dd>
              </div>
            )}
            <div>
              <dt>ICE</dt>
              <dd>{ice.replace(/\s/g, "") || "Non renseigné"}</dd>
            </div>
          </dl>
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
          {onCancel ? (
            <button
              type="button"
              className="secondary"
              disabled={isSubmitting}
              onClick={onCancel}
            >
              Annuler
            </button>
          ) : (
            <Link
              className="button-link secondary-link"
              to={client ? `/clients/${client.id}` : "/clients"}
            >
              Annuler
            </Link>
          )}
          <button
            data-testid="client-submit"
            type="submit"
            disabled={isSubmitting}
          >
            <Icon name={client ? "edit" : "plus"} size={16} />
            {isSubmitting
              ? "Enregistrement…"
              : client
                ? "Enregistrer les modifications"
                : "Créer le client"}
          </button>
        </div>
      </footer>
    </form>
  );
}

function FormField({
  id,
  label,
  error,
  required,
  className = "",
  children,
}: {
  id: string;
  label: string;
  error?: string;
  required?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={`form-field ${className}`.trim()}>
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
