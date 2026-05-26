/**
 * Backend API client — talks to the Gateway, never to internal services directly.
 *
 * SSE chat stream is parsed event-by-event using the Fetch streams API.
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export interface ChatStreamEvent {
  event: 'token' | 'tool_use' | 'tool_result' | 'usage' | 'done' | 'error';
  data: Record<string, unknown> & { text?: string };
}

/**
 * Send a chat message and stream the SSE response back as an async iterable.
 * Each `yield` is a structured event { event, data }.
 */
export async function* sendChat(
  message: string,
  options: { sessionId?: string; model?: string } = {},
): AsyncGenerator<ChatStreamEvent> {
  const response = await fetch(`${API_BASE}/api/v1/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ message, session_id: options.sessionId, model: options.model }),
    credentials: 'include',
  });

  if (!response.ok || !response.body) {
    throw new Error(`Chat request failed: ${response.status}`);
  }

  const reader = response.body
    .pipeThrough(new TextDecoderStream())
    .getReader();

  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += value;
    const parts = buffer.split('\n\n');
    buffer = parts.pop() ?? '';

    for (const part of parts) {
      const eventMatch = /^event: (\w+)$/m.exec(part);
      const dataMatch = /^data: (.*)$/m.exec(part);
      if (!eventMatch || !dataMatch) continue;

      try {
        yield {
          event: eventMatch[1] as ChatStreamEvent['event'],
          data: JSON.parse(dataMatch[1]),
        };
      } catch {
        // skip malformed event
      }
    }
  }
}

export async function uploadDocument(file: File): Promise<{ id: string }> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE}/api/v1/documents`, {
    method: 'POST',
    body: formData,
    credentials: 'include',
  });

  if (!response.ok) throw new Error(`Upload failed: ${response.status}`);
  return response.json();
}
