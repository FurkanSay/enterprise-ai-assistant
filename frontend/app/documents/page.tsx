'use client';

import { ChangeEvent, useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import clsx from 'clsx';

import {
  listDocuments,
  uploadDocument,
  UnauthenticatedError,
  type DocumentSummary,
} from '@/lib/api-client';
import { clearSession, getCurrentUser, type AuthUser } from '@/lib/auth';

export const dynamic = 'force-dynamic';

/** Pulled out of the component so the polling effect can read the latest
 *  doc list without going through React's state updater (which Strict
 *  Mode invokes twice and double-schedules timers when used for side
 *  effects). */

const STATUS_BADGE: Record<DocumentSummary['status'], string> = {
  UPLOADED: 'bg-neutral-200 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300',
  PARSING: 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
  CHUNKING: 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
  EMBEDDING: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  READY: 'bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300',
  FAILED: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
};

/** Documents whose status isn't terminal — keep polling. */
function isPending(d: DocumentSummary): boolean {
  return d.status !== 'READY' && d.status !== 'FAILED';
}

export default function DocumentsPage() {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [docs, setDocs] = useState<DocumentSummary[]>([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Mirror of `docs` outside React state. The polling tick reads this
  // instead of using `setDocs(updater => ...)` to schedule the next
  // tick — Strict Mode invokes updaters twice and would queue two
  // overlapping setTimeouts.
  const docsRef = useRef<DocumentSummary[]>([]);

  // Route guard.
  useEffect(() => {
    const me = getCurrentUser();
    if (!me) {
      router.replace('/login');
      return;
    }
    setUser(me);
  }, [router]);

  const refresh = useCallback(async () => {
    try {
      const items = await listDocuments();
      docsRef.current = items;
      setDocs(items);
    } catch (e) {
      if (e instanceof UnauthenticatedError) {
        router.replace('/login');
        return;
      }
      setError(e instanceof Error ? e.message : 'Bilinmeyen hata');
    }
  }, [router]);

  // Initial load + adaptive polling: 2s while any doc is pending, otherwise idle.
  useEffect(() => {
    if (!user) return;
    let cancelled = false;

    async function tick() {
      if (cancelled) return;
      await refresh();
      if (cancelled) return;
      const anyPending = docsRef.current.some(isPending);
      if (anyPending) {
        pollTimerRef.current = setTimeout(tick, 2000);
      }
    }
    tick();
    return () => {
      cancelled = true;
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, [user, refresh]);

  async function handleUpload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || uploading) return;
    setUploading(true);
    setError(null);
    try {
      const created = await uploadDocument(file, file.name);
      // Optimistic insert; the next refresh will reconcile with the server.
      const next = [created, ...docsRef.current.filter((d) => d.id !== created.id)];
      docsRef.current = next;
      setDocs(next);
      // Kick polling immediately so the status badge updates fast.
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
      pollTimerRef.current = setTimeout(refresh, 500);
    } catch (e) {
      if (e instanceof UnauthenticatedError) {
        router.replace('/login');
        return;
      }
      setError(e instanceof Error ? e.message : 'Yükleme başarısız');
    } finally {
      setUploading(false);
    }
  }

  function handleLogout() {
    clearSession();
    router.push('/login');
  }

  if (!user) return null;

  return (
    <main className="mx-auto flex h-screen max-w-4xl flex-col">
      <header className="flex items-center justify-between border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
        <h1 className="text-lg font-semibold">Dokümanlar</h1>
        <div className="flex items-center gap-4 text-sm">
          <Link
            href="/chat"
            className="text-neutral-600 hover:text-neutral-900 dark:text-neutral-300 dark:hover:text-neutral-100"
          >
            Sohbet
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

      <section className="border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
        <label
          className={clsx(
            'flex cursor-pointer items-center justify-center gap-2 rounded-md border-2 border-dashed border-neutral-300 px-6 py-8 text-sm hover:bg-neutral-50 dark:border-neutral-700 dark:hover:bg-neutral-900',
            uploading && 'pointer-events-none opacity-60',
          )}
        >
          <input
            type="file"
            className="hidden"
            onChange={handleUpload}
            disabled={uploading}
          />
          <span>
            {uploading ? 'Yükleniyor…' : 'Bir dosya seçin veya buraya bırakın'}
          </span>
        </label>
        {error && (
          <p className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            {error}
          </p>
        )}
      </section>

      <ul className="flex-1 overflow-y-auto divide-y divide-neutral-200 dark:divide-neutral-800">
        {docs.length === 0 && (
          <li className="px-6 py-8 text-center text-sm text-neutral-500">
            Henüz doküman yok. Yukarıdan bir dosya yükleyin.
          </li>
        )}
        {docs.map((doc) => (
          <li
            key={doc.id}
            className="flex items-center justify-between px-6 py-3 text-sm"
          >
            <div className="min-w-0">
              <p className="truncate font-medium">{doc.title}</p>
              <p className="truncate text-xs text-neutral-500">
                {doc.originalFilename} · {formatBytes(doc.sizeBytes)} ·{' '}
                {doc.chunkCount} chunk
              </p>
            </div>
            <span
              className={clsx(
                'rounded-full px-2 py-0.5 text-xs font-medium',
                STATUS_BADGE[doc.status],
              )}
            >
              {doc.status}
            </span>
          </li>
        ))}
      </ul>
    </main>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1_048_576).toFixed(1)} MB`;
}
