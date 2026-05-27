"""Academic literature search + ingestion.

Phase L module. Pulls paper metadata from three open APIs in parallel
(OpenAlex, Semantic Scholar, arXiv), dedups by DOI, optionally resolves
to an open-access PDF via Unpaywall, then hands the result to the
existing Documents pipeline so RAG can answer questions about it.

No HTML scraping, no Google Scholar — those have ToS friction and
brittle selectors. The three APIs we use are explicitly free for
non-commercial research traffic and return richer metadata than
Scholar anyway (abstract + citation graph included).
"""
