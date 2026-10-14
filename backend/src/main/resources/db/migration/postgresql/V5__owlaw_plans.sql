-- Owlaw plans (prices in rand, excluding VAT): Solo R899, Firm R1,199 and Practice R1,599 per attorney per month.
-- Only databases that already have the old single "light" plan change here. A fresh database has no plans yet,
-- and the application seeds all three on first start.
-- Tenants on Light keep their plan row, which becomes Firm, so nothing is reassigned and nobody is locked out.

INSERT INTO plans (id, name, slug, description, price_monthly, max_users, max_cases, max_storage_gb,
                   included_voice_minutes, features_json, sort_order, active, created_at)
SELECT gen_random_uuid(), 'Solo', 'solo',
       'R899 per month for one attorney. Matters, docket clocks, billing, invoices, payments, write-offs, proof-of-payment review, section 86 trust including trust-to-fee, documents, calendar, the firm number, SMS, WhatsApp and email, and 100 assistant actions. The public website is an add-on at R499 per month plus R1,500 setup.',
       899, 1, -1, NULL, 0,
       '{"currency":"ZAR","perAttorney":true,"maxAttorneys":1,"trustIncluded":true,"websiteIncluded":false,"aiActionsPerAttorney":100,"staffSeats":false,"staffSeatMonthlyZar":349,"callRecording":false,"prioritySupport":false,"includedPstnMinutes":0,"callPerMinuteZar":1.49,"landlineMonthlyZar":149,"landlinePerMinuteZar":8.90,"websiteAddonMonthlyZar":499,"websiteAddonSetupZar":1500}',
       1, true, now()
WHERE EXISTS (SELECT 1 FROM plans WHERE slug = 'light')
  AND NOT EXISTS (SELECT 1 FROM plans WHERE slug = 'solo');

INSERT INTO plans (id, name, slug, description, price_monthly, max_users, max_cases, max_storage_gb,
                   included_voice_minutes, features_json, sort_order, active, created_at)
SELECT gen_random_uuid(), 'Practice', 'practice',
       'R1,599 per attorney per month. Everything in Firm plus 1,000 assistant actions per attorney, call recording, advanced reporting, priority support and fast-track for website feature requests.',
       1599, -1, -1, NULL, 0,
       '{"currency":"ZAR","perAttorney":true,"maxAttorneys":null,"trustIncluded":true,"websiteIncluded":true,"aiActionsPerAttorney":1000,"staffSeats":true,"staffSeatMonthlyZar":349,"callRecording":true,"prioritySupport":true,"includedPstnMinutes":0,"callPerMinuteZar":1.49,"landlineMonthlyZar":149,"landlinePerMinuteZar":8.90}',
       3, true, now()
WHERE EXISTS (SELECT 1 FROM plans WHERE slug = 'light')
  AND NOT EXISTS (SELECT 1 FROM plans WHERE slug = 'practice');

UPDATE plans
SET name = 'Firm',
    slug = 'firm',
    description = 'R1,199 per attorney per month. Everything in Solo plus the public website (people, expertise, insights, contact, theme and accent colours), 300 assistant actions per attorney, and staff seats at R349.',
    price_monthly = 1199,
    max_users = -1,
    included_voice_minutes = 0,
    features_json = '{"currency":"ZAR","perAttorney":true,"maxAttorneys":null,"trustIncluded":true,"websiteIncluded":true,"aiActionsPerAttorney":300,"staffSeats":true,"staffSeatMonthlyZar":349,"callRecording":false,"prioritySupport":false,"includedPstnMinutes":0,"callPerMinuteZar":1.49,"landlineMonthlyZar":149,"landlinePerMinuteZar":8.90}',
    sort_order = 2
WHERE slug = 'light'
  AND NOT EXISTS (SELECT 1 FROM plans WHERE slug = 'firm');

UPDATE modules SET min_plan_slug = 'solo' WHERE min_plan_slug = 'light';
UPDATE modules
SET description = 'Included in every plan. Per-client section 86 or IOLTA ledgers. Not a separate month-end charge.'
WHERE slug = 'trust' AND description LIKE 'Included in Light%';
