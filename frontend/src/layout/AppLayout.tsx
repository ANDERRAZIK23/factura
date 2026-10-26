import { useEffect, useRef, useState } from "react";
import { QuickNavigation } from "../components/ui/QuickNavigation";
import { ImpersonationBanner } from "../components/ui/ImpersonationBanner";
import { Toaster } from "sonner";
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import type { UserRole } from "../auth/types";
import {
  useCompanyBranding,
  useCurrentCompany,
} from "../features/companies/hooks/useCompanies";
import { formatRole } from "../utils/format";
import { Icon, type IconName } from "../components/ui/Icon";
import { FacturaLogo } from "../components/ui/FacturaLogo";

type NavigationLink = {
  label: string;
  path: string;
  roles: UserRole[];
  icon: IconName;
};

const navigation: { label: string; links: NavigationLink[] }[] = [
  {
    label: "Espace de travail",
    links: [
      {
        label: "Tableau de bord",
        path: "/dashboard",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "dashboard",
      },
    ],
  },
  {
    label: "Ventes",
    links: [
      {
        label: "Devis",
        path: "/quotes",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "invoice",
      },
      {
        label: "Factures",
        path: "/invoices",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "invoice",
      },
      {
        label: "Échéances et relances",
        path: "/collections",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "status",
      },
      {
        label: "Factures récurrentes",
        path: "/recurring-invoices",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "invoice",
      },
      {
        label: "Clients",
        path: "/clients",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "client",
      },
    ],
  },
  {
    label: "Catalogue",
    links: [
      {
        label: "Produits et services",
        path: "/products",
        roles: ["APP_ADMIN", "ADMIN", "INVOICE_MANAGER", "VIEWER"],
        icon: "product",
      },
    ],
  },
  {
    label: "Entreprise",
    links: [
      {
        label: "Utilisateurs",
        path: "/users",
        roles: ["APP_ADMIN", "ADMIN"],
        icon: "users",
      },
      {
        label: "Design des factures",
        path: "/invoice-designer",
        roles: ["APP_ADMIN", "ADMIN"],
        icon: "edit",
      },
      {
        label: "Profil de l’entreprise",
        path: "/settings",
        roles: ["APP_ADMIN", "ADMIN"],
        icon: "settings",
      },
    ],
  },
  {
    label: "Plateforme",
    links: [
      {
        label: "Entreprises",
        path: "/companies",
        roles: ["APP_ADMIN"],
        icon: "companies",
      },
      {
        label: "Design des contrats",
        path: "/contract-designer",
        roles: ["APP_ADMIN"],
        icon: "edit",
      },
      {
        label: "Design des factures",
        path: "/invoice-designer",
        roles: ["APP_ADMIN"],
        icon: "invoice",
      },
    ],
  },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const company = useCurrentCompany();
  const branding = useCompanyBranding();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const sidebar = useRef<HTMLElement>(null);
  const mobileTrigger = useRef<HTMLButtonElement>(null);
  const userMenu = useRef<HTMLDetailsElement>(null);
  const initials =
    user?.fullName
      .split(/\s+/)
      .map((part) => part[0])
      .join("")
      .slice(0, 2)
      .toUpperCase() || "FM";
  const companyInitial =
    company.data?.companyName.charAt(0).toUpperCase() || "C";
  const platformArea = user?.role === "APP_ADMIN";
  const companyLogo = platformArea ? null : branding.data?.logoDataUrl;
  const page = pageContext(location.pathname);
  const visibleNavigation = navigation
    .map((section) => ({
      ...section,
      links: section.links.filter(
        (link) =>
          user &&
          link.roles.includes(user.role) &&
          (!platformArea ||
            section.label === "Espace de travail" ||
            section.label === "Plateforme"),
      ),
    }))
    .filter((section) => section.links.length > 0);

  useEffect(() => {
    setMobileOpen(false);
    if (userMenu.current) userMenu.current.open = false;
  }, [location.pathname]);

  useEffect(() => {
    const media = window.matchMedia("(max-width: 820px)");
    const update = () => {
      setIsMobile(media.matches);
      if (!media.matches) setMobileOpen(false);
    };
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    if (!mobileOpen) return;
    const previousOverflow = document.body.style.overflow;
    const trigger = mobileTrigger.current;
    document.body.style.overflow = "hidden";
    sidebar.current?.querySelector<HTMLElement>("a")?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setMobileOpen(false);
      if (event.key !== "Tab") return;
      const focusable = Array.from(
        sidebar.current?.querySelectorAll<HTMLElement>("a[href], button") ?? [],
      ).filter((element) => element.getClientRects().length > 0);
      const first = focusable[0];
      const last = focusable.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKeyDown);
      trigger?.focus();
    };
  }, [mobileOpen]);

  useEffect(() => {
    function closeMenu(event: PointerEvent | KeyboardEvent) {
      if (event instanceof KeyboardEvent) {
        if (event.key === "Escape" && userMenu.current?.open) {
          userMenu.current.open = false;
          userMenu.current.querySelector("summary")?.focus();
        }
      } else if (
        event.target instanceof Node &&
        !userMenu.current?.contains(event.target) &&
        userMenu.current
      ) {
        userMenu.current.open = false;
      }
    }
    document.addEventListener("pointerdown", closeMenu);
    document.addEventListener("keydown", closeMenu);
    return () => {
      document.removeEventListener("pointerdown", closeMenu);
      document.removeEventListener("keydown", closeMenu);
    };
  }, []);

  async function signOut() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <>
      <a className="skip-link" href="#main-content">
        Aller au contenu
      </a>
      <Toaster
        richColors
        position="top-right"
        style={{ fontFamily: "var(--font-sans)" }}
      />
      <ImpersonationBanner />
      <div
        className={`app-shell${collapsed ? " sidebar-collapsed" : ""}${mobileOpen ? " sidebar-mobile-open" : ""}`}
      >
        <aside
          ref={sidebar}
          id="app-navigation"
          className="sidebar"
          aria-label="Barre latérale de l’application"
          inert={isMobile && !mobileOpen}
        >
          <div className="sidebar-header">
            <Link
              className="brand"
              to="/dashboard"
              aria-label="Tableau de bord Factura"
              title="Factura"
            >
              <FacturaLogo iconOnly={collapsed && !isMobile} />
            </Link>

            <button
              type="button"
              className="sidebar-mobile-close"
              aria-label="Fermer la navigation"
              onClick={() => setMobileOpen(false)}
            >
              ×
            </button>
          </div>
          <nav id="primary-navigation" aria-label="Navigation principale">
            {visibleNavigation.map((section) => {
              const visibleLinks = section.links;
              return (
                visibleLinks.length > 0 && (
                  <div className="nav-section" key={section.label}>
                    <span className="nav-section-label">{section.label}</span>
                    <div className="nav-links">
                      {visibleLinks.map((link) => (
                        <NavLink
                          key={link.path}
                          to={link.path}
                          title={collapsed ? link.label : undefined}
                        >
                          <Icon name={link.icon} />
                          <span>{link.label}</span>
                        </NavLink>
                      ))}
                    </div>
                  </div>
                )
              );
            })}
          </nav>
          <div className="sidebar-context">
            <CompanyMark
              className="sidebar-context-mark"
              logo={companyLogo}
              fallback={platformArea ? "P" : companyInitial}
            />
            <span>
              <small>
                {platformArea
                  ? "Périmètre d’administration"
                  : "Entreprise active"}
              </small>
              <strong>
                {platformArea
                  ? "Toutes les entreprises"
                  : (company.data?.companyName ??
                    (company.isPending ? "Chargement…" : "Espace entreprise"))}
              </strong>
            </span>
          </div>
        </aside>

        {mobileOpen && (
          <button
            type="button"
            className="sidebar-backdrop"
            aria-label="Fermer la navigation"
            onClick={() => setMobileOpen(false)}
          />
        )}

        <div className="app-workspace" inert={isMobile && mobileOpen}>
          <header className="topbar">
            <button
              ref={mobileTrigger}
              type="button"
              className="mobile-menu-button"
              aria-label={
                mobileOpen ? "Fermer la navigation" : "Ouvrir la navigation"
              }
              aria-expanded={mobileOpen}
              aria-controls="app-navigation"
              onClick={() => setMobileOpen((value) => !value)}
            >
              <Icon name="menu" />
            </button>
            <button
              type="button"
              className="desktop-menu-button"
              aria-label={
                collapsed ? "Développer la navigation" : "Réduire la navigation"
              }
              aria-expanded={!collapsed}
              aria-controls="primary-navigation"
              onClick={() => setCollapsed((value) => !value)}
            >
              <Icon name="columns" size={18} />
            </button>
            <Link
              className="topbar-brand"
              to="/dashboard"
              aria-label="Accueil Factura"
            >
              <FacturaLogo />
            </Link>
            <div className="topbar-page-context">
              <small>{page.area}</small>
              <span aria-hidden="true">/</span>
              <strong>{page.title}</strong>
            </div>
            <QuickNavigation
              links={visibleNavigation.flatMap((section) =>
                section.links.map((link) => ({ ...link, area: section.label })),
              )}
            />
            <div
              className={`workspace-context${platformArea ? " is-platform" : ""}`}
            >
              <CompanyMark
                className="workspace-context-mark"
                logo={companyLogo}
                fallback={platformArea ? "P" : companyInitial}
              />
              <span>
                <small>
                  {platformArea
                    ? "Administration de la plateforme"
                    : "Entreprise active"}
                </small>
                <strong>
                  {platformArea
                    ? "Toutes les entreprises"
                    : (company.data?.companyName ?? "Espace entreprise")}
                </strong>
              </span>
            </div>
            <details ref={userMenu} className="user-menu">
              <summary
                aria-label={`Compte de ${user?.fullName ?? "l’utilisateur"}`}
              >
                <span className="account-avatar" aria-hidden="true">
                  {initials}
                </span>
                <span className="user-menu-copy">
                  <strong>{user?.fullName}</strong>
                  <small>{formatRole(user?.role)}</small>
                </span>
                <span aria-hidden="true">⌄</span>
              </summary>
              <div className="user-menu-panel">
                <div className="user-menu-identity">
                  <strong>{user?.fullName}</strong>
                  <small>{user?.email}</small>
                  <span>{formatRole(user?.role)}</span>
                </div>
                {user?.role === "ADMIN" && (
                  <Link to="/settings">Profil de l’entreprise</Link>
                )}
                {user?.role === "APP_ADMIN" && (
                  <Link to="/companies">Entreprises de la plateforme</Link>
                )}
                <button data-testid="logout" type="button" onClick={signOut}>
                  Se déconnecter
                </button>
              </div>
            </details>
          </header>
          <main id="main-content" className="content" tabIndex={-1}>
            <div className="content-inner">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </>
  );
}

