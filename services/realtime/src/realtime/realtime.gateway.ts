import {
  OnGatewayConnection,
  OnGatewayDisconnect,
  OnGatewayInit,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Logger, OnModuleDestroy } from '@nestjs/common';
import { IncomingMessage } from 'http';
import { WebSocket, Server } from 'ws';

interface AuthenticatedSocket extends WebSocket {
  tenantId?: string;
  userId?: string;
  sessionId?: string;
  isAlive?: boolean;
  /** Wall-clock ms at handshake — used to grant fresh sockets a grace
   *  period before the first heartbeat counts against them. */
  joinedAt?: number;
}

/**
 * Active WebSocket gateway. Browsers connect through:
 *   ws://gateway/api/v1/ws?session=<sessionId>
 *
 * Auth model: Gateway runs JWT validation BEFORE the upgrade and forwards
 * X-Tenant-Id / X-User-Id headers. We trust those headers — same pattern
 * as the other downstream services (Documents, AI Engine). Connections
 * missing tenant/user/session context are rejected with WS code 4001.
 *
 * (`StreamGateway` in src/websocket/ is unused legacy from an earlier
 * design where browsers explicitly subscribed via a WS message; we have
 * not removed it yet but the active wiring is here.)
 *
 * Fanout: TokenStreamConsumer subscribes to Redis pattern `stream.*` and
 * calls `fanout(tenantId, sessionId, payload)` per message. AI Engine is
 * the publisher.
 *
 * Heartbeat: every 30s we send a server-initiated ping. A socket that
 * fails to pong before the next interval is terminated, so dead browser
 * tabs do not pile up sockets forever.
 */
@WebSocketGateway({ path: '/ws' })
export class RealtimeGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect, OnModuleDestroy
{
  private readonly log = new Logger(RealtimeGateway.name);
  private readonly sockets = new Map<string, Set<AuthenticatedSocket>>();
  private heartbeatTimer: NodeJS.Timeout | null = null;

  private static readonly HEARTBEAT_MS = 30_000;

  @WebSocketServer()
  server!: Server;

  afterInit(): void {
    this.log.log('Realtime gateway initialized at /ws');
    this.heartbeatTimer = setInterval(() => this.runHeartbeat(), RealtimeGateway.HEARTBEAT_MS);
  }

  onModuleDestroy(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  handleConnection(client: AuthenticatedSocket, req: IncomingMessage): void {
    const tenantId = req.headers['x-tenant-id'] as string | undefined;
    const userId = req.headers['x-user-id'] as string | undefined;
    const url = new URL(req.url ?? '/', 'http://localhost');
    const sessionId = url.searchParams.get('session') ?? undefined;

    if (!tenantId || !userId || !sessionId) {
      this.log.warn(
        { tenantId, userId, sessionId },
        'rejecting WS — missing context',
      );
      // 4001 = client error in WS code range; browsers will see this in
      // the `event.code` field of their `close` handler.
      client.close(4001, 'Missing context');
      return;
    }

    client.tenantId = tenantId;
    client.userId = userId;
    client.sessionId = sessionId;
    client.isAlive = true;
    client.joinedAt = Date.now();
    client.on('pong', () => {
      client.isAlive = true;
    });

    const key = this.subscriptionKey(tenantId, sessionId);
    if (!this.sockets.has(key)) this.sockets.set(key, new Set());
    this.sockets.get(key)!.add(client);

    // Send a one-shot "ready" so the browser can confirm the upgrade
    // actually reached this server (Gateway can swallow upgrades silently
    // if the route is misconfigured — easier to debug from the client).
    client.send(JSON.stringify({ event: 'ready', data: { sessionId } }));

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

  /** Called by TokenStreamConsumer to deliver a Redis pub/sub message.
   *  Defensive tenant cross-check: even though the subscription key
   *  embeds tenantId, we also assert the socket itself was opened by
   *  the same tenant before delivering. Costs one comparison per send,
   *  buys belt-and-suspenders if anyone ever publishes by sessionId
   *  alone or the map key ever collides. */
  fanout(tenantId: string, sessionId: string, payload: unknown): number {
    const key = this.subscriptionKey(tenantId, sessionId);
    const set = this.sockets.get(key);
    if (!set || set.size === 0) return 0;

    const message = JSON.stringify(payload);
    let delivered = 0;
    for (const socket of set) {
      if (socket.tenantId !== tenantId) continue;
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(message);
        delivered++;
      }
    }
    return delivered;
  }

  private runHeartbeat(): void {
    const now = Date.now();
    for (const set of this.sockets.values()) {
      for (const socket of set) {
        // Grace period: a freshly-connected socket gets one full
        // HEARTBEAT_MS to settle before its first ping counts. Without
        // this, a tab opened at T=29.9s could be terminated at T=60s
        // before it had a chance to respond to a single ping.
        if (
          socket.joinedAt !== undefined &&
          now - socket.joinedAt < RealtimeGateway.HEARTBEAT_MS
        ) {
          continue;
        }
        if (socket.isAlive === false) {
          // Missed the previous pong — assume dead.
          socket.terminate();
          continue;
        }
        socket.isAlive = false;
        try {
          socket.ping();
        } catch {
          socket.terminate();
        }
      }
    }
  }

  private subscriptionKey(tenantId: string, sessionId: string): string {
    return `${tenantId}:${sessionId}`;
  }
}
