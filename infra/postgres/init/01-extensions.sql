-- Postgres extensions required by the platform.
-- Runs on first container boot (docker-entrypoint-initdb.d order).

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";       -- pgvector — optional embeddings backup
CREATE EXTENSION IF NOT EXISTS "pg_trgm";       -- trigram for fuzzy text search
