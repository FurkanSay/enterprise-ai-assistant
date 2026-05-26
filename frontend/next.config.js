/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Standalone output is intentionally OFF until Phase H — it interacts badly
  // with the Next 15.0.3 prerender path used by our minimal landing.
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
    NEXT_PUBLIC_WS_URL: process.env.NEXT_PUBLIC_WS_URL ?? 'ws://localhost:8085',
  },
};

module.exports = nextConfig;
