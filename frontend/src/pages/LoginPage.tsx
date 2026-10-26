import { zodResolver } from "@hookform/resolvers/zod";
import axios from "axios";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { z } from "zod";
import { useAuth } from "../auth/useAuth";
import { FacturaLogo } from "../components/ui/FacturaLogo";
import { Icon } from "../components/ui/Icon";
import "./login.css";

const schema = z.object({
  email: z.email("Saisissez une adresse e-mail valide"),
  password: z.string().min(1, "Le mot de passe est requis"),
});
type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const authenticationRequired = Boolean(
    (location.state as { authenticationRequired?: boolean } | null)
      ?.authenticationRequired,
  );
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });
  if (user) return <Navigate to="/dashboard" replace />;
  const submit = async (values: FormValues) => {
    try {
      await login(values.email, values.password);
      const destination =
        (location.state as { from?: string } | null)?.from ?? "/dashboard";
      navigate(destination, { replace: true });
    } catch (error) {
      setError("root", {
        message:
          axios.isAxiosError(error) && error.response?.status === 401
            ? "Adresse e-mail ou mot de passe incorrect"
            : "Impossible de contacter le serveur. Veuillez réessayer.",
      });
    }
  };
  return (
    <main className="auth-page">
      <div className="auth-shell">
        <aside className="auth-intro" aria-labelledby="auth-intro-title">
          <div className="auth-brand">
            <FacturaLogo />
            <span>Gestion d’entreprise</span>
          </div>
          <div className="auth-intro-content">
            <p className="auth-kicker">Votre activité, en toute clarté</p>
            <h2 id="auth-intro-title">
              La facturation au cœur de votre entreprise.
            </h2>
            <p className="auth-intro-description">
              Retrouvez vos clients, votre catalogue et vos factures dans un
              espace de travail commun.
            </p>
            <ul className="auth-capabilities">
              <li>
                <span className="auth-capability-icon">
                  <Icon name="invoice" size={20} />
                </span>
                <div>
                  <strong>Un suivi de facturation complet</strong>
                  <p>De la création de vos factures au suivi de leur statut.</p>
                </div>
              </li>
              <li>
                <span className="auth-capability-icon">
                  <Icon name="product" size={20} />
                </span>
                <div>
                  <strong>Vos données réunies</strong>
                  <p>Des clients et un catalogue accessibles à votre équipe.</p>
                </div>
              </li>
              <li>
                <span className="auth-capability-icon">
                  <Icon name="users" size={20} />
                </span>
                <div>
                  <strong>Des accès adaptés à chaque rôle</strong>
                  <p>Un environnement organisé pour votre entreprise.</p>
                </div>
              </li>
            </ul>
          </div>
          <div className="auth-intro-footer">
            <Icon name="building" size={16} />
            <span>Un espace dédié à votre entreprise</span>
          </div>
        </aside>
        <section className="auth-access" aria-labelledby="login-title">
          <p className="auth-access-caption">Espace entreprise</p>
          <div className="auth-form-content">
            <div className="auth-form-icon" aria-hidden="true">
              <Icon name="building" size={24} />
            </div>
            <p className="auth-kicker">Connexion</p>
            <h1 id="login-title">Bon retour parmi nous</h1>
            <p className="auth-form-description" id="login-description">
              {authenticationRequired
                ? "Votre session a expiré. Reconnectez-vous pour continuer."
                : "Connectez-vous pour accéder à l’espace de votre entreprise."}
            </p>
            <form
              onSubmit={handleSubmit(submit)}
              noValidate
              aria-describedby={
                errors.root ? "login-error" : "login-description"
              }
              aria-busy={isSubmitting}
            >
              <div className="auth-field">
                <label htmlFor="email">E-mail</label>
                <input
                  id="email"
                  type="email"
                  autoComplete="username"
                  placeholder="vous@entreprise.com"
                  autoFocus
                  disabled={isSubmitting}
                  {...register("email")}
                  aria-invalid={Boolean(errors.email)}
                  aria-describedby={errors.email ? "email-error" : undefined}
                />
                {errors.email && (
                  <p className="field-error" id="email-error" role="alert">
                    {errors.email.message}
                  </p>
                )}
              </div>
              <div className="auth-field">
                <label htmlFor="password">Mot de passe</label>
                <div className="auth-password-field">
                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="Saisissez votre mot de passe"
                    disabled={isSubmitting}
                    {...register("password")}
                    aria-invalid={Boolean(errors.password)}
                    aria-describedby={
                      errors.password ? "password-error" : undefined
                    }
                  />
                  <button
                    type="button"
                    className="auth-password-toggle"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={
                      showPassword
                        ? "Masquer le mot de passe"
                        : "Afficher le mot de passe"
                    }
                    aria-controls="password"
                    disabled={isSubmitting}
                  >
                    <Icon name={showPassword ? "eye-off" : "eye"} size={18} />
                  </button>
                </div>
                {errors.password && (
                  <p className="field-error" id="password-error" role="alert">
                    {errors.password.message}
                  </p>
                )}
              </div>

              {errors.root && (
                <p className="form-error" id="login-error" role="alert">
                  {errors.root.message}
                </p>
              )}
              <button
                data-testid="login-submit"
                type="submit"
                disabled={isSubmitting}
                className="auth-submit"
              >
                {isSubmitting ? "Connexion…" : "Se connecter"}
              </button>
            </form>
            <p className="auth-account-help">
              Besoin d’un accès ? Contactez l’administrateur de votre
              entreprise.
            </p>
          </div>
          <p className="auth-access-footer">
            Factura <span aria-hidden="true">·</span> Gestion de facturation
          </p>
        </section>
      </div>
    </main>
  );
}
