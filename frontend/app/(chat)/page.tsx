'use client';

import { useState } from 'react';
import { ChatMessages, type ChatMessage } from '@/components/chat-messages';
import { ChatInput } from '@/components/chat-input';
import { DocumentUpload } from '@/components/document-upload';
import { sendChat } from '@/lib/api-client';

export default function ChatPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);

  async function handleSend(text: string) {
    if (!text.trim() || isStreaming) return;

    setMessages((prev) => [...prev, { role: 'user', text }]);
    setIsStreaming(true);

    try {
      let assistantBuffer = '';
      setMessages((prev) => [...prev, { role: 'assistant', text: '' }]);

      for await (const event of sendChat(text)) {
        if (event.event === 'token') {
          assistantBuffer += event.data.text ?? '';
          setMessages((prev) => {
            const next = [...prev];
            next[next.length - 1] = { role: 'assistant', text: assistantBuffer };
            return next;
          });
        }
      }
    } finally {
      setIsStreaming(false);
    }
  }

  return (
    <main className="mx-auto flex h-screen max-w-4xl flex-col">
      <header className="flex items-center justify-between border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
        <h1 className="text-lg font-semibold">Sohbet</h1>
        <DocumentUpload />
      </header>
      <ChatMessages messages={messages} />
      <ChatInput onSend={handleSend} disabled={isStreaming} />
    </main>
  );
}
