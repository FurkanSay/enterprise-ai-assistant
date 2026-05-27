'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

import { register } from '@/lib/api-client';

export const dynamic = 'force-dynamic';

/** Hard-coded demo tenant id — the init script creates exactly this row.
 *  Phase F.1 / a real tenant-provisioning flow can replace this with a
 *  proper signup/invite path. */
const DEMO_TENANT_ID = '00000000-0000-0000-0000-000000000001';

export default function RegisterPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await register(DEMO_TENANT_ID, email, password, displayName);
      if (res.ok) {
        router.push('/login');
        return;
      }
      if (res.status === 409) {
        setError('Bu email ile bir hesap zaten var.');
      } else if (res.status === 422 || res.status === 400) {
        const body = await res.json().catch(() => null);
        setError(body?.title ?? 'Geçersiz form verisi.');
      } else {
        setError(`Beklenmedik hata: ${res.status}`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Bağlantı hatası');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <h1 className="mb-2 text-2xl font-semibold">Kayıt ol</h1>
      <p className="mb-6 text-sm text-neutral-500">
        Demo tenant&apos;a yeni bir kullanıcı oluşturun.
      </p>
      <form onSubmit={submit} className="space-y-4">
        <label className="block">
          <span className="text-sm font-medium">Görünen ad</span>
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="mt-1 w-full rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </label>
        <label className="block">
          <span className="text-sm font-medium">Email</span>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 w-full rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </label>
        <label className="block">
          <span className="text-sm font-medium">Parola</span>
          <input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          />
        </label>
        {error && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-800 disabled:opacity-50 dark:bg-neutral-100 dark:text-neutral-900"
        >
          {busy ? 'Kaydediliyor…' : 'Hesap oluştur'}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-neutral-500">
        Zaten hesabınız var mı?{' '}
        <Link href="/login" className="underline">
          Giriş yapın
        </Link>
      </p>
    </main>
  );
}
