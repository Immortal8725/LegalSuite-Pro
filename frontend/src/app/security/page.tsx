import Link from "next/link";

export const metadata = { title: "Security and POPIA", alternates: { canonical: "/security" }, description: "How Owlaw protects law firm and client data under POPIA." };

const ITEMS: [string, string][] = [
  ["Your data stays separate", "Every firm's records are isolated by tenant on every request. Staff only see what their role allows, and administrators control access."],
  ["Encryption", "All traffic uses HTTPS. Passwords are hashed with BCrypt. Payment gateway credentials are encrypted with AES-256-GCM, and nightly backups are encrypted with AES-256-GCM before they leave the server."],
  ["Audit trail", "Sign-ins, imports, trust transactions and other sensitive actions are written to an audit log your firm can review."],
  ["Backups and recovery", "Backups run every night and are kept for 14 days, with a documented and tested restore procedure. Live health is shown on our status page."],
  ["Client money", "Owlaw never receives or holds client funds. Online invoice payments go directly to your firm's own PayFast account."],
  ["POPIA: our role", "Your firm is the responsible party for client personal information. Owlaw acts as an operator that processes it only on your instructions, to provide the service, under section 21 of POPIA."],
  ["POPIA: what we collect", "Account details for your users, and the client and matter information your firm captures. We do not sell personal information or use it for advertising."],
  ["POPIA: your rights", "You can export your data at any time and ask us to correct or delete it. When a firm closes its account, its data is deleted after the agreed retention period. Data subjects may also complain to the Information Regulator."],
  ["Security incidents", "If we become aware of a compromise affecting your data we will notify you without undue delay so you can meet your section 22 obligations."],
  ["Contact", "Information officer and security reports: privacy@owlaw.co.za."],
];

export default function SecurityPage() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <p className="text-sm"><Link href="/" className="underline">Owlaw</Link></p>
      <h1 className="mt-2 text-3xl font-semibold">Security and POPIA</h1>
      <p className="mt-2 text-slate-600">How we protect your firm and your clients&apos; personal information.</p>
      {ITEMS.map(([h, b]) => (
        <section key={h} className="mt-6">
          <h2 className="text-lg font-semibold">{h}</h2>
          <p className="mt-1 leading-relaxed text-slate-700">{b}</p>
        </section>
      ))}
      <p className="mt-10 text-sm"><Link href="/status" className="underline">System status</Link> · <Link href="/help" className="underline">Help centre</Link></p>
    </main>
  );
}
