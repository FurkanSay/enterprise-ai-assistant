'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

import { ChatMessages, type ChatMessage } from '@/components/chat-messages';
import { ChatInput } from '@/components/chat-input';
import { sendChat, UnauthenticatedError } from '@/lib/api-client';
import { clearSession, getCurrentUser, type AuthUser } from '@/lib/auth';

// Skip static prerender — the page is gated on a localStorage token that
// only exists in the browser. Next 15.1's SSG path also has an internal
// `entryCSSFiles` bug with client components that consume next/navigation;
// force-dynamic avoids the bug by deferring render to request time.
export const dynamic = 'force-dynamic';

export default function ChatPage() {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [sessionId, setSessionId] = useState<string | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  // Client-side route guard. Server-side middleware on a token in
  // localStorage isn't possible — the middleware doesn't see localStorage.
  useEffect(() => {
    const me = getCurrentUser();
    if (!me) {
      router.replace('/login');
      return;
    }
    setUser(me);
  }, [router]);

  async function handleSend(text: string) {
    if (!text.trim() || isStreaming) return;

    setMessages((prev) => [...prev, { role: 'user', text }]);
    setIsStreaming(true);
    setError(null);

    let assistantBuffer = '';
    setMessages((prev) => [...prev, { role: 'assistant', text: '' }]);

    try {
      for await (const event of sendChat(text, { sessionId })) {
        if (event.event === 'session' && typeof event.data.id === 'string') {
          // First user message creates the session; pin it so the next
          // message resumes the same conversation thread.
          setSessionId(event.data.id);
        } else if (event.event === 'token') {
          assistantBuffer += event.data.text ?? '';
          setMessages((prev) => {
            const next = [...prev];
            next[next.length - 1] = { role: 'assistant', text: assistantBuffer };
            return next;
          });
        }
      }
    } catch (e) {
      if (e instanceof UnauthenticatedError) {
        router.replace('/login');
        return;
      }
      setError(e instanceof Error ? e.message : 'Bilinmeyen hata');
      setMessages((prev) => prev.slice(0, -1)); // drop the empty assistant placeholder
    } finally {
      setIsStreaming(false);
    }
  }

  function handleLogout() {
    clearSession();
    router.push('/login');
  }

  if (!user) {
    return null; // redirect in flight
  }

  return (
    <main className="mx-auto flex h-screen max-w-4xl flex-col">
      <header className="flex items-center justify-between border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
        <div>
          <h1 className="text-lg font-semibold">Sohbet</h1>
          {sessionId && (
            <p className="text-xs text-neutral-500">oturum: {sessionId.slice(0, 8)}…</p>
          )}
        </div>
        <div className="flex items-center gap-4 text-sm">
          <Link
            href="/documents"
            className="text-neutral-600 hover:text-neutral-900 dark:text-neutral-300 dark:hover:text-neutral-100"
          >
            Dokümanlar
          </Link>
          <span className="text-neutral-500">{user.email}</span>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-md border border-neutral-300 px-3 py-1 text-xs hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
          >
            Çıkış
          </button>
        </div>
      </header>
      {error && (
        <div className="border-b border-red-200 bg-red-50 px-6 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
          {error}
        </div>
      )}
      <ChatMessages messages={messages} />
      <ChatInput onSend={handleSend} disabled={isStreaming} />
    </main>
  );
}
