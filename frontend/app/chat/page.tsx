'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

import { GitBranch } from 'lucide-react';

import { ChatMessages, type ChatMessage } from '@/components/chat-messages';
import { ChatInput } from '@/components/chat-input';
import { SessionSidebar } from '@/components/session-sidebar';
import {
  forkSession,
  getSession,
  sendChat,
  UnauthenticatedError,
} from '@/lib/api-client';
import { clearSession, getCurrentUser, type AuthUser } from '@/lib/auth';

// Skip static prerender — page is gated on a localStorage token that
// only exists in the browser. Next 15.1's SSG path also has an internal
// `entryCSSFiles` bug with client components that consume next/navigation;
// force-dynamic avoids it.
export const dynamic = 'force-dynamic';

export default function ChatPage() {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [sessionId, setSessionId] = useState<string | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  // Bumped after every assistant reply so the sidebar re-fetches.
  const [sidebarVersion, setSidebarVersion] = useState(0);

  // Client-side route guard. Middleware can't see localStorage.
  useEffect(() => {
    const me = getCurrentUser();
    if (!me) {
      router.replace('/login');
      return;
    }
    setUser(me);
  }, [router]);

  const handleSelectSession = useCallback(
    async (id: string) => {
      if (id === sessionId) return;
      setError(null);
      try {
        const detail = await getSession(id);
        setSessionId(detail.id);
        setMessages(
          detail.messages.map((m) => ({ role: m.role, text: m.text })),
        );
      } catch (e) {
        if (e instanceof UnauthenticatedError) {
          router.replace('/login');
          return;
        }
        setError(e instanceof Error ? e.message : 'Sohbet yüklenemedi');
      }
    },
    [router, sessionId],
  );

  const handleNewSession = useCallback(() => {
    setSessionId(undefined);
    setMessages([]);
    setError(null);
  }, []);

  async function handleSend(text: string) {
    if (!text.trim() || isStreaming) return;
    setMessages((prev) => [...prev, { role: 'user', text }]);
    setIsStreaming(true);
    setError(null);

    let assistantBuffer = '';
    setMessages((prev) => [...prev, { role: 'assistant', text: '' }]);

    let createdNew = sessionId === undefined;

    try {
      for await (const event of sendChat(text, { sessionId })) {
        if (event.event === 'session' && typeof event.data.id === 'string') {
          setSessionId(event.data.id);
          createdNew = true;
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
      setMessages((prev) => prev.slice(0, -1));
    } finally {
      setIsStreaming(false);
      // Refresh sidebar after every assistant reply — new sessions appear,
      // existing ones move up due to updated_at.
      if (createdNew || sessionId) {
        setSidebarVersion((v) => v + 1);
      }
    }
  }

  async function handleFork() {
    if (!sessionId || isStreaming) return;
    setError(null);
    try {
      const child = await forkSession(sessionId);
      // Load the freshly forked session so the user immediately sees
      // the inherited history and can ask the next branching question.
      const detail = await getSession(child.id);
      setSessionId(detail.id);
      setMessages(detail.messages.map((m) => ({ role: m.role, text: m.text })));
      setSidebarVersion((v) => v + 1);
    } catch (e) {
      if (e instanceof UnauthenticatedError) {
        router.replace('/login');
        return;
      }
      setError(e instanceof Error ? e.message : 'Dallandırma başarısız');
    }
  }

  function handleLogout() {
    clearSession();
    router.push('/login');
  }

  if (!user) return null;

  return (
    <div className="flex h-screen">
      <SessionSidebar
        activeSessionId={sessionId}
        onSelect={handleSelectSession}
        onNew={handleNewSession}
        refreshKey={sidebarVersion}
        onUnauthenticated={() => router.replace('/login')}
      />
      <main className="mx-auto flex h-screen flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
          <div>
            <h1 className="text-lg font-semibold">Sohbet</h1>
            {sessionId && (
              <p className="text-xs text-neutral-500">
                oturum: {sessionId.slice(0, 8)}…
              </p>
            )}
          </div>
          <div className="flex items-center gap-4 text-sm">
            {sessionId && messages.length > 0 && (
              <button
                type="button"
                onClick={handleFork}
                disabled={isStreaming}
                title="Bu sohbeti buradan ikiye ayır"
                className="flex items-center gap-1 rounded-md border border-neutral-300 px-3 py-1 text-xs hover:bg-neutral-100 disabled:opacity-50 dark:border-neutral-700 dark:hover:bg-neutral-900"
              >
                <GitBranch size={12} />
                Dallandır
              </button>
            )}
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
    </div>
  );
}
