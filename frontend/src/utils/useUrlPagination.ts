import { useSearchParams } from "react-router-dom";

export function useUrlPagination(defaults: { sort: string }) {
  const [params, setParams] = useSearchParams();
  const pageValue = Number(params.get("page") ?? 0);
  const page =
    Number.isSafeInteger(pageValue) && pageValue >= 0 ? pageValue : 0;
  const sizeValue = Number(params.get("size") ?? 20);
  const size = [10, 20, 50, 100].includes(sizeValue) ? sizeValue : 20;
  const sort = params.get("sort") ?? defaults.sort;

  function updateParam(name: string, value: string) {
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      if (value) next.set(name, value);
      else next.delete(name);
      next.set("page", "0");
      return next;
    });
  }

  function setPage(nextPage: number) {
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("page", String(nextPage));
      return next;
    });
  }

  function param(name: string, fallback = "") {
    return params.get(name) ?? fallback;
  }
  function resetFilters() {
    setParams(size === 20 ? {} : { size: String(size) });
  }

  return {
    page,
    size,
    sort,
    params,
    updateParam,
    setPage,
    param,
    resetFilters,
  };
}
