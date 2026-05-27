'use client';

import { ClipboardEvent, useState } from 'react';
import { FileText, X } from 'lucide-react';

import { AttachmentPreview } from './attachment-preview';

/** Threshold (chars) above which a paste becomes an attachment card
 *  rather than dumping the whole blob into the textarea. */
const PASTE_AS_ATTACHMENT_CHARS = 280;

interface PasteAttachment {
  id: number;
  text: string;
  kind: string;
}

/** First non-empty line of the pasted text, used as the card title.
 *  Keeps the chip readable when the user pastes a long doc whose first
 *  line is actually meaningful (a code file path, an email subject, a
 *  resume header line). */
function attachmentTitle(text: string): string {
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (line.length > 0) return line.slice(0, 80);
  }
  return 'Yapıştırılan metin';
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
  const [previewText, setPreviewText] = useState<string | null>(null);

  function submit() {
    const trimmed = text.trim();
    if ((!trimmed && attachments.length === 0) || disabled) return;

    // Compose: attachments first as fenced context blocks, user prompt
    // last. The fenced shape keeps the model from confusing pasted
    // content with the actual instruction.
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
    if (pasted.length <= PASTE_AS_ATTACHMENT_CHARS) return;
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
    <>
      <div className="border-t border-neutral-200 p-4 dark:border-neutral-800">
        {attachments.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-2">
            {attachments.map((att) => (
              <div
                key={att.id}
                className="group relative flex max-w-xs items-start gap-2 rounded-lg border border-neutral-300 bg-neutral-50 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-800"
              >
                <button
                  type="button"
                  onClick={() => setPreviewText(att.text)}
                  className="flex flex-1 cursor-pointer items-start gap-2 text-left"
                  aria-label="İçeriği görüntüle"
                >
                  <FileText
                    size={20}
                    className="mt-0.5 shrink-0 text-neutral-500 group-hover:text-neutral-700 dark:group-hover:text-neutral-300"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-xs font-medium">
                      {attachmentTitle(att.text)}
                    </div>
                    <div className="text-[10px] text-neutral-500">
                      Yapıştırılan metin · {att.text.length.toLocaleString('tr-TR')}{' '}
                      karakter
                    </div>
                  </div>
                </button>
                <button
                  type="button"
                  onClick={() => removeAttachment(att.id)}
                  aria-label="Kaldır"
                  className="shrink-0 rounded p-1 text-neutral-400 hover:bg-neutral-200 hover:text-neutral-700 dark:hover:bg-neutral-700 dark:hover:text-neutral-200"
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
                ? 'Soru ekleyin (Enter ile gönder)…'
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
      {previewText !== null && (
        <AttachmentPreview text={previewText} onClose={() => setPreviewText(null)} />
      )}
    </>
  );
}
