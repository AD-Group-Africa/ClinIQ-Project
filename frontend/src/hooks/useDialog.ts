import { useCallback, useEffect, useRef, type MouseEvent, type SyntheticEvent } from 'react';

/**
 * Wires a native <dialog> element to React state.
 *
 * Using the browser's own modal <dialog> gives us, without a library:
 *  - a focus trap and an inert page behind the dialog,
 *  - Escape to close,
 *  - focus returned to the element that opened it.
 *
 * Put `data-autofocus` on the element that should receive focus first (for example the Cancel
 * button of a destructive confirmation). Otherwise the browser focuses the first focusable element.
 */
export function useDialog(open: boolean, onClose: () => void, dismissible = true) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
      dialog.querySelector<HTMLElement>('[data-autofocus]')?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  // Native dialogs do not stop the page behind from scrolling, so lock it while open.
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  const onCancel = useCallback(
    (event: SyntheticEvent<HTMLDialogElement>) => {
      // "cancel" fires on Escape. Block it while an action is running.
      if (!dismissible) event.preventDefault();
    },
    [dismissible]
  );

  const onBackdropClick = useCallback(
    (event: MouseEvent<HTMLDialogElement>) => {
      // A click on the dialog element itself (not its children) is a click on the backdrop.
      if (dismissible && event.target === event.currentTarget) onClose();
    },
    [dismissible, onClose]
  );

  return { ref, dialogProps: { onClose, onCancel, onClick: onBackdropClick } };
}
