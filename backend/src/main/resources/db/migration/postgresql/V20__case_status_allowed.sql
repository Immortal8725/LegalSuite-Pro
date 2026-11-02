-- Matter status is now checked against a fixed list in CaseStatusRules.
-- Tidy older rows so every stored value is on that list. Nothing is deleted.
UPDATE cases SET status = lower(trim(status)) WHERE status IS NOT NULL AND status <> lower(trim(status));
UPDATE cases SET status = replace(replace(status, ' ', '_'), '-', '_') WHERE status LIKE '% %' OR status LIKE '%-%';
UPDATE cases SET status = 'open'
 WHERE status IS NULL
    OR status NOT IN ('limited','intake','open','pending','on_hold','discovery','mediation',
                      'issued','hearing','trial','settled','closed','archived','declined');
-- A matter that is not finished should not carry a closing date.
UPDATE cases SET date_closed = NULL
 WHERE date_closed IS NOT NULL AND status NOT IN ('settled','closed','archived','declined');
