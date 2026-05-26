import { Logger } from '@nestjs/common';
import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  OnGatewayDisconnect,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Server, WebSocket } from 'ws';
import { RedisService } from '../redis/redis.service';

/**
 * StreamGateway — pipes AI Engine's Redis-published token stream to browser WS.
 *
 * Flow:
 *  1. Browser connects with JWT (validated upstream by Gateway via WS upgrade headers)
 *  2. Browser sends { action: "subscribe", session_id } over WS
 *  3. Realtime SUBSCRIBE Redis channel `stream.<session_id>`
 *  4. Every Redis message → forward to that WS connection
 *  5. On disconnect → unsubscribe
 */
@WebSocketGateway({ path: '/ws', cors: true })
export class StreamGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer() server!: Server;
  private readonly logger = new Logger(StreamGateway.name);
  private readonly clientChannels = new Map<WebSocket, string>();

  constructor(private readonly redis: RedisService) {}

  handleConnection(client: WebSocket) {
    this.logger.log('client connected');
    // TODO: validate JWT from upgrade headers (X-Tenant-Id, X-User-Id)
  }

  handleDisconnect(client: WebSocket) {
    const channel = this.clientChannels.get(client);
    if (channel) {
      this.redis.unsubscribe(channel);
      this.clientChannels.delete(client);
    }
    this.logger.log('client disconnected');
  }

  @SubscribeMessage('subscribe')
  handleSubscribe(
    @MessageBody() data: { session_id: string },
    @ConnectedSocket() client: WebSocket,
  ) {
    const channel = `stream.${data.session_id}`;
    this.redis.subscribe(channel, (msg) => {
      if (client.readyState === WebSocket.OPEN) client.send(msg);
    });
    this.clientChannels.set(client, channel);
  }
}
