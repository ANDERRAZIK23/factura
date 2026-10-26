import { z } from "zod";
import { PRODUCT_UNITS } from "../types/product";

const decimal = (label: string) =>
  z
    .string()
    .trim()
    .min(1, `${label} est requis`)
    .refine(
      (value) => /^\d+(?:\.\d+)?$/.test(value),
      `${label} doit être positif ou nul`,
    );

export const productSchema = z.object({
  productName: z
    .string()
    .trim()
    .min(1, "Le nom du produit est requis")
    .max(200, "Le nom du produit ne doit pas dépasser 200 caractères"),
  description: z
    .string()
    .trim()
    .max(1000, "La description ne doit pas dépasser 1 000 caractères"),
  unitPriceExcludingTax: decimal("Le prix unitaire HT").refine(
    (value) => value.split(".")[0].replace(/^0+/, "").length <= 17,
    "Le prix unitaire HT dépasse la précision prise en charge",
  ),
  defaultVatRate: decimal("Le taux de TVA par défaut").refine(
    (value) => Number(value) <= 100,
    "Le taux de TVA par défaut doit être compris entre 0 et 100",
  ),
  unit: z.enum(PRODUCT_UNITS, { error: "L’unité est requise" }),
});

export type ProductFormValues = z.infer<typeof productSchema>;
