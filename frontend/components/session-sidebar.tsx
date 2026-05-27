'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { GitBranch, MessageSquare, Trash2, Plus } from 'lucide-react';
import clsx from 'clsx';

import {
  deleteSession,
  listSessions,
  UnauthenticatedError,
  type SessionSummary,
} from '@/lib/api-client';

interface TreeNode {
  session: SessionSummary;
  children: TreeNode[];
}

function buildTree(sessions: SessionSummary[]): TreeNode[] {
  const byId = new Map<string, TreeNode>();
  for (const s of sessions) byId.set(s.id, { session: s, children: [] });
  const roots: TreeNode[] = [];
  for (const node of byId.values()) {
    const parent = node.session.parent_session_id
      ? byId.get(node.session.parent_session_id) ?? null
      : null;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  // Newest first at each level.
  const sortBranch = (nodes: TreeNode[]) => {
    nodes.sort((a, b) =>
      b.session.updated_at.localeCompare(a.session.updated_at),
    );
    for (const n of nodes) sortBranch(n.children);
  };
  sortBranch(roots);
  return roots;
}

interface Props {
  activeSessionId: string | undefined;
  onSelect: (sessionId: string) => void;
  onNew: () => void;
  /** Bump this counter from the parent (e.g. after a chat reply) to force
   *  a refresh of the session list without re-mounting the sidebar. */
  refreshKey?: number;
  /** Called when the auth token is no longer valid. */
  onUnauthenticated: () => void;
}

export function SessionSidebar({
  activeSessionId,
  onSelect,
  onNew,
  refreshKey = 0,
  onUnauthenticated,
}: Props) {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Local optimistic state for deletions so the row disappears
  // immediately, before the server round-trip completes.
  const deletingRef = useRef<Set<string>>(new Set());

  const refresh = useCallback(async () => {
    setError(null);
    try {
      const items = await listSessions(50);
      setSessions(items);
    } catch (e) {
      if (e instanceof UnauthenticatedError) {
        onUnauthenticated();
        return;
      }
      setError(e instanceof Error ? e.message : 'Bilinmeyen hata');
    } finally {
      setLoading(false);
    }
  }, [onUnauthenticated]);

  useEffect(() => {
    refresh();
  }, [refresh, refreshKey]);

  const tree = useMemo(() => buildTree(sessions), [sessions]);

  // Flatten the tree into an ordered list of (node, depth) pairs so the
  // render loop stays simple — no recursion in the JSX. depth controls
  // indentation; the connector glyph distinguishes child sessions.
  function renderTree(nodes: TreeNode[], depth: number): React.ReactNode[] {
    const out: React.ReactNode[] = [];
    for (const node of nodes) {
      const s = node.session;
      const isFork = depth > 0;
      out.push(
        <button
          key={s.id}
          type="button"
          onClick={() => onSelect(s.id)}
          style={{ paddingLeft: 12 + depth * 14 }}
          className={clsx(
            'group flex w-full items-center gap-2 py-2 pr-3 text-left text-sm hover:bg-neutral-100 dark:hover:bg-neutral-800',
            s.id === activeSessionId && 'bg-neutral-200 dark:bg-neutral-800',
          )}
          title={
            isFork
              ? `Bir önceki sohbetten dallandı (${s.parent_session_id?.slice(0, 8)}…)`
              : undefined
          }
        >
          {isFork ? (
            <GitBranch size={14} className="shrink-0 text-amber-500" />
          ) : (
            <MessageSquare size={14} className="shrink-0 text-neutral-400" />
          )}
          <span className="min-w-0 flex-1 truncate">
            {s.title || s.id.slice(0, 8)}
          </span>
          <Trash2
            size={14}
            className="hidden text-neutral-400 hover:text-red-500 group-hover:block"
            onClick={(e) => handleDelete(s.id, e)}
            aria-label="Sohbeti sil"
          />
        </button>,
      );
      if (node.children.length > 0) {
        out.push(...renderTree(node.children, depth + 1));
      }
    }
    return out;
  }

  async function handleDelete(id: string, e: React.MouseEvent) {
    e.stopPropagation();
    if (deletingRef.current.has(id)) return;
    deletingRef.current.add(id);
    // Optimistic removal
    setSessions((prev) => prev.filter((s) => s.id !== id));
    try {
      await deleteSession(id);
      if (activeSessionId === id) onNew(); // close the deleted conversation
    } catch (e) {
      // Rollback: re-fetch to get authoritative list.
      refresh();
    } finally {
      deletingRef.current.delete(id);
    }
  }

  return (
    <aside className="flex h-screen w-64 shrink-0 flex-col border-r border-neutral-200 bg-neutral-50 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="flex items-center justify-between border-b border-neutral-200 px-3 py-3 dark:border-neutral-800">
        <span className="text-xs font-semibold uppercase tracking-wider text-neutral-500">
          Sohbetler
        </span>
        <button
          type="button"
          onClick={onNew}
          title="Yeni sohbet"
          className="rounded p-1 text-neutral-600 hover:bg-neutral-200 dark:text-neutral-300 dark:hover:bg-neutral-800"
        >
          <Plus size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading && (
          <div className="p-3 text-xs text-neutral-500">Yükleniyor…</div>
        )}
        {error && (
          <div className="m-2 rounded-md bg-red-50 px-2 py-1 text-xs text-red-700 dark:bg-red-950 dark:text-red-300">
            {error}
          </div>
        )}
        {!loading && sessions.length === 0 && !error && (
          <div className="p-3 text-xs text-neutral-500">
            Henüz sohbet yok. Bir soru sorarak başlayın.
          </div>
        )}
        {renderTree(tree, 0)}
      </div>
    </aside>
  );
}
