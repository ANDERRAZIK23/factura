import { useEffect, useRef } from "react";
import { ClientForm } from "../../clients/components/ClientForm";
import { useCreateClient } from "../../clients/hooks/useClients";
import type { Client } from "../../clients/types/client";

export function InvoiceClientDialog({
  onCreated,
  onClose,
}: {
  onCreated: (client: Client) => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const mutation = useCreateClient();
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  return (
    <dialog
      ref={dialog}
      className="invoice-client-dialog"
      aria-labelledby="invoice-new-client-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!mutation.isPending) {
          dialog.current?.close();
          onClose();
        }
      }}
    >
      <header className="invoice-client-dialog-header">
        <p className="editor-kicker">Répertoire clients</p>
        <h2 id="invoice-new-client-title">Nouveau client</h2>
        <p>
          Créez le client et retrouvez votre facture avec vos lignes et montants
          conservés.
        </p>
      </header>
      <ClientForm
        onCancel={() => {
          dialog.current?.close();
          onClose();
        }}
        onSubmit={async (input) => {
          const client = await mutation.mutateAsync(input);
          dialog.current?.close();
          onCreated(client);
        }}
      />
    </dialog>
  );
}
