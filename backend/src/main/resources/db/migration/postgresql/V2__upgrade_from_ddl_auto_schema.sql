-- Bring a database created by the older Hibernate ddl-auto build (main) up to the current model.
-- Every statement is idempotent, so this is a no-op on a fresh install that ran V1.
-- B4: conflict_checks.results_json was varchar(255) and overflowed with three or more matches.

create table if not exists billing_accounts (
    seat_count integer not null,
    last_amount_cents bigint,
    last_payment_at timestamp(6) with time zone,
    seat_active_until timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    environment character varying(16),
    id uuid not null,
    tenant_id uuid not null,
    last_m_payment_id character varying(48),
    last_pf_payment_id character varying(64),
    payfast_token character varying(128),
    seat_status character varying(255)
);
alter table conflict_checks alter column results_json type text;
create table if not exists invoice_payments (
    amount numeric(38,2),
    paid_at date,
    created_at timestamp(6) with time zone,
    id uuid not null,
    invoice_id uuid,
    proof_id uuid,
    recorded_by uuid,
    tenant_id uuid,
    trust_transaction_id uuid,
    note character varying(2000),
    method character varying(255),
    reference character varying(255)
);
create table if not exists invoice_write_offs (
    amount numeric(38,2),
    created_at timestamp(6) with time zone,
    created_by uuid,
    id uuid not null,
    invoice_id uuid,
    tenant_id uuid,
    reason character varying(2000)
);
alter table invoices add column if not exists write_off_amount numeric(38,2);
alter table invoices add column if not exists write_off_at timestamp(6) with time zone;
alter table invoices add column if not exists write_off_by uuid;
alter table invoices add column if not exists write_off_note character varying(2000);
create table if not exists minutes_invoices (
    minutes integer not null,
    period character varying(7) not null,
    amount_cents bigint not null,
    charged_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    id uuid not null,
    status character varying(16),
    tenant_id uuid,
    m_payment_id character varying(48),
    pf_payment_id character varying(64),
    note character varying(500)
);
create table if not exists payment_proofs (
    amount_claimed numeric(38,2),
    reviewed_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    document_id uuid,
    id uuid not null,
    invoice_id uuid,
    payment_id uuid,
    reviewed_by uuid,
    submitted_by uuid,
    tenant_id uuid,
    note character varying(2000),
    review_note character varying(2000),
    reference character varying(255),
    status character varying(255)
);
create table if not exists product_checkouts (
    seat_count integer not null,
    amount_cents bigint not null,
    created_at timestamp(6) with time zone,
    id uuid not null,
    kind character varying(16),
    period character varying(16),
    status character varying(16),
    tenant_id uuid,
    m_payment_id character varying(48) not null
);
create table if not exists product_payments (
    amount_cents bigint not null,
    created_at timestamp(6) with time zone,
    id uuid not null,
    kind character varying(16),
    period character varying(16),
    tenant_id uuid,
    payment_status character varying(32),
    m_payment_id character varying(48),
    pf_payment_id character varying(64),
    token character varying(128)
);
alter table users add column if not exists password_change_required boolean;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='billing_accounts' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table billing_accounts add constraint billing_accounts_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='billing_accounts' and pg_get_constraintdef(k.oid)='UNIQUE (tenant_id)') then alter table billing_accounts add constraint billing_accounts_tenant_id_key UNIQUE (tenant_id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='invoice_payments' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table invoice_payments add constraint invoice_payments_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='invoice_write_offs' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table invoice_write_offs add constraint invoice_write_offs_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='minutes_invoices' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table minutes_invoices add constraint minutes_invoices_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='payment_proofs' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table payment_proofs add constraint payment_proofs_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='product_checkouts' and pg_get_constraintdef(k.oid)='UNIQUE (m_payment_id)') then alter table product_checkouts add constraint product_checkouts_m_payment_id_key UNIQUE (m_payment_id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='product_checkouts' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table product_checkouts add constraint product_checkouts_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='product_payments' and pg_get_constraintdef(k.oid)='UNIQUE (pf_payment_id)') then alter table product_payments add constraint product_payments_pf_payment_id_key UNIQUE (pf_payment_id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='product_payments' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table product_payments add constraint product_payments_pkey PRIMARY KEY (id); end if; end $$;
