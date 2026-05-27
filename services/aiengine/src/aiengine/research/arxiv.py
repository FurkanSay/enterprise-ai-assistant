"""arXiv API client.

arXiv exposes an Atom feed at export.arxiv.org/api/query. Free, no auth,
explicit policy: "be polite, don't hammer". For our use case (max ~25
results per user query) this is well within their guidelines.

The Atom payload is XML — we parse it with the stdlib ElementTree to
avoid pulling in lxml. Fields we keep: id (arxiv id), title, authors,
summary, published year, primary PDF link.
"""

from __future__ import annotations

from xml.etree import ElementTree as ET

import httpx
import structlog

from aiengine.research.models import Paper

log = structlog.get_logger(__name__)

_BASE = "https://export.arxiv.org/api/query"
_TIMEOUT = httpx.Timeout(15.0, connect=4.0)
_NS = {
    "atom": "http://www.w3.org/2005/Atom",
    "arxiv": "http://arxiv.org/schemas/atom",
}


def _extract_arxiv_id(raw_id: str) -> str:
    # "http://arxiv.org/abs/2401.12345v2" → "2401.12345"
    last = raw_id.rsplit("/", 1)[-1]
    # strip version suffix
    return last.split("v")[0] if last and last[0].isdigit() else last


def _pdf_url(entry: ET.Element) -> str | None:
    for link in entry.findall("atom:link", _NS):
        if link.get("title") == "pdf" or link.get("type") == "application/pdf":
            return link.get("href")
    return None


async def search(query: str, *, max_results: int = 10) -> list[Paper]:
    params = {
        "search_query": f"all:{query}",
        "max_results": max(1, min(max_results, 25)),
        "sortBy": "relevance",
    }
    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        resp = await client.get(_BASE, params=params)
        resp.raise_for_status()
        body = resp.text

    try:
        root = ET.fromstring(body)
    except ET.ParseError as exc:
        log.warning("research.arxiv.parse_failed", error=str(exc))
        return []

    papers: list[Paper] = []
    for entry in root.findall("atom:entry", _NS):
        raw_id = (entry.findtext("atom:id", default="", namespaces=_NS) or "").strip()
        arxiv_id = _extract_arxiv_id(raw_id)
        title = (entry.findtext("atom:title", default="", namespaces=_NS) or "").strip()
        summary = (
            entry.findtext("atom:summary", default="", namespaces=_NS) or ""
        ).strip() or None
        published = entry.findtext("atom:published", default="", namespaces=_NS) or ""
        year = int(published[:4]) if published[:4].isdigit() else None
        authors = [
            (a.findtext("atom:name", default="", namespaces=_NS) or "").strip()
            for a in entry.findall("atom:author", _NS)
        ]
        authors = [a for a in authors if a][:8]
        # arxiv DOIs are predictable but not always set; emit if we can
        doi_el = entry.find("arxiv:doi", _NS)
        doi = doi_el.text.strip() if doi_el is not None and doi_el.text else None
        papers.append(
            Paper(
                doi=doi,
                arxiv_id=arxiv_id,
                source="arxiv",
                source_id=raw_id,
                title=" ".join(title.split()),  # arxiv titles have stray newlines
                authors=authors,
                year=year,
                venue="arXiv",
                abstract=summary,
                citations=None,  # arxiv API doesn't expose this
                oa_pdf_url=_pdf_url(entry),
                landing_url=raw_id,
            )
        )
    log.info("research.arxiv.search", query=query, hits=len(papers))
    return papers
