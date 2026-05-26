import { Module } from '@nestjs/common';
import { RealtimeGateway } from './realtime.gateway';
import { TokenStreamConsumer } from './token-stream.consumer';

@Module({
  providers: [RealtimeGateway, TokenStreamConsumer],
})
export class RealtimeModule {}
