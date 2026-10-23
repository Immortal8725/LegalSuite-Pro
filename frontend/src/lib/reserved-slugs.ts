/** Keep in step with AuthService.SYSTEM_SLUGS and DirectoryCatalog.PRACTICE_AREAS. */
export const SYSTEM_SLUGS = [
  "www", "api", "mail", "smtp", "pop", "imap", "ftp", "app", "admin", "platform", "owlaw", "support", "status",
  "find-a-lawyer", "firm", "login", "register", "portal", "legal", "help", "security", "dashboard", "static", "assets",
  "cdn", "docs", "blog", "billing", "pay", "sign", "webmail", "ns1", "ns2", "dev", "staging", "test",
];
export const PRACTICE_AREA_SLUGS = [
  "divorce-and-family", "raf-claims", "personal-injury", "conveyancing-and-property", "criminal", "labour-and-employment",
  "wills-and-estates", "commercial-and-contracts", "debt-collection", "immigration", "notarial", "litigation", "medical-negligence", "tax",
];
export const RESERVED_SLUGS = new Set([...SYSTEM_SLUGS, ...PRACTICE_AREA_SLUGS]);
export const RESERVED_MESSAGE =
  "That web address is reserved by Owlaw because it is a system word or a type of lawyer on Find a Lawyer. Please choose another, for example add your surname or town.";

export function slugify(s: string): string {
  return s.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "").slice(0, 63).replace(/-+$/, "");
}
export const isReservedSlug = (s: string) => RESERVED_SLUGS.has(slugify(s));
