'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

import { getCurrentUser } from '@/lib/auth';

export const dynamic = 'force-dynamic';

export default function HomePage() {
  const router = useRouter();

  // If already logged in, jump straight to chat. Cheap UX — keeps the
  // marketing landing for first-time visitors only.
  useEffect(() => {
    if (getCurrentUser()) {
      router.replace('/chat');
    }
  }, [router]);

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-8 px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">Kurumsal AI Asistan</h1>
      <p className="text-center text-neutral-600 dark:text-neutral-400">
        Çok kiracılı, RAG destekli kurumsal AI asistan platformu.
      </p>
      <div className="flex gap-3">
        <Link
          href="/login"
          className="rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-800 dark:bg-neutral-100 dark:text-neutral-900"
        >
          Giriş yap
        </Link>
        <Link
          href="/register"
          className="rounded-md border border-neutral-300 px-4 py-2 text-sm font-medium hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
        >
          Kayıt ol
        </Link>
      </div>
    </main>
  );
}
