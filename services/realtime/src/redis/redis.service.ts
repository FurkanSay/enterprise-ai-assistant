import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

/**
 * Redis pub/sub client. Two connections needed:
 *  - subscriber: listens on stream.<session_id> channels
 *  - publisher (future): only if Realtime publishes its own events
 *
 * Keep a single subscriber for the whole process — ioredis handles fanout
 * internally per channel.
 */
@Injectable()
export class RedisService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RedisService.name);
  private subscriber!: Redis;

  constructor(private readonly config: ConfigService) {}

  onModuleInit() {
    const url = this.config.get<string>('REDIS_URL') ?? 'redis://localhost:6379';
    this.subscriber = new Redis(url, { lazyConnect: false });
    this.subscriber.on('error', (err) => this.logger.error(`Redis error: ${err.message}`));
    this.subscriber.on('connect', () => this.logger.log('Redis subscriber connected'));
  }

  async onModuleDestroy() {
    await this.subscriber?.quit();
  }

  subscribe(channel: string, handler: (message: string) => void) {
    this.subscriber.subscribe(channel);
    this.subscriber.on('message', (ch, msg) => {
      if (ch === channel) handler(msg);
    });
  }

  unsubscribe(channel: string) {
    this.subscriber.unsubscribe(channel);
  }
}
