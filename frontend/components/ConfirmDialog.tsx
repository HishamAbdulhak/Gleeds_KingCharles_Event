"use client";

import { ReactNode, useEffect, useRef } from "react";

/**
 * The Admin's confirmation before a destructive action. Cancel holds the focus, so Enter or Esc never destroys
 * anything; the confirm button is the copper danger one. Esc (the native dialog's cancel) calls `onCancel`.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  busyLabel,
  busy,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  /** the confirm button's text while the action runs, e.g. "Resetting…" */
  busyLabel: string;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    if (open) dialog.current?.showModal();
    else dialog.current?.close();
  }, [open]);

  return (
    <dialog
      ref={dialog}
      aria-labelledby="confirm-title"
      onCancel={(e) => {
        e.preventDefault(); // the parent closes it, so `open` stays the truth
        if (!busy) onCancel();
      }}
      className="panel m-auto w-full max-w-md text-marble backdrop:bg-black/60"
    >
      {open ? (
        <div className="flex flex-col gap-4">
          <h2 id="confirm-title" className="text-lg">
            {title}
          </h2>
          <div className="flex flex-col gap-2 text-sm text-marble/80">{children}</div>
          <div className="flex justify-end gap-3">
            <button type="button" autoFocus disabled={busy} onClick={onCancel} className="btn btn-secondary">
              Cancel
            </button>
            <button type="button" disabled={busy} onClick={onConfirm} className="btn btn-danger">
              {busy ? busyLabel : confirmLabel}
            </button>
          </div>
        </div>
      ) : null}
    </dialog>
  );
}
