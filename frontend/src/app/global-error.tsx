"use client";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en-ZA">
      <body style={{ fontFamily: "system-ui, sans-serif", background: "#f8fafc", margin: 0 }}>
        <main style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
          <div style={{ maxWidth: 420, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 16, padding: 32, textAlign: "center" }}>
            <p style={{ color: "#8a6a22", fontWeight: 700, letterSpacing: 1, textTransform: "uppercase", fontSize: 13 }}>Owlaw</p>
            <h1 style={{ color: "#1e2a44", fontSize: 22 }}>Owlaw could not load</h1>
            <p style={{ color: "#475569", fontSize: 14 }}>
              Try again in a moment.{error.digest ? ` Reference ${error.digest}.` : ""}
            </p>
            <button type="button" onClick={reset} style={{ marginTop: 12, background: "#1e2a44", color: "#fff", border: 0, borderRadius: 8, padding: "8px 16px", fontWeight: 600 }}>
              Try again
            </button>
          </div>
        </main>
      </body>
    </html>
  );
}
