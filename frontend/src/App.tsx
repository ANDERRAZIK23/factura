import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { RoleProtectedRoute } from "./auth/RoleProtectedRoute";
import { AppLayout } from "./layout/AppLayout";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { ForbiddenPage, NotFoundPage } from "./pages/StatusPage";
import { UsersPage } from "./pages/UsersPage";
import { ClientListPage } from "./features/clients/pages/ClientListPage";
import { ClientCreatePage } from "./features/clients/pages/ClientCreatePage";
import { ClientDetailsPage } from "./features/clients/pages/ClientDetailsPage";
import { ClientEditPage } from "./features/clients/pages/ClientEditPage";
import { ProductListPage } from "./features/products/pages/ProductListPage";
import { ProductCreatePage } from "./features/products/pages/ProductCreatePage";
import { ProductDetailsPage } from "./features/products/pages/ProductDetailsPage";
import { ProductEditPage } from "./features/products/pages/ProductEditPage";
import { InvoiceListPage } from "./features/invoices/pages/InvoiceListPage";
import { InvoiceCreatePage } from "./features/invoices/pages/InvoiceCreatePage";
import { InvoiceDetailsPage } from "./features/invoices/pages/InvoiceDetailsPage";
import { InvoiceEditPage } from "./features/invoices/pages/InvoiceEditPage";
import { CompaniesPage } from "./features/companies/pages/CompaniesPage";
import { CompanyCreatePage } from "./features/companies/pages/CompanyCreatePage";
import { CompanyDetailsPage } from "./features/companies/pages/CompanyDetailsPage";
import { CompanySettingsPage } from "./features/companies/pages/CompanySettingsPage";

import { InvoiceDesignerPage } from "./features/invoice-designs/InvoiceDesignerPage";
import {
  QuotationListPage,
  QuotationCreatePage,
  QuotationDetailsPage,
} from "./features/sales/QuotationPages";
import {
  RecurringListPage,
  RecurringCreatePage,
} from "./features/sales/RecurringPages";
import { CollectionsPage } from "./features/sales/CollectionsPage";
import { ClientPortalPage } from "./features/portal/ClientPortalPage";

const ContractDesignerPage = lazy(
  () => import("./features/contract-designs/ContractDesignerPage"),
);

export function App() {
  return (
    <Routes>
      <Route path="/portal" element={<ClientPortalPage />} />
      <Route path="*" element={<StaffApp />} />
    </Routes>
  );
}

function StaffApp() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forbidden" element={<ForbiddenPage />} />
        <Route
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route
            path="/contract-designer"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN"]}>
                <Suspense
                  fallback={
                    <div className="loading-state" role="status">
                      Chargement du designer de contrats…
                    </div>
                  }
                >
                  <ContractDesignerPage />
                </Suspense>
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/invoice-designer"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN", "ADMIN"]}>
                <InvoiceDesignerPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/quotes" element={<QuotationListPage />} />
          <Route
            path="/quotes/new"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <QuotationCreatePage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/quotes/:id" element={<QuotationDetailsPage />} />
          <Route path="/collections" element={<CollectionsPage />} />
          <Route path="/recurring-invoices" element={<RecurringListPage />} />
          <Route
            path="/recurring-invoices/new"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <RecurringCreatePage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/users"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN", "ADMIN"]}>
                <UsersPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/clients" element={<ClientListPage />} />
          <Route
            path="/clients/new"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <ClientCreatePage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/clients/:id" element={<ClientDetailsPage />} />
          <Route
            path="/clients/:id/edit"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <ClientEditPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/products" element={<ProductListPage />} />
          <Route
            path="/products/new"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <ProductCreatePage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/products/:id" element={<ProductDetailsPage />} />
          <Route
            path="/products/:id/edit"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <ProductEditPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/invoices" element={<InvoiceListPage />} />
          <Route
            path="/invoices/new"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <InvoiceCreatePage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/invoices/:id" element={<InvoiceDetailsPage />} />
          <Route
            path="/invoices/:id/edit"
            element={
              <RoleProtectedRoute
                roles={["APP_ADMIN", "ADMIN", "INVOICE_MANAGER"]}
              >
                <InvoiceEditPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN", "ADMIN"]}>
                <CompanySettingsPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/companies"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN"]}>
                <CompaniesPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/companies/new"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN"]}>
                <CompanyCreatePage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/companies/:id"
            element={
              <RoleProtectedRoute roles={["APP_ADMIN"]}>
                <CompanyDetailsPage />
              </RoleProtectedRoute>
            }
          />
        </Route>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthProvider>
  );
}
