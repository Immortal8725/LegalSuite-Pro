-- Owlaw blog (phase 1): posts written by the Owlaw operator only.
CREATE TABLE IF NOT EXISTS blog_posts (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(400),
    body TEXT,
    cover_ref VARCHAR(500),
    cover_type VARCHAR(60),
    practice_area VARCHAR(80),
    city VARCHAR(80),
    author_name VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    seo_title VARCHAR(200),
    seo_description VARCHAR(300),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_blog_posts_slug UNIQUE (slug)
);
CREATE INDEX IF NOT EXISTS idx_blog_posts_status ON blog_posts (status, published_at DESC);

-- Three outlines left as drafts for an attorney to write and check. Nothing is published.
INSERT INTO blog_posts (id, slug, title, summary, body, practice_area, author_name, status, created_at, updated_at)
SELECT gen_random_uuid(), 'raf-claim-steps', 'Claiming from the RAF: the steps (outline)', 'Draft for attorney review.',
 E'> Draft for attorney review. Do not publish until an attorney has written and checked every section.\n\n## Who can claim\n\n## What to gather first\n\n## Lodging the claim\n\n## Time limits\n\n## What happens after you lodge\n\n## When to speak to an attorney',
 'raf-claims', 'Owlaw', 'draft', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM blog_posts WHERE slug = 'raf-claim-steps');
INSERT INTO blog_posts (id, slug, title, summary, body, practice_area, author_name, status, created_at, updated_at)
SELECT gen_random_uuid(), 'unfair-dismissal-ccma', 'Unfair dismissal and the CCMA (outline)', 'Draft for attorney review.',
 E'> Draft for attorney review. Do not publish until an attorney has written and checked every section.\n\n## What counts as a dismissal\n\n## Time limit to refer a dispute\n\n## Referring to the CCMA\n\n## Conciliation\n\n## Arbitration or the Labour Court\n\n## When to speak to an attorney',
 'labour-and-employment', 'Owlaw', 'draft', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM blog_posts WHERE slug = 'unfair-dismissal-ccma');
INSERT INTO blog_posts (id, slug, title, summary, body, practice_area, author_name, status, created_at, updated_at)
SELECT gen_random_uuid(), 'divorce-in-south-africa', 'Getting divorced in South Africa (outline)', 'Draft for attorney review.',
 E'> Draft for attorney review. Do not publish until an attorney has written and checked every section.\n\n## Uncontested and contested divorce\n\n## Marriage regimes and property\n\n## Children\n\n## Maintenance\n\n## Costs and how long it takes\n\n## When to speak to an attorney',
 'divorce-and-family', 'Owlaw', 'draft', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM blog_posts WHERE slug = 'divorce-in-south-africa');
