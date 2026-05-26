import type { Metadata } from 'next';
import { Inter, Source_Serif_4 } from 'next/font/google';
import './globals.css';

const fontSans = Inter({ subsets: ['latin'], variable: '--font-sans', display: 'swap' });
const fontSerif = Source_Serif_4({ subsets: ['latin'], variable: '--font-serif', display: 'swap' });

export const metadata: Metadata = {
  title: 'Enterprise AI Assistant',
  description: 'Multi-tenant retrieval-augmented assistant for company documents.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="tr" className={`${fontSans.variable} ${fontSerif.variable}`}>
      <body style={{ margin: 0, background: '#F9F4EE', color: '#2C2925' }}>{children}</body>
    </html>
  );
}
