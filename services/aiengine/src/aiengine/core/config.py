"""Application settings via pydantic-settings.

Single source of truth for all config. Reads from env vars + .env file.
Validates at startup — invalid config fails fast.
"""

from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings — env-driven, validated at boot."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ─── Service identity ────────────────────────────────────────────────
    service_name: str = "aiengine"
    service_version: str = "0.1.0"
    environment: Literal["development", "staging", "production"] = "development"

    # ─── Database (Postgres) ─────────────────────────────────────────────
    database_url: str = Field(
        default="postgresql+asyncpg://kai:kai_dev_pwd@localhost:5432/kai",
        description="Async SQLAlchemy URL",
    )
    database_schema: str = "aiengine_schema"
    database_pool_size: int = 10
    database_pool_overflow: int = 5

    # ─── Redis ───────────────────────────────────────────────────────────
    redis_url: str = "redis://localhost:6379"

    # ─── Qdrant ──────────────────────────────────────────────────────────
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "kai_chunks"
    qdrant_vector_size: int = 384  # all-MiniLM-L6-v2

    # ─── Processing (gRPC client) ────────────────────────────────────────
    processing_grpc_url: str = "localhost:8083"

    # ─── LLM providers ───────────────────────────────────────────────────
    anthropic_api_key: str = ""
    openai_api_key: str = ""
    cohere_api_key: str = ""
    voyage_api_key: str = ""
    ollama_base_url: str = "http://host.docker.internal:11434"

    # ─── Defaults ────────────────────────────────────────────────────────
    default_embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    default_reranker_model: str = "BAAI/bge-reranker-v2-m3"
    default_llm_model: str = "claude-opus-4-7"
    max_tool_iterations_free: int = 10
    max_tool_iterations_pro: int = 50
    max_tokens_per_request: int = 8192

    # ─── Telemetry ───────────────────────────────────────────────────────
    otel_exporter_otlp_endpoint: str = "http://localhost:4317"
    otel_service_namespace: str = "kai"

    # ─── Logging ─────────────────────────────────────────────────────────
    log_level: Literal["debug", "info", "warning", "error"] = "info"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Cached settings accessor — instantiates once per process."""
    return Settings()
