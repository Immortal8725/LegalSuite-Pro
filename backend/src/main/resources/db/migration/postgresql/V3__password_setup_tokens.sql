-- One-time set-password links for invited users. Only the SHA-256 of each token is stored.

create table if not exists password_setup_tokens (
    created_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone not null,
    used_at timestamp(6) with time zone,
    id uuid not null,
    tenant_id uuid not null,
    user_id uuid not null,
    purpose character varying(32) not null,
    token_hash character varying(64) not null
);
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='password_setup_tokens' and pg_get_constraintdef(k.oid)='PRIMARY KEY (id)') then alter table password_setup_tokens add constraint password_setup_tokens_pkey PRIMARY KEY (id); end if; end $$;
do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='password_setup_tokens' and pg_get_constraintdef(k.oid)='UNIQUE (token_hash)') then alter table password_setup_tokens add constraint password_setup_tokens_token_hash_key UNIQUE (token_hash); end if; end $$;
CREATE INDEX IF NOT EXISTS idx_password_setup_tokens_user ON password_setup_tokens USING btree (user_id);
