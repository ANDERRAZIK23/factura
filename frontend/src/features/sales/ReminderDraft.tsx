import { useEffect, useRef, useState } from "react";
import type { Reminder } from "./salesApi";
import "./sales.css";

export function ReminderDraft({
  reminder,
  close,
}: {
  reminder: Reminder;
  close: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [body, setBody] = useState(reminder.body);
  const [message, setMessage] = useState("");
  useEffect(() => {
    const target = dialog.current;
    target?.showModal();
    return () => target?.close();
  }, []);
  async function copy() {
    try {
      await navigator.clipboard.writeText(
        `Objet : ${reminder.subject}\n\n${body}`,
      );
      setMessage("Message copié.");
    } catch {
      setMessage(
        "La copie est indisponible. Sélectionnez le texte du message pour le copier.",
      );
    }
  }
  return (
    <dialog
      ref={dialog}
      className="sales-dialog sales-form"
      onCancel={close}
      aria-labelledby="reminder-title"
    >
      <h2 id="reminder-title">Préparer la relance</h2>
      <p className="sales-muted">
        Copiez le message dans votre messagerie pour l’envoyer au client.
      </p>
      <p>
        Destinataire : {reminder.recipient ?? "Adresse e-mail non renseignée"}
      </p>
      <p>
        <strong>{reminder.subject}</strong>
      </p>
      <label>
        Message
        <textarea value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      {message && <p role="status">{message}</p>}
      <div className="sales-actions">
        <button type="button" onClick={() => void copy()}>
          Copier le message
        </button>
        <button type="button" className="secondary" onClick={close}>
          Fermer
        </button>
      </div>
    </dialog>
  );
}
