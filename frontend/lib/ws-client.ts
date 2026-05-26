/**
 * WebSocket client — connects to the Realtime service for token streams
 * and document status events.
 */

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? 'ws://localhost:8085';

export interface RealtimeEvent {
  type: 'token' | 'doc_ready' | 'doc_failed' | string;
  [key: string]: unknown;
}

export function connectRealtime(
  sessionId: string,
  onEvent: (event: RealtimeEvent) => void,
): WebSocket {
  const url = `${WS_URL}/ws?session=${encodeURIComponent(sessionId)}`;
  const ws = new WebSocket(url);

  ws.addEventListener('message', (msg) => {
    try {
      onEvent(JSON.parse(msg.data));
    } catch {
      /* skip malformed */
    }
  });

  return ws;
}
