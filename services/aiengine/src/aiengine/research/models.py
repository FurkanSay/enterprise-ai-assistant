"""Cross-source paper model.

OpenAlex, Semantic Scholar and arXiv each return slightly different
shapes — we collapse the useful fields into one dataclass so the rest
of the codebase (aggregator, tool, frontend) doesn't have to care
which source delivered a given record.
"""

from __future__ import annotations

from pydantic import BaseModel


class Paper(BaseModel):
    """One academic paper, source-agnostic."""

    # Identity (at least one of doi/arxiv_id/source_id must be set so we
    # can dedup. Title-only matches are a last resort.)
    doi: str | None = None
    arxiv_id: str | None = None
    source: str  # "openalex" | "semantic_scholar" | "arxiv"
    source_id: str  # provider-native id

    # Display fields
    title: str
    authors: list[str] = []
    year: int | None = None
    venue: str | None = None
    abstract: str | None = None
    citations: int | None = None

    # PDF / OA
    oa_pdf_url: str | None = None
    landing_url: str | None = None  # publisher page or arXiv abstract

    @property
    def primary_key(self) -> str:
        """Used for dedup across sources. DOI is canonical; falls back
        to arxiv id and then to a (lowercased, whitespace-normalised)
        title to catch the same paper indexed under different ids."""
        if self.doi:
            return f"doi:{self.doi.lower()}"
        if self.arxiv_id:
            return f"arxiv:{self.arxiv_id.lower()}"
        return f"title:{' '.join(self.title.lower().split())}"
