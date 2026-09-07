import type { NextConfig } from "next";

const API = process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:18081";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${API}/api/:path*` },
      { source: "/ws/:path*", destination: `${API}/ws/:path*` },
    ];
  },
};

export default nextConfig;
