'use client';

import { useState } from 'react';
import { FileText, Loader2 } from 'lucide-react';
import clsx from 'clsx';

import { AttachmentPreview } from './attachment-preview';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

interface Props {
  messages: ChatMessage[];
  /** Live chain-of-thought from the reasoning model. Empty string = no
   *  thinking in progress. When non-empty, we render a collapsed panel
   *  above the next assistant bubble so the user can see the model is
   *  working rather than staring at silence. */
  thinking?: string;
}

interface Part {
  kind: 'text' | 'attachment';
  text: string;
}

/** Split a message into text parts and attachment parts. Attachments
 *  arrive wrapped in `<attached id="...">…</attached>` by the chat
 *  input's paste-as-chip path. We render them as cards instead of
 *  dumping the XML into the conversation. */
function splitParts(message: string): Part[] {
  const re = /<attached\s+id="[^"]*">\n?([\s\S]*?)\n?<\/attached>/g;
  const out: Part[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(message)) !== null) {
    if (match.index > cursor) {
      const head = message.slice(cursor, match.index).trim();
      if (head) out.push({ kind: 'text', text: head });
    }
    out.push({ kind: 'attachment', text: match[1] });
    cursor = match.index + match[0].length;
  }
  if (cursor < message.length) {
    const tail = message.slice(cursor).trim();
    if (tail) out.push({ kind: 'text', text: tail });
  }
  if (out.length === 0) out.push({ kind: 'text', text: message });
  return out;
}

function firstLine(text: string): string {
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (line) return line.slice(0, 80);
  }
  return 'Yapıştırılan metin';
}

export function ChatMessages({ messages, thinking = '' }: Props) {
  const [previewText, setPreviewText] = useState<string | null>(null);
  const [showThinking, setShowThinking] = useState(false);

  if (messages.length === 0 && !thinking) {
    return (
      <div className="flex flex-1 items-center justify-center text-neutral-400">
        Soru sorarak başlayın. Yüklediğiniz dokümanlardan cevap verir.
      </div>
    );
  }

  return (
    <>
      <div className="flex-1 space-y-4 overflow-y-auto px-6 py-4">
        {messages.map((m, idx) => {
          const parts = splitParts(m.text);
          const isUser = m.role === 'user';
          return (
            <div
              key={idx}
              className={clsx(
                'max-w-[80%] space-y-2 rounded-lg px-4 py-2 text-sm',
                isUser
                  ? 'ml-auto bg-neutral-900 text-white dark:bg-neutral-100 dark:text-neutral-900'
                  : 'mr-auto bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-100',
              )}
            >
              {parts.map((p, i) =>
                p.kind === 'text' ? (
                  <div key={i} className="whitespace-pre-wrap">
                    {p.text || <span className="opacity-50">…</span>}
                  </div>
                ) : (
                  <button
                    key={i}
                    type="button"
                    onClick={() => setPreviewText(p.text)}
                    className={clsx(
                      'group flex w-full max-w-sm items-start gap-2 rounded-md border px-3 py-2 text-left',
                      isUser
                        ? 'border-white/30 bg-white/10 hover:bg-white/20'
                        : 'border-neutral-300 bg-white hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-900 dark:hover:bg-neutral-800',
                    )}
                  >
                    <FileText size={18} className="mt-0.5 shrink-0 opacity-70" />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-xs font-medium">
                        {firstLine(p.text)}
                      </div>
                      <div className="text-[10px] opacity-60">
                        Yapıştırılan metin ·{' '}
                        {p.text.length.toLocaleString('tr-TR')} karakter ·{' '}
                        görüntüle
                      </div>
                    </div>
                  </button>
                ),
              )}
            </div>
          );
        })}
        {thinking && (
          <div className="mr-auto max-w-[80%]">
            <button
              type="button"
              onClick={() => setShowThinking((v) => !v)}
              className="flex items-center gap-2 rounded-md border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs text-neutral-600 hover:bg-neutral-100 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-400 dark:hover:bg-neutral-800"
            >
              <Loader2 size={14} className="animate-spin" />
              <span className="font-medium">Düşünüyor…</span>
              <span className="opacity-60">
                ({thinking.length.toLocaleString('tr-TR')} karakter
                {showThinking ? ' · gizle' : ' · göster'})
              </span>
            </button>
            {showThinking && (
              <pre className="mt-1 max-h-60 overflow-auto whitespace-pre-wrap break-words rounded-md border border-neutral-200 bg-neutral-50 p-3 text-xs italic text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-400">
                {thinking}
              </pre>
            )}
          </div>
        )}
      </div>
      {previewText !== null && (
        <AttachmentPreview
          text={previewText}
          onClose={() => setPreviewText(null)}
        />
      )}
    </>
  );
}
