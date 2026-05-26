'use client';

import { useRef, useState } from 'react';
import { uploadDocument } from '@/lib/api-client';

export function DocumentUpload() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle');

  async function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    setStatus('uploading');
    try {
      await uploadDocument(file);
      setStatus('success');
    } catch {
      setStatus('error');
    }
  }

  return (
    <div className="flex items-center gap-3">
      <button
        onClick={() => inputRef.current?.click()}
        className="rounded-md border border-neutral-300 px-3 py-1.5 text-xs font-medium hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
      >
        Doküman yükle
      </button>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.docx,.txt,.md"
        className="hidden"
        onChange={handleChange}
      />
      {status === 'uploading' && (
        <span className="text-xs text-neutral-500">Yükleniyor…</span>
      )}
      {status === 'success' && (
        <span className="text-xs text-green-600">İşleniyor</span>
      )}
      {status === 'error' && (
        <span className="text-xs text-red-600">Hata</span>
      )}
    </div>
  );
}
