import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

export function useDebouncedSearch(delay = 300) {
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get("search") ?? "");
  const urlSearch = params.get("search") ?? "";
  const [previousUrlSearch, setPreviousUrlSearch] = useState(urlSearch);
  // Keep the input aligned with Back/Forward navigation and filter resets.
  if (previousUrlSearch !== urlSearch) {
    setPreviousUrlSearch(urlSearch);
    setSearch(urlSearch);
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const current = params.get("search") ?? "";
      if (current === search.trim()) return;
      setParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          if (search.trim()) next.set("search", search.trim());
          else next.delete("search");
          next.set("page", "0");
          return next;
        },
        { replace: true },
      );
    }, delay);
    return () => window.clearTimeout(timer);
  }, [params, search, setParams, delay]);

  const activeSearch = params.get("search") || undefined;

  return { search, setSearch, activeSearch };
}
