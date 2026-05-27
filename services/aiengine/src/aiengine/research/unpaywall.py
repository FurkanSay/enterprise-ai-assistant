"""Unpaywall — DOI → open-access PDF URL.

Unpaywall (https://unpaywall.org/products/api) is the canonical OA
finder: given a DOI, it returns whether a free PDF exists anywhere
(publisher OA, repository copy, author homepage) and the direct URL.

ToS requires an email address as a query parameter for traffic
attribution. We accept it via env var so different deployments can
identify themselves cleanly.
"""

from __future__ import annotations

import httpx
import structlog

log = structlog.get_logger(__name__)

_BASE = "https://api.unpaywall.org/v2"
_TIMEOUT = httpx.Timeout(15.0, connect=4.0)


async def find_oa_pdf(doi: str, *, email: str) -> str | None:
    """Return the best open-access PDF URL for a DOI, or None."""
    if not doi:
        return None
    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        resp = await client.get(f"{_BASE}/{doi}", params={"email": email})
        if resp.status_code == 404:
            return None
        if resp.status_code != 200:
            log.warning(
                "research.unpaywall.unexpected_status",
                doi=doi,
                status=resp.status_code,
            )
            return None
        body = resp.json()
    best = body.get("best_oa_location") or {}
    return best.get("url_for_pdf") or best.get("url")
