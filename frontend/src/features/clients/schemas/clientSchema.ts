import { z } from "zod";

const optional = (max: number, message: string) =>
  z.string().trim().max(max, message);

export const clientSchema = z.object({
  clientName: z
    .string()
    .trim()
    .min(2, "Le nom du client doit contenir au moins 2 caractères")
    .max(200, "Le nom du client ne doit pas dépasser 200 caractères"),
  phoneNumber: optional(
    30,
    "Le numéro de téléphone ne doit pas dépasser 30 caractères",
  ).regex(
    /^$|^[+0-9() -]+$/,
    "Le numéro de téléphone contient des caractères invalides",
  ),
  address: optional(500, "L’adresse ne doit pas dépasser 500 caractères"),
  email: optional(
    254,
    "L’adresse e-mail ne doit pas dépasser 254 caractères",
  ).refine(
    (value) => value === "" || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value),
    "Saisissez une adresse e-mail valide",
  ),
  ice: z
    .string()
    .trim()
    .refine(
      (value) => value === "" || /^\d{15}$/.test(value.replace(/\s/g, "")),
      "L’ICE doit contenir exactement 15 chiffres",
    ),
});

export type ClientFormValues = z.infer<typeof clientSchema>;
