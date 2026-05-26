import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify';
import { Logger } from 'nestjs-pino';
import { WsAdapter } from '@nestjs/platform-ws';
import { AppModule } from './app.module';
import { startTelemetry, shutdownTelemetry } from './telemetry';

async function bootstrap() {
  // OTel must initialize BEFORE Nest creates instrumented modules.
  startTelemetry();

  const app = await NestFactory.create<NestFastifyApplication>(
    AppModule,
    new FastifyAdapter({ trustProxy: true }),
    { bufferLogs: true },
  );
  app.useLogger(app.get(Logger));
  app.useWebSocketAdapter(new WsAdapter(app));
  app.enableShutdownHooks();

  const port = parseInt(process.env.PORT ?? '8085', 10);
  await app.listen(port, '0.0.0.0');

  process.once('SIGTERM', shutdownTelemetry);
  process.once('SIGINT', shutdownTelemetry);
}

bootstrap().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('fatal startup error', err);
  process.exit(1);
});
