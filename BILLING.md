# Product billing (PayFast Aggregation)

LegalSuite charges firms for the product. That is separate from client fee invoices and from section 86 / IOLTA trust. Client invoices stay on **Client invoices**. Trust stays on **Trust Accounting**. This document is only the operator's bill: the Light seat, and public-network minutes.

PayFast Aggregation is the payments provider. Stripe is not used for this bill. PayGate is not used.

## What is charged

| Charge | How | Amount |
| --- | --- | --- |
| Light seat | PayFast subscription, monthly, until cancelled | R1,199 per seat per month |
| Phone minutes | Tokenised adhoc charge, manual trigger in the pilot | Sum of public-network call cost for the month |

One seat is **119900 cents** (R1,199.00). Two seats are 239800 cents. The checkout form sends rands with two decimals (`1199.00`) because that is what PayFast's process endpoint expects. The adhoc API sends cents (`119900`) because that is what PayFast's API expects.

PayFast wire values for the seat:

- `subscription_type=1` (PayFast's recurring subscription)
- `frequency=3` (monthly)
- `cycles=0` (until cancelled)

A second checkout, **Save a card token**, uses `subscription_type=2` and amount `0.00`. The seat subscription also stores a token when the ITN includes one. Minute charges use whichever token is on the billing account.

## VAT

The operator issues its own tax invoice. PayFast is not asked to add VAT on top of the R1,199 seat. Do not treat the PayFast receipt as the tax invoice.

## Environment

Copy `.env.example`. Do not commit real values.

| Variable | Purpose |
| --- | --- |
| `PAYFAST_MERCHANT_ID` | Merchant id from PayFast |
| `PAYFAST_MERCHANT_KEY` | Merchant key. It is posted in the checkout form because PayFast requires it. It is not a card number. |
| `PAYFAST_PASSPHRASE` | Salt for the MD5 signature. Set the same value in the PayFast merchant security settings. Leave blank only when that merchant has no passphrase. Never put it in the browser. |
| `PAYFAST_ENV` | `sandbox` or `live`. Default `sandbox`. Anything else refuses checkout. |
| `PAYFAST_RETURN_URL` | Browser page after a successful redirect, for example `https://your-host/product-billing/return` |
| `PAYFAST_CANCEL_URL` | Browser page after cancel, for example `https://your-host/product-billing/cancel` |
| `PAYFAST_NOTIFY_URL` | ITN target on this API: `https://your-api/api/v1/product-billing/payfast/itn` |

Return and cancel are pages in the web app. Notify must hit the Spring process. If the web host proxies `/api` to Spring, the public site origin can be the notify host. PayFast must be able to reach it. `http://127.0.0.1` is not reachable from PayFast.

The merchant key travels in the signed checkout form. The passphrase does not.

## Sandbox, then live

1. Create a PayFast sandbox merchant at `https://sandbox.payfast.co.za`. Enable subscriptions and tokenization on that merchant.
2. Set the salt passphrase in the sandbox security settings and copy that exact value to `PAYFAST_PASSPHRASE`. The checkout signature appends it. The adhoc signature sorts it in with the other API fields.
3. Keep `PAYFAST_ENV=sandbox`.
4. Point return, cancel, and notify at a public HTTPS host.
5. Sign in as a firm owner or director. Open **Product billing**. Subscribe. PayFast's sandbox card completes the redirect.
6. Confirm the ITN in the PayFast dashboard if it does not arrive. The seat status becomes `active` only after signature check and server confirm. A browser return is not proof of payment.
7. Place a public-network call with a non-zero per-minute rate, then use **Invoice minutes and charge**. With no token, the invoice is stored and no charge is sent.

The developer-docs sample (`merchant_id` `10000100`, passphrase `jt7NOE43FZPn`) is rejected by the sandbox. That merchant accepts an unsigned once-off form. A signature is checked when one is sent, and `jt7NOE43FZPn` is not the salt. Subscriptions need a passphrase that matches the merchant. PayFast's support article lists a separate sandbox merchant that already has a salt (`merchant_id` `10004002`, passphrase `payfast`). Prefer your own sandbox merchant. Do not commit either pair.

A PayFast page that says "Generated signature does not match submitted signature" means the env passphrase is not the salt on that merchant id. The form field order in this app matches PayFast's attribute order, and that order is what the sandbox accepts when the salt matches.

Live cutover, after PayFast KYC:

1. Create the live merchant. Set a passphrase.
2. Replace the four secrets and the three URLs.
3. Set `PAYFAST_ENV=live`.
4. Restart the API. Checkout then posts to `https://www.payfast.co.za/eng/process`. Adhoc calls omit `testing=true`.
5. Run one sandbox-sized live payment you can refund before inviting firms.

Hosts, fixed in code:

| | Sandbox | Live |
| --- | --- | --- |
| Checkout | `https://sandbox.payfast.co.za/eng/process` | `https://www.payfast.co.za/eng/process` |
| ITN confirm | `https://sandbox.payfast.co.za/eng/query/validate` | `https://www.payfast.co.za/eng/query/validate` |
| Adhoc | `https://api.payfast.co.za/subscriptions/{token}/adhoc?testing=true` | `https://api.payfast.co.za/subscriptions/{token}/adhoc` |

## ITN

`POST /api/v1/product-billing/payfast/itn` is public. It does not trust a browser session.

1. Rebuild the MD5 from the posted fields, in the order received, PHP `urlencode` style, with the passphrase appended when one is set. A bad signature returns HTTP 400 and writes nothing.
2. Ignore a repeat `pf_payment_id`.
3. POST the fields back to PayFast's validate URL. The body must be `VALID`. A network failure returns HTTP 400 so PayFast retries. Tests stub this step. CI does not call PayFast.
4. Compare `amount_gross` to the checkout amount. A mismatch is stored as `rejected` and does not activate a seat.
5. `COMPLETE` on a seat sets the billing account active, stores `pf_payment_id`, `m_payment_id`, the token, and the amount, and extends the seat by one month. `COMPLETE` on minutes marks that minute invoice charged and does not sell a seat. A token-only ITN stores the token and does not activate a seat.

The full token is not returned to the product billing page. The page shows that a token exists and the last four characters.

## API

Authenticated firm staff can read `GET /api/v1/product-billing`.

Owners, directors, and attorneys:

- `POST /api/v1/product-billing/seats/checkout` with `{ "seats": 1 }`
- `POST /api/v1/product-billing/token/checkout`
- `POST /api/v1/product-billing/minutes/charge` with `{ "period": "2026-09" }`

There is no cron. The minute charge is the pilot job. Run it from the product billing page.

## Still a person

- PayFast account opening and KYC
- Sandbox merchant id, key, and passphrase in the server environment
- Public HTTPS for the notify URL
- Subscriptions and tokenization enabled on the merchant
- Live keys only after KYC, with `PAYFAST_ENV=live`
- The operator's own VAT tax invoices
- A per-minute carrier rate, or minute invoices stay at zero and are not sent to PayFast
