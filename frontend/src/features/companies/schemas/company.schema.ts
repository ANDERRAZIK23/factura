import { z } from "zod";

const phoneRegex = /^(?:(?:\+|00)\d{1,3})?[\s.-]?\(?\d{1,4}\)?[\s.-]?\d{1,4}[\s.-]?\d{1,9}$/;

export const companySchema = z.object({
  companyName: z.string().min(2, "Le nom de l'entreprise doit contenir au moins 2 caractères."),
  address: z.string().optional(),
  phone: z
    .string()
    .optional()
    .refine((val) => !val || phoneRegex.test(val), {
      message: "Format de téléphone invalide (ex: +212600000000 ou 0600000000).",
    }),
  email: z
    .string()
    .optional()
    .refine((val) => !val || z.string().email().safeParse(val).success, {
      message: "Adresse e-mail invalide.",
    }),
  ice: z
    .string()
    .optional()
    .refine((val) => !val || /^\d{15}$/.test(val), {
      message: "L'ICE doit contenir exactement 15 chiffres.",
    }),
});

export type CompanyFormData = z.infer<typeof companySchema>;
