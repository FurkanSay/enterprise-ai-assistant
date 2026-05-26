"""RAG pipeline — ingestion + hybrid retrieval + reranking.

Components:
  - embeddings.py    → sentence-transformers wrapper
  - qdrant_store.py  → Qdrant client + tenant-scoped queries
  - bm25_client.py   → gRPC client to Processing service
  - reranker.py      → cross-encoder
  - pipeline.py      → orchestrator
"""

from aiengine.rag.pipeline import RetrievalHit, hybrid_search

__all__ = ["hybrid_search", "RetrievalHit"]
