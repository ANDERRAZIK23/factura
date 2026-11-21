import { z } from "zod";
import { PAYMENT_METHODS } from "../types/invoice";
import { LINE_VAT_RATE } from "../utils/invoiceCreatePreview";

const requiredNumber = (label: string, decimals: number, positive = false) =>
  z
    .string()
    .trim()
    .min(1, `${label} est requis`)
    .refine(
      (value) =>
        new RegExp(
          `^-?(?:\\d+(?:\\.\\d{0,${decimals}})?|\\.\\d{1,${decimals}})$`,
        ).test(value) && Number.isFinite(Number(value)),
      `${label} accepte jusqu’à ${decimals} décimales`,
    )
    .refine(
      (value) => (positive ? Number(value) > 0 : Number(value) >= 0),
      positive
        ? `${label} doit être supérieure à zéro`
        : `${label} doit être positif ou nul`,
    );

const rate = (label: string) =>
  requiredNumber(label, 2).refine(
    (value) => Number(value) <= 100,
    `${label} doit être compris entre 0 et 100`,
  );

export const invoiceCreateLineSchema = z.object({
  productId: z.string().min(1, "Le produit est requis"),
  quantity: requiredNumber("La quantité", 3, true),
  unitPriceExcludingTax: requiredNumber("Le prix unitaire", 2),
  discountRate: z.literal("").or(rate("Le taux de remise")),
  vatRate: rate("Le taux de TVA"),
});

export const invoiceCreateSchema = z.object({
  invoiceDate: z.string().min(1, "La date de facture est requise"),
  clientId: z.string().min(1, "Le client est requis"),
  paymentMethod: z.enum(PAYMENT_METHODS),
  customerOrderReference: z
    .string()
    .trim()
    .max(
      100,
      "La référence de commande client ne doit pas dépasser 100 caractères",
    ),
  vatRate: z.literal(LINE_VAT_RATE).or(rate("Le taux de TVA")),
  lines: z
    .array(invoiceCreateLineSchema)
    .min(1, "Ajoutez au moins une ligne de facture."),
});

export type InvoiceCreateFormValues = z.infer<typeof invoiceCreateSchema>;
