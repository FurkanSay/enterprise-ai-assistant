/**
 * Backend API client — talks to the Gateway, never to internal services
 * directly. Every protected request is Bearer-authenticated via the
 * token saved at login (see ./auth.ts).
 *
 * SSE chat stream is parsed event-by-event via the Fetch streams API,
 * yielded as an async iterable.
 */

import { getAccessToken, clearSession } from './auth';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class UnauthenticatedError extends Error {
  constructor() {
    super('Not authenticated');
    this.name = 'UnauthenticatedError';
  }
}

export interface ChatStreamEvent {
  event:
    | 'token'
    | 'thinking'
    | 'tool_use'
    | 'tool_result'
    | 'usage'
    | 'done'
    | 'session'
    | 'error';
  data: Record<string, unknown> & { text?: string; id?: string };
}

function authHeaders(): HeadersInit {
  const token = getAccessToken();
  if (!token) throw new UnauthenticatedError();
  return { Authorization: `Bearer ${token}` };
}

async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { ...(init.headers ?? {}), ...authHeaders() },
  });
  if (res.status === 401) {
    clearSession();
    throw new UnauthenticatedError();
  }
  return res;
}

// ── Auth (anonymous endpoints) ──────────────────────────────────────────

export async function login(email: string, password: string): Promise<Response> {
  return fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
}

export async function register(
  tenantId: string,
  email: string,
  password: string,
  displayName: string,
): Promise<Response> {
  return fetch(`${API_BASE}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenantId, email, password, displayName }),
  });
}

export async function fetchMe(): Promise<{
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  isActive: boolean;
}> {
  const res = await authedFetch(`/api/v1/auth/me`);
  if (!res.ok) throw new Error(`/me failed: ${res.status}`);
  return res.json();
}

// ── Documents ───────────────────────────────────────────────────────────

export interface DocumentSummary {
  id: string;
  title: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  status: 'UPLOADED' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'READY' | 'FAILED';
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
}

export async function listDocuments(): Promise<DocumentSummary[]> {
  const res = await authedFetch(`/api/v1/documents`);
  if (!res.ok) throw new Error(`list documents failed: ${res.status}`);
  const body = await res.json();
  return body.items ?? [];
}

export async function uploadDocument(
  file: File,
  title?: string,
): Promise<DocumentSummary> {
  const form = new FormData();
  form.append('file', file);
  if (title) form.append('title', title);
  const res = await authedFetch(`/api/v1/documents`, { method: 'POST', body: form });
  if (!res.ok) throw new Error(`upload failed: ${res.status}`);
  return res.json();
}

// ── Sessions ────────────────────────────────────────────────────────────

export interface SessionSummary {
  id: string;
  title: string;
  model: string;
  message_count: number;
  created_at: string;
  updated_at: string;
  parent_session_id?: string | null;
  forked_from_message_id?: string | null;
  mode?: 'normal' | 'deep_search';
}

export interface SessionMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  sequence_number: number;
  created_at: string;
}

export interface SessionDetail extends SessionSummary {
  messages: SessionMessage[];
}

export async function listSessions(limit = 20): Promise<SessionSummary[]> {
  const res = await authedFetch(`/api/v1/sessions?limit=${limit}`);
  if (!res.ok) throw new Error(`list sessions failed: ${res.status}`);
  return res.json();
}

export async function getSession(sessionId: string): Promise<SessionDetail> {
  const res = await authedFetch(`/api/v1/sessions/${sessionId}`);
  if (!res.ok) throw new Error(`get session failed: ${res.status}`);
  return res.json();
}

export async function forkSession(
  sessionId: string,
  upToMessageId?: string,
): Promise<SessionSummary> {
  const body = upToMessageId ? { up_to_message_id: upToMessageId } : {};
  const res = await authedFetch(`/api/v1/sessions/${sessionId}/fork`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`fork session failed: ${res.status}`);
  return res.json();
}

export async function deleteSession(sessionId: string): Promise<void> {
  const res = await authedFetch(`/api/v1/sessions/${sessionId}`, {
    method: 'DELETE',
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`delete session failed: ${res.status}`);
  }
}

export async function deleteDocument(documentId: string): Promise<void> {
  const res = await authedFetch(`/api/v1/documents/${documentId}`, {
    method: 'DELETE',
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`delete document failed: ${res.status}`);
  }
}

// ── Chat ────────────────────────────────────────────────────────────────

export async function* sendChat(
  message: string,
  options: {
    sessionId?: string;
    model?: string;
    mode?: 'normal' | 'deep_search';
  } = {},
): AsyncGenerator<ChatStreamEvent> {
  const response = await fetch(`${API_BASE}/api/v1/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authHeaders(),
    },
    body: JSON.stringify({
      message,
      mode: options.mode ?? 'normal',
      session_id: options.sessionId,
      model: options.model,
    }),
  });

  if (response.status === 401) {
    clearSession();
    throw new UnauthenticatedError();
  }
  if (!response.ok || !response.body) {
    throw new Error(`Chat request failed: ${response.status}`);
  }

  // Spec-friendly SSE parser:
  //   - frames are separated by a blank line (CRLF or LF tolerated)
  //   - inside a frame, multiple `data:` lines must be joined with '\n'
  //     per the EventSource spec.
  // The earlier regex-only version captured a single line and tripped on
  // chunked frames that arrived split across two reader reads.
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value;
    // Normalise CRLF → LF so the split below catches both.
    buffer = buffer.replace(/\r\n/g, '\n');

    let separatorIndex: number;
    while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);

      let eventName: string | undefined;
      const dataLines: string[] = [];
      for (const line of frame.split('\n')) {
        if (line.startsWith('event: ')) {
          eventName = line.slice(7).trim();
        } else if (line.startsWith('data: ')) {
          dataLines.push(line.slice(6));
        } else if (line.startsWith(':')) {
          // SSE comment / keep-alive — ignore.
        }
      }
      if (!eventName || dataLines.length === 0) continue;
      try {
        yield {
          event: eventName as ChatStreamEvent['event'],
          data: JSON.parse(dataLines.join('\n')),
        };
      } catch {
        // skip malformed
      }
    }
  }
}
