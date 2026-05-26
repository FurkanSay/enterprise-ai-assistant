"""OpenTelemetry setup — traces, metrics, logs.

Exports OTLP to the collector (see docker-compose). Auto-instruments
FastAPI, SQLAlchemy, Redis, gRPC.
"""

from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.grpc import GrpcAioInstrumentorClient
from opentelemetry.instrumentation.redis import RedisInstrumentor
from opentelemetry.instrumentation.sqlalchemy import SQLAlchemyInstrumentor
from opentelemetry.sdk.resources import SERVICE_NAME, SERVICE_NAMESPACE, SERVICE_VERSION, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from aiengine.core.config import Settings

_provider: TracerProvider | None = None


def configure_telemetry(app: FastAPI, settings: Settings) -> None:
    """Set up OTel exporters + auto-instrumentation. Idempotent."""
    global _provider

    if _provider is not None:
        return

    resource = Resource.create(
        {
            SERVICE_NAME: settings.service_name,
            SERVICE_VERSION: settings.service_version,
            SERVICE_NAMESPACE: settings.otel_service_namespace,
            "deployment.environment": settings.environment,
        }
    )

    _provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint=settings.otel_exporter_otlp_endpoint, insecure=True)
    _provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(_provider)

    # Auto-instrument
    FastAPIInstrumentor.instrument_app(app, tracer_provider=_provider)
    SQLAlchemyInstrumentor().instrument(tracer_provider=_provider)
    RedisInstrumentor().instrument(tracer_provider=_provider)
    GrpcAioInstrumentorClient().instrument(tracer_provider=_provider)


def shutdown_telemetry() -> None:
    """Flush any pending spans on shutdown."""
    if _provider is not None:
        _provider.shutdown()


def get_tracer(name: str) -> trace.Tracer:
    """Get a tracer for manual span creation."""
    return trace.get_tracer(name)
