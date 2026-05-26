import { Inject, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import type Redis from 'ioredis';
import { RealtimeGateway } from './realtime.gateway';

/**
 * Subscribes to Redis pub/sub channels published by the AI Engine:
 *   stream.<sessionId>   — LLM token stream
 *
 * Pattern subscribe (PSUBSCRIBE) so we don't need to manage individual
 * subscriptions per session. When a message arrives, we look up the
 * sessionId in the channel name and ask the gateway to fanout.
 */
@Injectable()
export class TokenStreamConsumer implements OnModuleInit, OnModuleDestroy {
  private readonly log = new Logger(TokenStreamConsumer.name);
  private static readonly PATTERN = 'stream.*';

  constructor(
    @Inject('REDIS_SUBSCRIBER') private readonly sub: Redis,
    private readonly gateway: RealtimeGateway,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.sub.psubscribe(TokenStreamConsumer.PATTERN);
    this.sub.on('pmessage', (_pattern, channel, message) => this.handleMessage(channel, message));
    this.log.log(`Subscribed to Redis pattern "${TokenStreamConsumer.PATTERN}"`);
  }

  async onModuleDestroy(): Promise<void> {
    await this.sub.punsubscribe(TokenStreamConsumer.PATTERN);
    await this.sub.quit();
  }

  private handleMessage(channel: string, raw: string): void {
    // channel format: "stream.<tenantId>.<sessionId>"
    const parts = channel.split('.');
    if (parts.length < 3) {
      this.log.warn({ channel }, 'malformed channel — skipping');
      return;
    }
    const tenantId = parts[1];
    const sessionId = parts.slice(2).join('.');

    let payload: unknown;
    try {
      payload = JSON.parse(raw);
    } catch (err) {
      this.log.warn({ channel, err }, 'invalid JSON payload — skipping');
      return;
    }

    const delivered = this.gateway.fanout(tenantId, sessionId, payload);
    if (delivered === 0) {
      this.log.debug({ tenantId, sessionId }, 'no active socket for token stream');
    }
  }
}
