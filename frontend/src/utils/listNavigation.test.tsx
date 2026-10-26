import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { useDebouncedSearch } from "./useDebouncedSearch";
import { useUrlPagination } from "./useUrlPagination";

function Navigation() {
  const search = useDebouncedSearch(30);
  const pagination = useUrlPagination({ sort: "name,asc" });
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <input
        aria-label="Recherche"
        value={search.search}
        onChange={(event) => search.setSearch(event.target.value)}
      />
      <output data-testid="url">{location.search}</output>
      <output data-testid="page">{pagination.page}</output>
      <output data-testid="size">{pagination.size}</output>
      <button onClick={() => navigate(-1)}>Retour</button>
      <button
        onClick={() => {
          search.setSearch("");
          pagination.resetFilters();
        }}
      >
        Réinitialiser
      </button>
      <button onClick={() => pagination.updateParam("size", "50")}>
        50 lignes
      </button>
    </>
  );
}

describe("list navigation", () => {
  it("restores the search on Back without rewriting the previous URL", async () => {
    render(
      <MemoryRouter initialEntries={["/?search=ancien", "/?search=actuel"]}>
        <Navigation />
      </MemoryRouter>,
    );
    await userEvent.clear(screen.getByLabelText("Recherche"));
    await userEvent.type(screen.getByLabelText("Recherche"), "nouveau");
    await waitFor(() =>
      expect(screen.getByTestId("url")).toHaveTextContent("search=nouveau"),
    );
    await userEvent.click(screen.getByText("Retour"));
    expect(screen.getByLabelText("Recherche")).toHaveValue("ancien");
    await act(() => new Promise((resolve) => setTimeout(resolve, 80)));
    expect(screen.getByTestId("url")).toHaveTextContent("search=ancien");
  });

  it("clears filters and a pending search while preserving the page size", async () => {
    render(
      <MemoryRouter
        initialEntries={["/?search=ancien&active=false&page=3&size=50"]}
      >
        <Navigation />
      </MemoryRouter>,
    );
    await userEvent.type(screen.getByLabelText("Recherche"), " suite");
    await userEvent.click(screen.getByText("Réinitialiser"));
    await act(() => new Promise((resolve) => setTimeout(resolve, 80)));
    expect(screen.getByLabelText("Recherche")).toHaveValue("");
    expect(screen.getByTestId("url")).toHaveTextContent(/^\?size=50$/);
    expect(screen.getByTestId("page")).toHaveTextContent("0");
  });

  it("rejects invalid page values and returns to page one when size changes", async () => {
    render(
      <MemoryRouter initialEntries={["/?page=Infinity&size=999&search=client"]}>
        <Navigation />
      </MemoryRouter>,
    );
    expect(screen.getByTestId("page")).toHaveTextContent("0");
    expect(screen.getByTestId("size")).toHaveTextContent("20");
    await userEvent.click(screen.getByText("50 lignes"));
    expect(screen.getByTestId("url")).toHaveTextContent("page=0");
    expect(screen.getByTestId("url")).toHaveTextContent("size=50");
    expect(screen.getByTestId("url")).toHaveTextContent("search=client");
  });
});
