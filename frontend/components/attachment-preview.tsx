'use client';

import { useEffect } from 'react';
import { X } from 'lucide-react';

/** Modal that shows the full text of a pasted attachment. Closes on
 *  outside-click, ESC, or the X button. Uses a portal-less fixed
 *  overlay because Next 15 app router + client components + dialog
 *  portals don't always SSR cleanly. */
export function AttachmentPreview({
  text,
  onClose,
}: {
  text: string;
  onClose: () => void;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[80vh] w-full max-w-3xl flex-col rounded-lg border border-neutral-200 bg-white shadow-xl dark:border-neutral-800 dark:bg-neutral-900"
      >
        <header className="flex items-center justify-between border-b border-neutral-200 px-4 py-3 dark:border-neutral-800">
          <div>
            <h2 className="text-sm font-semibold">Yapıştırılan metin</h2>
            <p className="text-xs text-neutral-500">
              {text.length.toLocaleString('tr-TR')} karakter
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Kapat"
            className="rounded p-1 hover:bg-neutral-100 dark:hover:bg-neutral-800"
          >
            <X size={16} />
          </button>
        </header>
        <pre className="m-0 flex-1 overflow-auto whitespace-pre-wrap break-words p-4 font-mono text-xs leading-relaxed text-neutral-700 dark:text-neutral-300">
          {text}
        </pre>
      </div>
    </div>
  );
}
