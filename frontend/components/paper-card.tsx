'use client';

import { useState } from 'react';
import { BookOpen, Check, Download, ExternalLink } from 'lucide-react';
import clsx from 'clsx';

export interface Paper {
  doi?: string | null;
  arxiv_id?: string | null;
  source: string;
  source_id: string;
  title: string;
  authors?: string[];
  year?: number | null;
  venue?: string | null;
  abstract?: string | null;
  citations?: number | null;
  oa_pdf_url?: string | null;
  landing_url?: string | null;
}

/** Render of one paper inside a Deep Search result block.
 *
 *  Phase L now auto-ingests every result on the backend side, so the
 *  card no longer carries an "Add to RAG" button — the entire result
 *  set is queued for ingestion the moment literature_search returns.
 *  The card surfaces:
 *    - Title, authors, year, venue, citations
 *    - Abstract preview (collapsible)
 *    - Open-access PDF link if available
 *    - DOI link
 *    - An "auto-ingest" indicator so the user knows what is happening */
export function PaperCard({ paper }: { paper: Paper }) {
  const [expanded, setExpanded] = useState(false);
  const authorsLine =
    paper.authors && paper.authors.length > 0
      ? paper.authors.length > 4
        ? `${paper.authors.slice(0, 4).join(', ')} +${paper.authors.length - 4}`
        : paper.authors.join(', ')
      : 'Bilinmeyen';
  const abstract = paper.abstract ?? '';
  const showExpand = abstract.length > 220;
  const visible = expanded || !showExpand ? abstract : abstract.slice(0, 220) + '…';

  return (
    <div className="rounded-lg border border-neutral-200 bg-white p-3 text-xs dark:border-neutral-800 dark:bg-neutral-900">
      <div className="flex items-start gap-2">
        <BookOpen size={16} className="mt-0.5 shrink-0 text-purple-500" />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold leading-snug">{paper.title}</div>
          <div className="mt-0.5 truncate text-[11px] text-neutral-500">
            {authorsLine}
            {paper.year ? ` · ${paper.year}` : ''}
            {paper.venue ? ` · ${paper.venue}` : ''}
            {typeof paper.citations === 'number'
              ? ` · ${paper.citations.toLocaleString('tr-TR')} atıf`
              : ''}
            {' · '}
            <span className="rounded bg-neutral-100 px-1 dark:bg-neutral-800">
              {paper.source}
            </span>
          </div>
        </div>
      </div>

      {abstract && (
        <div className="mt-2 whitespace-pre-wrap text-[11px] leading-relaxed text-neutral-600 dark:text-neutral-400">
          {visible}{' '}
          {showExpand && (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="inline text-purple-600 hover:underline dark:text-purple-400"
            >
              {expanded ? 'gizle' : 'tamamını göster'}
            </button>
          )}
        </div>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-2">
        <span
          className={clsx(
            'flex items-center gap-1 rounded-md border px-2 py-1 text-[11px]',
            paper.oa_pdf_url || paper.arxiv_id
              ? 'border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
              : 'border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-300',
          )}
          title={
            paper.oa_pdf_url || paper.arxiv_id
              ? 'PDF arka planda indirilip RAG koleksiyonuna ekleniyor'
              : 'PDF açık erişim değil — abstract metadata olarak ekleniyor'
          }
        >
          {paper.oa_pdf_url || paper.arxiv_id ? (
            <>
              <Download size={12} />
              Otomatik RAG&apos;e ekleniyor
            </>
          ) : (
            <>
              <Check size={12} />
              Abstract eklenecek (PDF yok)
            </>
          )}
        </span>
        {paper.oa_pdf_url && (
          <a
            href={paper.oa_pdf_url}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1 rounded-md border border-neutral-300 px-2 py-1 text-[11px] text-neutral-700 hover:bg-neutral-50 dark:border-neutral-700 dark:text-neutral-300 dark:hover:bg-neutral-800"
          >
            <ExternalLink size={12} />
            Açık erişim PDF
          </a>
        )}
        {paper.doi && (
          <a
            href={`https://doi.org/${paper.doi}`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-[11px] text-neutral-500 hover:underline"
          >
            DOI: {paper.doi}
          </a>
        )}
      </div>
    </div>
  );
}
