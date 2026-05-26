import { Controller, Get } from '@nestjs/common';

@Controller('health')
export class HealthController {
  @Get('live')
  live() {
    return { status: 'ok', service: 'realtime' };
  }

  @Get('ready')
  ready() {
    // TODO: ping Redis + verify pub/sub subscription healthy
    return { status: 'ok', service: 'realtime' };
  }
}
