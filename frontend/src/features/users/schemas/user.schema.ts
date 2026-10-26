import { z } from "zod";

export const companyUserSchema = z.object({
  fullName: z.string().min(2, "Le nom doit contenir au moins 2 caractères."),
  email: z.string().email("Adresse e-mail invalide."),
  password: z.string().min(12, "Le mot de passe doit contenir au moins 12 caractères."),
  role: z.enum(["ADMIN", "INVOICE_MANAGER", "VIEWER"], {
    message: "Veuillez sélectionner un rôle valide.",
  }),
});

export type CompanyUserFormData = z.infer<typeof companyUserSchema>;
