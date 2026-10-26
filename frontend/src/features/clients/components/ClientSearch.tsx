import { Icon } from "../../../components/ui/Icon";
export function ClientSearch({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="search-field list-search">
      <label htmlFor="client-search">Rechercher des clients</label>
      <div className="list-search-input">
        <Icon name="search" size={16} />
        <input
          id="client-search"
          type="search"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="Code, nom, téléphone, e-mail ou ICE"
        />
      </div>
    </div>
  );
}
