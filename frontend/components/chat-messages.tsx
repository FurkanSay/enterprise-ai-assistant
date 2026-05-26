'use client';

import clsx from 'clsx';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

export function ChatMessages({ messages }: { messages: ChatMessage[] }) {
  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center text-neutral-400">
        Soru sorarak başlayın. Yüklediğiniz dokümanlardan cevap verir.
      </div>
    );
  }

  return (
    <div className="flex-1 space-y-4 overflow-y-auto px-6 py-4">
      {messages.map((m, idx) => (
        <div
          key={idx}
          className={clsx(
            'max-w-[80%] rounded-lg px-4 py-2 text-sm',
            m.role === 'user'
              ? 'ml-auto bg-neutral-900 text-white dark:bg-neutral-100 dark:text-neutral-900'
              : 'mr-auto bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-100',
          )}
        >
          {m.text || <span className="opacity-50">…</span>}
        </div>
      ))}
    </div>
  );
}
