import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Icon, type IconName } from "./Icon";

export function QuickNavigation({
  links,
}: {
  links: { label: string; path: string; icon: IconName; area: string }[];
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [search, setSearch] = useState("");
  const normalize = (value: string) =>
    value
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
  const matches = links.filter((link) =>
    normalize(`${link.area} ${link.label}`).includes(normalize(search.trim())),
  );

  function open() {
    setSearch("");
    if (!dialog.current?.open) dialog.current?.showModal();
    input.current?.focus();
  }

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        if (trigger.current?.closest("[inert]")) return;
        if (document.querySelector("dialog[open]") && !dialog.current?.open)
          return;
        event.preventDefault();
        if (dialog.current?.open) dialog.current.close();
        else open();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <>
      <button
        ref={trigger}
        type="button"
        className="quick-nav-trigger"
        onClick={open}
        aria-haspopup="dialog"
      >
        <Icon name="search" size={17} />
        <span>Accès rapide</span>
        <kbd>⌘ / Ctrl K</kbd>
      </button>
      <dialog
        ref={dialog}
        className="quick-nav-dialog"
        aria-labelledby="quick-nav-title"
        onClose={() => trigger.current?.focus()}
      >
        <div className="quick-nav-heading">
          <div>
            <h2 id="quick-nav-title">Accès rapide</h2>
            <p>Retrouvez les pages de votre espace de travail.</p>
          </div>
          <button
            type="button"
            className="secondary compact-button"
            onClick={() => dialog.current?.close()}
          >
            Fermer
          </button>
        </div>
        <div className="quick-nav-search">
          <Icon name="search" />
          <label className="sr-only" htmlFor="quick-nav-search">
            Rechercher une page
          </label>
          <input
            ref={input}
            id="quick-nav-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Factures, clients, paramètres…"
            autoComplete="off"
          />
        </div>
        <nav className="quick-nav-results" aria-label="Pages disponibles">
          {matches.map((link) => (
            <Link
              key={link.path}
              to={link.path}
              onClick={() => dialog.current?.close()}
            >
              <Icon name={link.icon} />
              <span>
                <strong>{link.label}</strong>
                <small>{link.area}</small>
              </span>
              <span className="quick-nav-arrow" aria-hidden="true">
                ↗
              </span>
            </Link>
          ))}
          {matches.length === 0 && (
            <p role="status" className="quick-nav-empty">
              Aucune page ne correspond à « {search} ».
            </p>
          )}
        </nav>
        <div className="quick-nav-footer">
          Tab pour parcourir les résultats · Entrée pour ouvrir · Échap pour
          fermer
        </div>
      </dialog>
    </>
  );
}
