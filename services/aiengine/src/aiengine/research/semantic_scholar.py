"""Semantic Scholar Graph API client.

Semantic Scholar (api.semanticscholar.org) is run by Allen AI. Free,
no key required for low-volume reads. Their `paperSearch` endpoint
returns title, authors, year, citation count, abstract — and a
nice TLDR field on a meaningful subset of papers (we surface that
as the abstract when present, it's more useful for skimming).
"""

from __future__ import annotations

import httpx
import structlog

from aiengine.research.models import Paper

log = structlog.get_logger(__name__)

_BASE = "https://api.semanticscholar.org/graph/v1"
_TIMEOUT = httpx.Timeout(15.0, connect=4.0)
_FIELDS = (
    "title,year,abstract,tldr,authors.name,citationCount,"
    "externalIds,openAccessPdf,publicationVenue"
)


async def search(
    query: str, *, max_results: int = 10, year_from: int | None = None
) -> list[Paper]:
    params: dict[str, str | int] = {
        "query": query,
        "limit": max(1, min(max_results, 25)),
        "fields": _FIELDS,
    }
    if year_from:
        params["year"] = f"{year_from}-"

    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        resp = await client.get(f"{_BASE}/paper/search", params=params)
        if resp.status_code == 429:
            log.warning("research.semantic_scholar.rate_limited")
            return []
        resp.raise_for_status()
        body = resp.json()

    papers: list[Paper] = []
    for w in body.get("data", []):
        ext = w.get("externalIds") or {}
        tldr = (w.get("tldr") or {}).get("text") if w.get("tldr") else None
        venue = (w.get("publicationVenue") or {}).get("name")
        oa = w.get("openAccessPdf") or {}
        authors = [a.get("name", "") for a in w.get("authors", []) if a.get("name")][:8]
        papers.append(
            Paper(
                doi=ext.get("DOI"),
                arxiv_id=ext.get("ArXiv"),
                source="semantic_scholar",
                source_id=w.get("paperId", ""),
                title=w.get("title") or "Untitled",
                authors=authors,
                year=w.get("year"),
                venue=venue,
                # Prefer the human-written TLDR. Fall back to the raw abstract,
                # which Semantic Scholar returns full-text for OA papers.
                abstract=tldr or w.get("abstract"),
                citations=w.get("citationCount"),
                oa_pdf_url=oa.get("url"),
            )
        )
    log.info("research.semantic_scholar.search", query=query, hits=len(papers))
    return papers
