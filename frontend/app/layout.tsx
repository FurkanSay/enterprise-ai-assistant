import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Kurumsal AI Asistan',
  description: 'Multi-tenant RAG AI Assistant Platform',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="tr">
      <body className="min-h-screen bg-neutral-50 text-neutral-900 dark:bg-neutral-950 dark:text-neutral-100">
        {children}
      </body>
    </html>
  );
}
