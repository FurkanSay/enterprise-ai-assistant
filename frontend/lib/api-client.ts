/**
 * Backend API client — talks to the Gateway, never to internal services
 * directly. Every protected request is Bearer-authenticated via the
 * token saved at login (see ./auth.ts).
 *
 * SSE chat stream is parsed event-by-event via the Fetch streams API,
 * yielded as an async iterable.
 */

import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  updateTokens,
} from './auth';

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class UnauthenticatedError extends Error {
  constructor() {
    super('Not authenticated');
    this.name = 'UnauthenticatedError';
  }
}

// In-flight refresh promise so 5 simultaneous 401s share ONE network
// round-trip. Without this, the first refresh rotates the token, the
// other four hit the now-revoked one and bounce the user to /login.
let pendingRefresh: Promise<string | null> | null = null;

/**
 * Single-flight refresh. Returns the new access token, or null if the
 * refresh itself failed (revoked, expired, or no refresh token stored).
 * On failure, the session is wiped — the caller should treat this as
 * "user is logged out."
 */
async function refreshAccessToken(): Promise<string | null> {
  if (pendingRefresh) return pendingRefresh;
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  pendingRefresh = (async (): Promise<string | null> => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) {
        clearSession();
        return null;
      }
      const body = await res.json();
      if (!body.accessToken || !body.refreshToken) {
        clearSession();
        return null;
      }
      updateTokens(body.accessToken, body.refreshToken);
      return body.accessToken as string;
    } catch {
      clearSession();
      return null;
    } finally {
      pendingRefresh = null;
    }
  })();

  return pendingRefresh;
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

/**
 * Run a request, and if it 401s once, try refreshing and re-running ONCE.
 * Streaming requests pass `init.body` as a ReadableStream or FormData,
 * neither of which are consumed twice safely — for those, callers should
 * use the dedicated SSE path (which handles 401 itself).
 */
async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const exec = async (token: string): Promise<Response> =>
    fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        ...(init.headers ?? {}),
        Authorization: `Bearer ${token}`,
      },
    });

  const token = getAccessToken();
  if (!token) throw new UnauthenticatedError();

  let res = await exec(token);
  if (res.status !== 401) return res;

  // Try refreshing exactly once. If it succeeds, retry the same request
  // with the new token; if it fails, surface UnauthenticatedError so
  // page guards can redirect to /login.
  const refreshed = await refreshAccessToken();
  if (!refreshed) {
    clearSession();
    throw new UnauthenticatedError();
  }
  res = await exec(refreshed);
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

/**
 * Best-effort logout — revokes the refresh token server-side. Network
 * errors are swallowed because the caller still wants the local session
 * gone regardless. Idempotent on the server.
 */
export async function logout(refreshToken: string): Promise<void> {
  try {
    await fetch(`${API_BASE}/api/v1/auth/logout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    // ignore — local clearSession() in the caller still happens
  }
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
  // Populated when the document came from Deep Search auto-ingest.
  sourceSessionId?: string | null;
  sourcePaperDoi?: string | null;
  sourcePaperTitle?: string | null;
}

export async function listDocuments(
  options: { sourceSessionId?: string } = {},
): Promise<DocumentSummary[]> {
  const params = new URLSearchParams();
  if (options.sourceSessionId) {
    params.set('source_session_id', options.sourceSessionId);
  }
  const qs = params.toString();
  const res = await authedFetch(`/api/v1/documents${qs ? '?' + qs : ''}`);
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

export interface SessionToolResult {
  kind: string; // "paper_list" | "paper_ingested"
  data: Record<string, unknown>;
}

export interface SessionMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  sequence_number: number;
  created_at: string;
  tool_results?: SessionToolResult[];
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
  const body = JSON.stringify({
    message,
    mode: options.mode ?? 'normal',
    session_id: options.sessionId,
    model: options.model,
  });
  const post = (token: string) =>
    fetch(`${API_BASE}/api/v1/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`,
      },
      body,
    });

  const token = getAccessToken();
  if (!token) throw new UnauthenticatedError();
  let response = await post(token);
  if (response.status === 401) {
    // Try one refresh, then retry with the fresh access token.
    const refreshed = await refreshAccessToken();
    if (!refreshed) {
      clearSession();
      throw new UnauthenticatedError();
    }
    response = await post(refreshed);
    if (response.status === 401) {
      clearSession();
      throw new UnauthenticatedError();
    }
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
