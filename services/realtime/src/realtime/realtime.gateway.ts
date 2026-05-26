import {
  OnGatewayConnection,
  OnGatewayDisconnect,
  OnGatewayInit,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Logger } from '@nestjs/common';
import { IncomingMessage } from 'http';
import { WebSocket, Server } from 'ws';

interface AuthenticatedSocket extends WebSocket {
  tenantId?: string;
  userId?: string;
  sessionId?: string;
}

/**
 * WebSocket gateway — single channel per (tenantId, userId, sessionId).
 *
 * The frontend connects with:  ws://gateway/ws?session={sessionId}
 * Auth is via cookie/Bearer header (forwarded by gateway).
 *
 * This file is the "presentation" of realtime; actual message routing
 * (from Redis pub/sub → socket) lives in TokenStreamConsumer.
 */
@WebSocketGateway({ path: '/ws' })
export class RealtimeGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect
{
  private readonly log = new Logger(RealtimeGateway.name);
  private readonly sockets = new Map<string, Set<AuthenticatedSocket>>();

  @WebSocketServer()
  server!: Server;

  afterInit(): void {
    this.log.log('Realtime gateway initialized at /ws');
  }

  handleConnection(client: AuthenticatedSocket, req: IncomingMessage): void {
    const tenantId = req.headers['x-tenant-id'] as string | undefined;
    const userId = req.headers['x-user-id'] as string | undefined;
    const url = new URL(req.url ?? '/', 'http://localhost');
    const sessionId = url.searchParams.get('session') ?? undefined;

    if (!tenantId || !userId || !sessionId) {
      this.log.warn({ tenantId, userId, sessionId }, 'rejecting WS — missing context');
      client.close(4001, 'Missing context');
      return;
    }

    client.tenantId = tenantId;
    client.userId = userId;
    client.sessionId = sessionId;

    const key = this.subscriptionKey(tenantId, sessionId);
    if (!this.sockets.has(key)) this.sockets.set(key, new Set());
    this.sockets.get(key)!.add(client);

    this.log.debug({ key, total: this.sockets.get(key)?.size }, 'WS connected');
  }

  handleDisconnect(client: AuthenticatedSocket): void {
    if (!client.tenantId || !client.sessionId) return;
    const key = this.subscriptionKey(client.tenantId, client.sessionId);
    const set = this.sockets.get(key);
    if (set) {
      set.delete(client);
      if (set.size === 0) this.sockets.delete(key);
    }
    this.log.debug({ key }, 'WS disconnected');
  }

  /** Used by TokenStreamConsumer to fanout a Redis event. */
  fanout(tenantId: string, sessionId: string, payload: unknown): number {
    const key = this.subscriptionKey(tenantId, sessionId);
    const set = this.sockets.get(key);
    if (!set || set.size === 0) return 0;

    const message = JSON.stringify(payload);
    let delivered = 0;
    for (const socket of set) {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(message);
        delivered++;
      }
    }
    return delivered;
  }

  private subscriptionKey(tenantId: string, sessionId: string): string {
    return `${tenantId}:${sessionId}`;
  }
}
