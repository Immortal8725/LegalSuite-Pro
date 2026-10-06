# Tenant public site

Each firm can have a public marketing site at `/firm/{slug}`. The staff desk stays the dense practice app. This note is how flags and approval work.

Schema is JPA on the same database the API already uses (`ddl-auto: create-drop` on H2, `update` on Postgres). There is no Flyway changelog in this repo.

## Records

`tenant_public_sites` (one row per firm):

- `publishStatus`: `draft`, `pending_approval`, `published`, or `rejected`
- Draft branding: public name, tagline, about, and an accent key from a closed set (`navy`, `forest`, `oxblood`, `copper`)
- Live branding: the name, tagline, about, and accent actually shown. A draft edit does not replace them until approval
- `brandingStatus`: `none`, `pending`, `approved`, or `rejected`

`public_site_features` (one row per firm per feature):

- `requested`: the firm wants it on
- `approvalStatus`: `none`, `pending`, `approved`, or `rejected`
- `note`: optional note from the platform operator

Features: `people`, `insights`, `situations` (Who we help), `fees`, `whatsapp`, `booking` (enquiry form), `newsletter`, `recognition`.

Home, the expertise hub, the contact page (address and phone), and the legal footer are part of a published site. They are not separate feature flags. The enquiry form itself is the `booking` feature. People, insights, situations, fees, WhatsApp, newsletter, and recognition stay off unless they are live.

## When a feature is live

A feature is on the public site only when all three are true:

1. `publishStatus` is `published`
2. `requested` is true
3. `approvalStatus` is `approved`

Turning a feature off is immediate. Turning it on, or publishing the site, waits for the platform operator.

## Who can call what

| Caller | API | Scope |
| --- | --- | --- |
| Firm owner (`owner`, `partner`, or `director`) | `GET/PUT /api/v1/public-site`, `POST /api/v1/public-site/submit` | Own firm only |
| Platform operator (`superadmin`) | `GET /api/v1/platform/public-sites/queue` and the approve/reject posts under `/api/v1/platform/public-sites/{tenantId}/` | Every firm |
| Public | `GET /api/v1/public/sites/{slug}` and `GET /api/v1/landing/{slug}` | Published site, live features only |
| Public | `POST /api/v1/intake/{slug}` | Refused unless `booking` is live |
| Public | `POST /api/v1/public/sites/{slug}/subscribe` | Refused unless `newsletter` is live. Consent must be ticked |

Associates, client-portal users, and a firm owner acting on another firm cannot approve. A firm cannot grant itself `superadmin` from Team.

The public payload does not include hourly rates, bar numbers, authenticator state, module flags, FFC numbers, or the approval map. An unpublished site returns the firm name and slug only.

## Demo

- Ndlovu & Partners is published. People, insights, recognition, WhatsApp, and enquiry are approved. Newsletter and Who we help are requested and still pending, so they are absent from `/firm/ndlovu-partners`.
- Smith & Associates is `pending_approval`, so `/firm/smith-associates` is not live.
- Operator: firm slug `legalsuite`, `ops@legalsuite.pro`, password `password`. The queue is **Site approvals**.

Subdomain: `ndlovu-partners.localhost` rewrites marketing paths onto `/firm/ndlovu-partners`. Set `NEXT_PUBLIC_PUBLIC_SITE_ROOT` for another parent domain. Staff routes are not rewritten.

## Deferred

Full CMS editing, custom domains, photo upload, geo personalisation, carousels, and sending the newsletter. Recognition items in the demo are seeded, not edited in the screen. Portrait blocks use Pexels placeholder photographs until the firm uploads its own. Legal footer pages are placeholders for counsel to replace.
