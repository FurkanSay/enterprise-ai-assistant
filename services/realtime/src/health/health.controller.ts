import { Controller, Get, Inject } from '@nestjs/common';
import type Redis from 'ioredis';

@Controller('health')
export class HealthController {
  constructor(@Inject('REDIS_CLIENT') private readonly redis: Redis) {}

  @Get('live')
  live(): { status: string; service: string } {
    return { status: 'ok', service: 'realtime' };
  }

  @Get('ready')
  async ready(): Promise<{ status: string; service: string; checks: Record<string, string> }> {
    const redisStatus = await this.redis
      .ping()
      .then(() => 'ok')
      .catch(() => 'down');

    return {
      status: redisStatus === 'ok' ? 'ok' : 'degraded',
      service: 'realtime',
      checks: { redis: redisStatus },
    };
  }
}
