/** @type {import('next').NextConfig} */
const nextConfig = {
  // Streaming + SSE need this off for our chat route
  output: 'standalone',
  reactStrictMode: true,
  // API + WS proxy targets (dev)
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
    NEXT_PUBLIC_WS_URL: process.env.NEXT_PUBLIC_WS_URL ?? 'ws://localhost:8085',
  },
};

module.exports = nextConfig;
