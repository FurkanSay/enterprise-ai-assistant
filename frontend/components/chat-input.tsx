'use client';

import { useState } from 'react';

export function ChatInput({
  onSend,
  disabled,
}: {
  onSend: (text: string) => void;
  disabled?: boolean;
}) {
  const [text, setText] = useState('');

  function submit() {
    if (!text.trim() || disabled) return;
    onSend(text);
    setText('');
  }

  return (
    <div className="border-t border-neutral-200 p-4 dark:border-neutral-800">
      <div className="flex gap-2">
        <textarea
          rows={1}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
          placeholder="Soru yazın…"
          className="flex-1 resize-none rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-neutral-400 dark:border-neutral-700 dark:bg-neutral-900"
          disabled={disabled}
        />
        <button
          onClick={submit}
          disabled={disabled || !text.trim()}
          className="rounded-md bg-neutral-900 px-4 text-sm font-medium text-white disabled:opacity-50 dark:bg-neutral-100 dark:text-neutral-900"
        >
          Gönder
        </button>
      </div>
    </div>
  );
}
