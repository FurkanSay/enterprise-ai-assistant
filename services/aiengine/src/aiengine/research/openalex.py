"""OpenAlex search client.

OpenAlex (https://docs.openalex.org) is a free open catalogue of
scholarly papers. No API key required, "polite pool" identifies us via
the User-Agent header — they're more generous with rate limits when
you do this.

We only use the `/works` endpoint with `search=...` and a few filters.
The response has everything we need (title, authors, year, abstract
via the inverted-index trick, doi, citation count, OA url).
"""

from __future__ import annotations

import httpx
import structlog

from aiengine.research.models import Paper

log = structlog.get_logger(__name__)

_BASE = "https://api.openalex.org"
_TIMEOUT = httpx.Timeout(15.0, connect=4.0)


def _abstract_from_inverted_index(idx: dict[str, list[int]] | None) -> str | None:
    """OpenAlex returns abstracts as a {word: [positions]} inverted
    index to dodge copyright restrictions. Reconstruct the plain text."""
    if not idx:
        return None
    max_pos = -1
    for positions in idx.values():
        for p in positions:
            if p > max_pos:
                max_pos = p
    if max_pos < 0:
        return None
    words: list[str] = [""] * (max_pos + 1)
    for word, positions in idx.items():
        for p in positions:
            if 0 <= p < len(words):
                words[p] = word
    return " ".join(w for w in words if w).strip() or None


def _normalise_doi(raw: str | None) -> str | None:
    if not raw:
        return None
    return raw.removeprefix("https://doi.org/").removeprefix("http://doi.org/")


async def search(
    query: str,
    *,
    max_results: int = 10,
    year_from: int | None = None,
    polite_email: str | None = None,
) -> list[Paper]:
    params: dict[str, str | int] = {
        "search": query,
        "per_page": max(1, min(max_results, 25)),
        # `host_venue` was deprecated and removed in late 2024 — requesting
        # it now returns 400. Venue info comes from primary_location.source
        # instead; we read both shapes in the response loop.
        "select": (
            "id,doi,title,authorships,publication_year,abstract_inverted_index,"
            "cited_by_count,open_access,best_oa_location,primary_location"
        ),
    }
    if year_from:
        params["filter"] = f"from_publication_date:{year_from}-01-01"
    headers = {}
    if polite_email:
        headers["User-Agent"] = f"kai-platform/0.1 (mailto:{polite_email})"

    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        resp = await client.get(f"{_BASE}/works", params=params, headers=headers)
        resp.raise_for_status()
        body = resp.json()

    papers: list[Paper] = []
    for w in body.get("results", []):
        authors = [
            a.get("author", {}).get("display_name", "")
            for a in w.get("authorships", [])
            if a.get("author", {}).get("display_name")
        ][:8]
        oa = w.get("best_oa_location") or {}
        primary = w.get("primary_location") or {}
        primary_source = primary.get("source") or {}
        papers.append(
            Paper(
                doi=_normalise_doi(w.get("doi")),
                source="openalex",
                source_id=w.get("id", ""),
                title=w.get("title") or "Untitled",
                authors=authors,
                year=w.get("publication_year"),
                venue=primary_source.get("display_name"),
                abstract=_abstract_from_inverted_index(w.get("abstract_inverted_index")),
                citations=w.get("cited_by_count"),
                oa_pdf_url=oa.get("pdf_url"),
                landing_url=oa.get("landing_page_url") or primary.get("landing_page_url"),
            )
        )
    log.info("research.openalex.search", query=query, hits=len(papers))
    return papers
