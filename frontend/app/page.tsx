import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-8 px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">Kurumsal AI Asistan</h1>
      <p className="text-center text-neutral-600 dark:text-neutral-400">
        Çok kiracılı, RAG destekli kurumsal AI asistan platformu.
      </p>
      <div className="flex gap-3">
        <Link
          href="/chat"
          className="rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-800 dark:bg-neutral-100 dark:text-neutral-900"
        >
          Sohbete Başla
        </Link>
        <a
          href="https://github.com/USERNAME/kurumsal-ai-asistan-platformu"
          className="rounded-md border border-neutral-300 px-4 py-2 text-sm font-medium hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
          target="_blank"
          rel="noreferrer noopener"
        >
          GitHub
        </a>
      </div>
    </main>
  );
}
