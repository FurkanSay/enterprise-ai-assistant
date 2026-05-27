"""Parallel multi-source paper search with DOI dedup.

Strategy:
  1. Fire OpenAlex + Semantic Scholar + arXiv in parallel
  2. Dedup by primary_key (DOI > arXiv id > normalised title)
  3. When two sources hit the same paper, merge: prefer the entry with
     the richer abstract / OA url
  4. Sort by citation count (None → end), trim to N

Failure model: a single API outage must not break the whole search.
We catch + log per-source and continue with whatever did succeed.
"""

from __future__ import annotations

import asyncio

import structlog

from aiengine.research import arxiv, openalex, semantic_scholar
from aiengine.research.models import Paper

log = structlog.get_logger(__name__)


def _merge(a: Paper, b: Paper) -> Paper:
    """Pick the better of two records for the same paper."""
    return Paper(
        doi=a.doi or b.doi,
        arxiv_id=a.arxiv_id or b.arxiv_id,
        # Keep the first-seen source for traceability; the merged set
        # gets exposed to the model via the dedup primary_key anyway.
        source=a.source if (a.abstract or a.oa_pdf_url) else b.source,
        source_id=a.source_id if (a.abstract or a.oa_pdf_url) else b.source_id,
        title=a.title if len(a.title) >= len(b.title) else b.title,
        authors=a.authors if len(a.authors) >= len(b.authors) else b.authors,
        year=a.year or b.year,
        venue=a.venue or b.venue,
        abstract=a.abstract or b.abstract,
        citations=max(filter(None, [a.citations, b.citations]), default=None),
        oa_pdf_url=a.oa_pdf_url or b.oa_pdf_url,
        landing_url=a.landing_url or b.landing_url,
    )


async def search_all(
    query: str,
    *,
    max_results: int = 10,
    year_from: int | None = None,
    polite_email: str | None = None,
) -> list[Paper]:
    """Run all three sources in parallel and return deduped, sorted hits."""
    # Each source asked for `max_results * 2` because after dedup we
    # often lose ~30% of rows. Trimmed back to `max_results` at the end.
    per_source = max_results * 2
    tasks = [
        openalex.search(
            query,
            max_results=per_source,
            year_from=year_from,
            polite_email=polite_email,
        ),
        semantic_scholar.search(query, max_results=per_source, year_from=year_from),
        arxiv.search(query, max_results=per_source),
    ]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    merged: dict[str, Paper] = {}
    for src_name, res in zip(("openalex", "semantic_scholar", "arxiv"), results):
        if isinstance(res, BaseException):
            log.warning("research.aggregator.source_failed", source=src_name, error=str(res))
            continue
        for paper in res:
            key = paper.primary_key
            existing = merged.get(key)
            merged[key] = _merge(existing, paper) if existing else paper

    # Citation-sorted, None at the bottom so the model still sees
    # arxiv preprints (citation=None) below the established work.
    ordered = sorted(
        merged.values(),
        key=lambda p: p.citations if p.citations is not None else -1,
        reverse=True,
    )
    log.info(
        "research.aggregator.done",
        query=query,
        total_raw=sum(len(r) for r in results if isinstance(r, list)),
        deduped=len(ordered),
    )
    return ordered[:max_results]