function CompanyMark({
  className,
  logo,
  fallback,
}: {
  className: string;
  logo?: string | null;
  fallback: string;
}) {
  return (
    <span className={className} aria-hidden="true">
      {logo ? <img src={logo} alt="" /> : fallback}
    </span>
  );
}

function pageContext(pathname: string) {
  const segment = pathname.split("/").filter(Boolean)[0] ?? "dashboard";
  const contexts: Record<string, { area: string; title: string }> = {
    dashboard: { area: "Accueil", title: "Tableau de bord" },
    invoices: {
      area: "Ventes",
      title: pathname.endsWith("/new") ? "Nouvelle facture" : "Factures",
    },
    clients: {
      area: "Ventes",
      title: pathname.endsWith("/new") ? "Nouveau client" : "Clients",
    },
    products: {
      area: "Catalogue",
      title: pathname.endsWith("/new")
        ? "Nouveau produit"
        : "Produits et services",
    },
    users: { area: "Entreprise", title: "Utilisateurs" },
    quotes: { area: "Ventes", title: "Devis" },
    collections: { area: "Ventes", title: "Échéances et relances" },
    "recurring-invoices": { area: "Ventes", title: "Factures récurrentes" },
    "invoice-designer": {
      area: "Administration",
      title: "Design des factures",
    },
    settings: { area: "Entreprise", title: "Profil de l’entreprise" },
    companies: {
      area: "Plateforme",
      title: pathname.endsWith("/new") ? "Nouvelle entreprise" : "Entreprises",
    },
    "contract-designer": { area: "Plateforme", title: "Design des contrats" },
  };
  return contexts[segment] ?? { area: "Application", title: "Facture" };
}
