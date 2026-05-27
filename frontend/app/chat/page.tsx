'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

import { GitBranch } from 'lucide-react';

import { ChatMessages, type ChatMessage } from '@/components/chat-messages';
import { ChatInput } from '@/components/chat-input';
import { SessionSidebar } from '@/components/session-sidebar';
import type { Paper } from '@/components/paper-card';
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
  const [mode, setMode] = useState<'normal' | 'deep_search'>('normal');
  const [error, setError] = useState<string | null>(null);
  // Live chain-of-thought from reasoning models (Nemotron, DeepSeek-R1).
  // Rendered in a separate "düşünüyor" panel while the LLM cogitates
  // BEFORE producing visible text — without this, large prompts looked
  // like "no streaming" because the visible token stream lagged 20-40s.
  const [thinking, setThinking] = useState<string>('');
  // Bumped after every assistant reply so the sidebar re-fetches.
  const [sidebarVersion, setSidebarVersion] = useState(0);
  // Per-paper ingest status, keyed by paperKey (doi || arxiv_id || source_id).
  const [ingestStatus, setIngestStatus] = useState<
    Record<string, 'idle' | 'pending' | 'done' | 'error'>
  >({});

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
        // Lock the mode to whatever this session was born with — the
        // backend would reject mismatches anyway, but keeping the UI
        // honest avoids confused users.
        setMode((detail.mode as 'normal' | 'deep_search') || 'normal');
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
    setMode('normal');
  }, []);

  function paperKey(p: Paper): string {
    return p.doi || p.arxiv_id || p.source_id;
  }

  function handleIngestPaper(paper: Paper) {
    const key = paperKey(paper);
    if (ingestStatus[key] === 'pending' || ingestStatus[key] === 'done') return;
    setIngestStatus((prev) => ({ ...prev, [key]: 'pending' }));
    // Compose a deterministic follow-up turn that the model resolves
    // into an `ingest_paper` tool call. We pass the full Paper record
    // as inline JSON so the model has everything it needs (no extra
    // round-trip), and prefix with a clear instruction.
    const payload = JSON.stringify(paper);
    handleSend(
      `Lütfen şu makaleyi RAG koleksiyonuma ekle (ingest_paper):\n${payload}`,
    );
  }

  function handleToggleDeepSearch() {
    // Mode change always starts a fresh session so the toolset +
    // system prompt stay consistent within one conversation.
    const target = mode === 'deep_search' ? 'normal' : 'deep_search';
    if (sessionId && messages.length > 0) {
      const proceed = window.confirm(
        target === 'deep_search'
          ? 'Deep Search modu için yeni bir sohbet açılacak. Devam et?'
          : 'Normal moda dönmek için yeni bir sohbet açılacak. Devam et?',
      );
      if (!proceed) return;
    }
    setSessionId(undefined);
    setMessages([]);
    setError(null);
    setMode(target);
  }

  // Pseudo-streaming buffer.
  // Some providers (Nemotron-3 free tier, certain OpenRouter routes)
  // batch the entire response into one or two large chunks instead of
  // sending real per-token deltas — the user sees no visible typing,
  // just a sudden wall of text after a long pause. This buffer + tick
  // approach decouples the display rate from the server's chunk rate:
  // characters drain at a fixed cadence regardless of how the bytes
  // arrive. When the stream is truly token-by-token (small models),
  // the buffer stays near-empty and the cadence is invisible.
  const pendingRef = useRef<string>('');
  const renderedRef = useRef<string>('');
  const tickHandleRef = useRef<ReturnType<typeof setInterval> | null>(null);

  function startTypewriter() {
    if (tickHandleRef.current) return;
    // 35 chars / 25ms ≈ 1400 chars/sec — fast enough to feel responsive
    // on long answers, slow enough to register as "typing" to the eye.
    tickHandleRef.current = setInterval(() => {
      if (pendingRef.current.length === 0) return;
      const chunk = pendingRef.current.slice(0, 35);
      pendingRef.current = pendingRef.current.slice(35);
      renderedRef.current += chunk;
      const rendered = renderedRef.current;
      setMessages((prev) => {
        const next = [...prev];
        next[next.length - 1] = { role: 'assistant', text: rendered };
        return next;
      });
    }, 25);
  }

  function stopTypewriter() {
    if (tickHandleRef.current) {
      clearInterval(tickHandleRef.current);
      tickHandleRef.current = null;
    }
  }

  async function handleSend(text: string) {
    if (!text.trim() || isStreaming) return;
    setMessages((prev) => [...prev, { role: 'user', text }]);
    setIsStreaming(true);
    setError(null);
    setThinking('');

    let thinkingBuffer = '';
    pendingRef.current = '';
    renderedRef.current = '';
    setMessages((prev) => [...prev, { role: 'assistant', text: '' }]);
    startTypewriter();

    let createdNew = sessionId === undefined;

    try {
      for await (const event of sendChat(text, { sessionId, mode })) {
        if (event.event === 'session' && typeof event.data.id === 'string') {
          setSessionId(event.data.id);
          createdNew = true;
        } else if (event.event === 'tool_result') {
          // Surface the structured tool output on the active assistant
          // bubble so chat-messages can render paper cards / ingest
          // confirmations next to the answer text.
          let parsed: unknown = null;
          try {
            parsed = JSON.parse((event.data as { output?: string }).output ?? '');
          } catch {
            parsed = null;
          }
          if (parsed && typeof parsed === 'object') {
            const payload = parsed as {
              ui_kind?: string;
              doi?: string;
              arxiv_id?: string;
              source_id?: string;
            };
            if (
              payload.ui_kind === 'paper_list' ||
              payload.ui_kind === 'paper_ingested'
            ) {
              setMessages((prev) => {
                const next = [...prev];
                const last = next[next.length - 1];
                if (last && last.role === 'assistant') {
                  const tools = [...(last.toolResults ?? [])];
                  tools.push({ kind: payload.ui_kind!, data: payload });
                  next[next.length - 1] = { ...last, toolResults: tools };
                }
                return next;
              });
              if (payload.ui_kind === 'paper_ingested') {
                const k =
                  payload.doi || payload.arxiv_id || payload.source_id || '';
                if (k) {
                  setIngestStatus((prev) => ({ ...prev, [k]: 'done' }));
                }
              }
            }
          }
        } else if (event.event === 'thinking') {
          thinkingBuffer += event.data.text ?? '';
          setThinking(thinkingBuffer);
        } else if (event.event === 'token') {
          // First real content delta — drop the thinking panel.
          if (thinkingBuffer) {
            thinkingBuffer = '';
            setThinking('');
          }
          // Queue, do NOT flush immediately. The typewriter drains it.
          pendingRef.current += event.data.text ?? '';
        }
      }
      // Drain whatever is left at full speed.
      while (pendingRef.current.length > 0) {
        await new Promise((r) => setTimeout(r, 20));
      }
    } catch (e) {
      if (e instanceof UnauthenticatedError) {
        router.replace('/login');
        return;
      }
      setError(e instanceof Error ? e.message : 'Bilinmeyen hata');
      setMessages((prev) => prev.slice(0, -1));
    } finally {
      stopTypewriter();
      // Final flush in case some chars sat between the last tick and
      // the loop exit.
      if (pendingRef.current.length > 0) {
        renderedRef.current += pendingRef.current;
        pendingRef.current = '';
        const finalText = renderedRef.current;
        setMessages((prev) => {
          const next = [...prev];
          next[next.length - 1] = { role: 'assistant', text: finalText };
          return next;
        });
      }
      setIsStreaming(false);
      setThinking('');
      // Refresh sidebar after every assistant reply — new sessions appear,
      // existing ones move up due to updated_at.
      if (createdNew || sessionId) {
        setSidebarVersion((v) => v + 1);
      }
    }
  }

  // Stop the typewriter on unmount to avoid stale timers.
  useEffect(() => {
    return () => stopTypewriter();
  }, []);

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
            <h1 className="flex items-center gap-2 text-lg font-semibold">
              Sohbet
              {mode === 'deep_search' && (
                <span className="rounded-md bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-800 dark:bg-purple-950 dark:text-purple-300">
                  Deep Search
                </span>
              )}
            </h1>
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
        <ChatMessages
          messages={messages}
          thinking={thinking}
          onIngestPaper={handleIngestPaper}
          ingestStatus={ingestStatus}
        />
        <ChatInput
          onSend={handleSend}
          disabled={isStreaming}
          mode={mode}
          onToggleDeepSearch={handleToggleDeepSearch}
        />
      </main>
    </div>
  );
}
