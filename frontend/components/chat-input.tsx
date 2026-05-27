'use client';

import { ClipboardEvent, useState } from 'react';
import { Paperclip, X } from 'lucide-react';

/** Threshold (chars) above which a paste becomes an attachment chip
 *  rather than dumping the whole blob into the textarea. Chosen empirically:
 *  ~3 sentences of plain text fit under the limit, longer pastes (logs,
 *  code, articles) get chipped so the input stays readable.
 */
const PASTE_AS_ATTACHMENT_CHARS = 280;

interface PasteAttachment {
  id: number;
  text: string;
  /** Optional MIME-ish label — set to 'paste' for clipboard pastes,
   *  reserved for future file-drag-drop. */
  kind: string;
}

export function ChatInput({
  onSend,
  disabled,
}: {
  onSend: (text: string) => void;
  disabled?: boolean;
}) {
  const [text, setText] = useState('');
  const [attachments, setAttachments] = useState<PasteAttachment[]>([]);

  function submit() {
    const trimmed = text.trim();
    if ((!trimmed && attachments.length === 0) || disabled) return;

    // Compose: attachments first as fenced context blocks, user prompt last.
    // The fenced shape keeps the model from confusing pasted content with
    // the actual instruction.
    const parts: string[] = [];
    for (const att of attachments) {
      parts.push(`<attached id="${att.id}">\n${att.text}\n</attached>`);
    }
    if (trimmed) parts.push(trimmed);

    onSend(parts.join('\n\n'));
    setText('');
    setAttachments([]);
  }

  function handlePaste(e: ClipboardEvent<HTMLTextAreaElement>) {
    const pasted = e.clipboardData.getData('text');
    if (pasted.length <= PASTE_AS_ATTACHMENT_CHARS) return; // normal paste
    e.preventDefault();
    setAttachments((prev) => [
      ...prev,
      { id: Date.now() + prev.length, text: pasted, kind: 'paste' },
    ]);
  }

  function removeAttachment(id: number) {
    setAttachments((prev) => prev.filter((a) => a.id !== id));
  }

  return (
    <div className="border-t border-neutral-200 p-4 dark:border-neutral-800">
      {attachments.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-2">
          {attachments.map((att) => (
            <div
              key={att.id}
              className="flex items-center gap-2 rounded-md border border-neutral-300 bg-neutral-50 px-2 py-1 text-xs dark:border-neutral-700 dark:bg-neutral-800"
              title={att.text.slice(0, 600)}
            >
              <Paperclip size={12} />
              <span className="font-medium">Yapıştırılan metin</span>
              <span className="text-neutral-500">
                {att.text.length.toLocaleString('tr-TR')} karakter
              </span>
              <button
                type="button"
                onClick={() => removeAttachment(att.id)}
                aria-label="Kaldır"
                className="rounded p-0.5 hover:bg-neutral-200 dark:hover:bg-neutral-700"
              >
                <X size={12} />
              </button>
            </div>
          ))}
        </div>
      )}
      <div className="flex gap-2">
        <textarea
          rows={1}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onPaste={handlePaste}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
          placeholder={
            attachments.length > 0
              ? 'Bir soru ekleyin (Enter ile gönder)…'
              : 'Soru yazın…'
          }
          className="flex-1 resize-none rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-neutral-400 dark:border-neutral-700 dark:bg-neutral-900"
          disabled={disabled}
        />
        <button
          onClick={submit}
          disabled={disabled || (!text.trim() && attachments.length === 0)}
          className="rounded-md bg-neutral-900 px-4 text-sm font-medium text-white disabled:opacity-50 dark:bg-neutral-100 dark:text-neutral-900"
        >
          Gönder
        </button>
      </div>
    </div>
  );
}
