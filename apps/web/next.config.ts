import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output is only enabled for the Docker build (NEXT_STANDALONE=1),
  // so local `next start` keeps working unchanged.
  output: process.env.NEXT_STANDALONE === "1" ? "standalone" : undefined,
  allowedDevOrigins: ["*.e2b.app", "*.monkeycode-ai.live", "localhost:3000"],
  experimental: {
    serverActions: {
      allowedOrigins: ["*.e2b.app", "*.monkeycode-ai.live", "localhost:3000"],
    },
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
