import type { NextConfig } from "next";

const API = process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:18081";
const dev = process.env.NODE_ENV !== "production";

// The browser only talks to this origin (the API is proxied through the rewrites below),
// plus PayFast for the product-billing checkout form post.
const ADSENSE_ON = /^ca-pub-\d{10,20}$/.test((process.env.NEXT_PUBLIC_ADSENSE_CLIENT || "").trim());
// Google AdSense hosts, allowed on /blog pages only and only when AdSense is configured.
const ADS = {
  script: "https://pagead2.googlesyndication.com https://*.googlesyndication.com https://*.adtrafficquality.google https://*.googletagservices.com https://www.google.com",
  frame: "https://*.googlesyndication.com https://*.doubleclick.net https://*.adtrafficquality.google https://www.google.com",
  connect: "https://*.googlesyndication.com https://*.doubleclick.net https://*.adtrafficquality.google https://*.google.com",
};

// In-browser background removal on the website settings page: MediaPipe's WebAssembly from jsDelivr
// and its portrait model from Google's model bucket. Allowed on that page only.
const PHOTO_TOOL = { script: "'wasm-unsafe-eval' https://cdn.jsdelivr.net", connect: "https://cdn.jsdelivr.net https://storage.googleapis.com" };

const buildCsp = (ads: boolean, photo = false) => [
  "default-src 'self'",
  // Next.js injects inline bootstrap scripts; dev mode also needs eval for fast refresh.
  `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}${ads ? ` ${ADS.script}` : ""}${photo ? ` ${PHOTO_TOOL.script}` : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  `connect-src 'self' ws: wss: https://api.github.com${ads ? ` ${ADS.connect}` : ""}${photo ? ` ${PHOTO_TOOL.connect}` : ""}`,
  "worker-src 'self' blob:",
  "media-src 'self' blob:",
  `frame-src 'self' blob:${ads ? ` ${ADS.frame}` : ""}`,
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self' https://sandbox.payfast.co.za https://www.payfast.co.za",
  "frame-ancestors 'none'",
].join("; ");
const csp = buildCsp(false);

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=(self), payment=()" },
  ...(dev ? [] : [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }]),
];

const nextConfig: NextConfig = {
  // Uploaded firm photos are stored at up to 3200 px; the optimiser serves 800, 1600 or 3200 px copies to suit the screen.
  images: {
    deviceSizes: [640, 800, 1080, 1600, 2048, 3200],
    imageSizes: [96, 128, 256, 384],
    qualities: [75, 90],
    formats: ["image/webp"],
    minimumCacheTTL: 2592000,
  },
  // The Docker image sets NEXT_OUTPUT=standalone; local `next start` uses the normal build.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  poweredByHeader: false,
  experimental: {
    // Uploads go through the /api rewrite; let 50 MB files (plus form overhead) through and give slow lines time.
    middlewareClientMaxBodySize: "52mb",
    proxyTimeout: 300_000,
  },
  async headers() {
    // Signed-in app, portal and one-time links: never indexed, even if linked from elsewhere.
    const noindex = [
      "dashboard", "cases", "clients", "contacts", "calendar", "tasks", "time", "billing", "trust", "documents", "esign", "expenses",
      "fica", "conflicts", "leads", "messages", "reports", "settings", "team", "templates", "voice", "website", "usage", "modules",
      "integrations", "import", "audit", "ai", "bundles", "fitness", "staff-activity", "support-access", "directory-listing",
      "product-billing", "platform", "portal", "onboarding", "change-password", "set-password", "two-factor", "pay", "sign", "login",
    ].flatMap((p) => [`/${p}`, `/${p}/:path*`]);
    return [
      { source: "/:path*", headers: securityHeaders },
      // Later rules win for the same header: the blog alone gets the AdSense hosts.
      ...(ADSENSE_ON
        ? ["/blog", "/blog/:slug"].map((source) => ({ source, headers: [{ key: "Content-Security-Policy", value: buildCsp(true) }] }))
        : []),
      { source: "/website", headers: [{ key: "Content-Security-Policy", value: buildCsp(false, true) }] },
      ...noindex.map((source) => ({ source, headers: [{ key: "X-Robots-Tag", value: "noindex, nofollow" }] })),
    ];
  },
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${API}/api/:path*` },
      { source: "/ws/:path*", destination: `${API}/ws/:path*` },
    ];
  },
};

export default nextConfig;
