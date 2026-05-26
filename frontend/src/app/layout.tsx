import type { Metadata } from 'next';
import { Inter, Source_Serif_4 } from 'next/font/google';
import './globals.css';

// Inter — geometric sans for UI, headings, buttons.
const fontSans = Inter({
  subsets: ['latin'],
  variable: '--font-sans',
  display: 'swap',
});

// Source Serif 4 — long-form serif for chat content and document text.
const fontSerif = Source_Serif_4({
  subsets: ['latin'],
  variable: '--font-serif',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Enterprise AI Assistant',
  description: 'Multi-tenant retrieval-augmented assistant for company documents.',
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="tr" suppressHydrationWarning>
      <body className={`${fontSans.variable} ${fontSerif.variable} min-h-screen`}>
        {children}
      </body>
    </html>
  );
}
