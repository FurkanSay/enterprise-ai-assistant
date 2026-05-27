"""Paper → Documents pipeline bridge.

Once the user picks a paper to add to their RAG corpus, we:
  1. Resolve a PDF URL (paper.oa_pdf_url if already set, else
     Unpaywall by DOI; arXiv ids have a direct fallback).
  2. Download the bytes (or, for paywalled papers with no OA copy,
     fall back to a text document built from the abstract + metadata
     — at least the model can quote from that).
  3. Upload through the Documents service over internal HTTP, with the
     current tenant's X-Tenant-Id / X-User-Id headers forwarded so the
     row is persisted under the right tenant. No Gateway involved —
     Documents trusts the headers because nothing else can reach it on
     the docker network.

We deliberately reuse the existing /api/v1/documents endpoint instead
of inventing an "internal-upload" route — Documents was already
designed to accept tenant context via headers (Gateway just happens to
be the usual injector). Less code, one auth model for the whole
platform.
"""

from __future__ import annotations

import io

import httpx
import structlog

from aiengine.core.config import get_settings
from aiengine.core.tenant import TenantContext
from aiengine.research import unpaywall
from aiengine.research.models import Paper

log = structlog.get_logger(__name__)

_TIMEOUT = httpx.Timeout(30.0, connect=5.0)


async def _resolve_pdf_url(paper: Paper) -> str | None:
    if paper.oa_pdf_url:
        return paper.oa_pdf_url
    if paper.arxiv_id:
        # Canonical arXiv PDF URL — works without any API call.
        return f"https://arxiv.org/pdf/{paper.arxiv_id}.pdf"
    if paper.doi:
        settings = get_settings()
        if settings.unpaywall_email:
            return await unpaywall.find_oa_pdf(paper.doi, email=settings.unpaywall_email)
    return None


def _build_filename(paper: Paper, suffix: str) -> str:
    parts: list[str] = []
    if paper.authors:
        parts.append(paper.authors[0].split()[-1])  # last name of first author
    if paper.year:
        parts.append(str(paper.year))
    parts.append(paper.title[:60].strip())
    safe = "-".join(parts).replace("/", "-").replace("\\", "-").strip()
    return f"{safe}.{suffix}"


def _build_title(paper: Paper) -> str:
    first_author = paper.authors[0].split()[-1] if paper.authors else "Unknown"
    suffix = f"et al., {paper.year}" if len(paper.authors) > 1 else (paper.year or "")
    return f"[Lit] {first_author} {suffix} — {paper.title}".strip()


def _build_abstract_doc(paper: Paper) -> bytes:
    """Fallback document when no OA PDF is available — abstract +
    metadata as plain text. RAG can still cite the abstract."""
    lines = [
        f"Title: {paper.title}",
        f"Authors: {', '.join(paper.authors) if paper.authors else 'Unknown'}",
        f"Year: {paper.year or 'Unknown'}",
        f"Venue: {paper.venue or 'Unknown'}",
        f"Citations: {paper.citations if paper.citations is not None else 'Unknown'}",
        f"DOI: {paper.doi or 'Unknown'}",
        f"Source: {paper.source}",
        "",
        "Abstract:",
        paper.abstract or "(no abstract available)",
    ]
    return "\n".join(lines).encode("utf-8")


async def ingest(
    tenant: TenantContext,
    paper: Paper,
    *,
    source_session_id: str | None = None,
) -> dict[str, str]:
    """Push the paper through the Documents pipeline.

    Returns the Documents response (id, status, ...).
    Raises on transport failure; the agent loop wraps that as a
    tool_result with is_error=True.
    """
    settings = get_settings()
    pdf_url = await _resolve_pdf_url(paper)

    headers = {
        "X-Tenant-Id": str(tenant.tenant_id),
        "X-User-Id": str(tenant.user_id),
    }
    title = _build_title(paper)

    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        if pdf_url:
            try:
                pdf_resp = await client.get(pdf_url, follow_redirects=True)
                pdf_resp.raise_for_status()
                file_bytes = pdf_resp.content
                file_name = _build_filename(paper, "pdf")
                mime = "application/pdf"
                log.info(
                    "research.ingest.pdf_downloaded",
                    doi=paper.doi,
                    bytes=len(file_bytes),
                    url=pdf_url,
                )
            except httpx.HTTPError as exc:
                log.warning(
                    "research.ingest.pdf_download_failed",
                    doi=paper.doi,
                    url=pdf_url,
                    error=str(exc),
                )
                file_bytes = _build_abstract_doc(paper)
                file_name = _build_filename(paper, "txt")
                mime = "text/plain"
        else:
            # No OA PDF anywhere — persist the abstract so at least the
            # model has something to ground in.
            file_bytes = _build_abstract_doc(paper)
            file_name = _build_filename(paper, "txt")
            mime = "text/plain"
            log.info("research.ingest.no_pdf_using_abstract", doi=paper.doi)

        files = {
            "file": (file_name, io.BytesIO(file_bytes), mime),
        }
        data: dict[str, str] = {"title": title}
        if source_session_id:
            data["source_session_id"] = source_session_id
            if paper.doi:
                data["source_paper_doi"] = paper.doi
            data["source_paper_title"] = paper.title
        upload_resp = await client.post(
            f"{settings.documents_http_url}/api/v1/documents",
            headers=headers,
            files=files,
            data=data,
        )
        upload_resp.raise_for_status()
        body = upload_resp.json()
        log.info(
            "research.ingest.uploaded",
            doi=paper.doi,
            document_id=body.get("id"),
            kind="pdf" if mime == "application/pdf" else "abstract",
        )
        return body
