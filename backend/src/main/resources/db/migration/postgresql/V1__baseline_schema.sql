-- LegalSuite baseline schema for a fresh Postgres database.
-- Generated from the JPA entity model (Hibernate create on PostgreSQL 17), then trimmed.
-- A database that already has tables from an older ddl-auto build is baselined at V1
-- (spring.flyway.baseline-on-migrate) and brought up to date by V2 instead.

CREATE TABLE audit_logs (
    created_at timestamp(6) with time zone,
    actor_id uuid,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    action character varying(255) NOT NULL,
    actor_email character varying(255),
    detail text,
    entity_id character varying(255),
    entity_type character varying(255)
);

CREATE TABLE billing_accounts (
    seat_count integer NOT NULL,
    last_amount_cents bigint,
    last_payment_at timestamp(6) with time zone,
    seat_active_until timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    environment character varying(16),
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    last_m_payment_id character varying(48),
    last_pf_payment_id character varying(64),
    payfast_token character varying(128),
    seat_status character varying(255)
);

CREATE TABLE call_records (
    billable boolean NOT NULL,
    cost_per_minute numeric(38,2),
    duration_seconds integer NOT NULL,
    non_matter boolean NOT NULL,
    recording_consent_given boolean NOT NULL,
    recording_enabled boolean NOT NULL,
    total_cost numeric(38,2),
    answered_at timestamp(6) with time zone,
    ended_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone,
    callee_user_id uuid,
    caller_user_id uuid,
    case_id uuid,
    client_id uuid,
    firm_phone_number_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    time_entry_id uuid,
    from_number character varying(20),
    staff_callback_number character varying(20),
    to_number character varying(20),
    caller_id_kind character varying(32),
    bridge_token character varying(64),
    twilio_call_sid character varying(64),
    notes character varying(4000),
    transcript character varying(8000),
    call_type character varying(255),
    direction character varying(255),
    recording_url character varying(255),
    status character varying(255)
);

CREATE TABLE cases (
    accrual_date date,
    appearance_authorized boolean NOT NULL,
    billing_rate numeric(38,2),
    budget numeric(38,2),
    conflict_checked boolean NOT NULL,
    date_closed date,
    date_opened date,
    discovery_date date,
    governmental_defendant boolean NOT NULL,
    hit_and_run boolean NOT NULL,
    notice_served boolean NOT NULL,
    notice_served_date date,
    pending_retainer_amount numeric(38,2),
    plaintiff_dob date,
    probate_opened date,
    raf_claim_lodged boolean NOT NULL,
    raf_lodged_date date,
    statute_of_limitations date,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    client_id uuid,
    conflict_waiver_signature_id uuid,
    engagement_signature_id uuid,
    id uuid NOT NULL,
    lead_attorney_id uuid,
    pending_trust_account_id uuid,
    tenant_id uuid,
    conflict_waiver_hash character varying(128),
    sol_citation character varying(500),
    sol_reason character varying(2000),
    tags_json character varying(2000),
    description character varying(4000),
    docket_clocks_json character varying(4000),
    billing_type character varying(255),
    case_number character varying(255),
    case_type character varying(255),
    controlling_kind character varying(255),
    court_case_number character varying(255),
    court_name character varying(255),
    docket_track character varying(255),
    engagement_status character varying(255),
    judge_name character varying(255),
    opposing_counsel character varying(255),
    opposing_party character varying(255),
    practice_area character varying(255),
    priority character varying(255),
    sol_rule_id character varying(255),
    status character varying(255),
    title character varying(255)
);

CREATE TABLE clients (
    date_of_birth date,
    default_hourly_rate numeric(38,2),
    portal_enabled boolean NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    assigned_attorney_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    tags_json character varying(2000),
    notes character varying(4000),
    address_line1 character varying(255),
    city character varying(255),
    company_name character varying(255),
    email character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    mobile character varying(255),
    phone character varying(255),
    portal_password_hash character varying(255),
    source character varying(255),
    state character varying(255),
    status character varying(255),
    type character varying(255),
    zip character varying(255)
);

CREATE TABLE conflict_checks (
    match_count integer NOT NULL,
    created_at timestamp(6) with time zone,
    created_by uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    results_json text,
    search_name character varying(255),
    status character varying(255)
);

CREATE TABLE connected_integrations (
    connected boolean NOT NULL,
    connected_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    provider character varying(255) NOT NULL,
    status_note character varying(255)
);

CREATE TABLE contacts (
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid,
    notes character varying(2000),
    company character varying(255),
    email character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    phone character varying(255),
    title character varying(255),
    type character varying(255)
);

CREATE TABLE conversations (
    created_at timestamp(6) with time zone,
    last_message_at timestamp(6) with time zone,
    case_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    participant_ids_json character varying(2000),
    title character varying(255),
    type character varying(255)
);

CREATE TABLE document_templates (
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    body text,
    category character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);

CREATE TABLE documents (
    privileged boolean NOT NULL,
    created_at timestamp(6) with time zone,
    size_bytes bigint NOT NULL,
    case_id uuid,
    client_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    uploaded_by uuid,
    tags_json character varying(2000),
    category character varying(255),
    mime_type character varying(255),
    name character varying(255),
    original_name character varying(255),
    storage_path character varying(255)
);

CREATE TABLE events (
    all_day boolean NOT NULL,
    created_at timestamp(6) with time zone,
    end_time timestamp(6) with time zone,
    start_time timestamp(6) with time zone,
    case_id uuid,
    created_by uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    description character varying(2000),
    location character varying(255),
    status character varying(255),
    title character varying(255),
    type character varying(255)
);

CREATE TABLE expenses (
    amount numeric(38,2),
    billable boolean NOT NULL,
    billed boolean NOT NULL,
    date date,
    created_at timestamp(6) with time zone,
    case_id uuid,
    id uuid NOT NULL,
    invoice_id uuid,
    tenant_id uuid,
    user_id uuid,
    description character varying(2000),
    category character varying(255),
    status character varying(255),
    vendor character varying(255)
);

CREATE TABLE firm_phone_numbers (
    default_outbound boolean NOT NULL,
    country character varying(8),
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    verified_at timestamp(6) with time zone,
    validation_code character varying(12),
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    e164 character varying(20) NOT NULL,
    kind character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    twilio_sid character varying(64),
    friendly_name character varying(80),
    locality character varying(255),
    region character varying(255)
);

CREATE TABLE invoice_payments (
    amount numeric(38,2),
    paid_at date,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    invoice_id uuid,
    proof_id uuid,
    recorded_by uuid,
    tenant_id uuid,
    trust_transaction_id uuid,
    note character varying(2000),
    method character varying(255),
    reference character varying(255)
);

CREATE TABLE invoice_write_offs (
    amount numeric(38,2),
    created_at timestamp(6) with time zone,
    created_by uuid,
    id uuid NOT NULL,
    invoice_id uuid,
    tenant_id uuid,
    reason character varying(2000)
);

CREATE TABLE invoices (
    amount_paid numeric(38,2),
    date_due date,
    date_issued date,
    subtotal numeric(38,2),
    tax_amount numeric(38,2),
    total numeric(38,2),
    write_off_amount numeric(38,2),
    created_at timestamp(6) with time zone,
    write_off_at timestamp(6) with time zone,
    case_id uuid,
    client_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    write_off_by uuid,
    notes character varying(2000),
    write_off_note character varying(2000),
    line_items_json character varying(8000),
    invoice_number character varying(255),
    status character varying(255)
);

CREATE TABLE landing_pages (
    published boolean NOT NULL,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid,
    seo_description character varying(1000),
    colors_json character varying(2000),
    about_text character varying(8000),
    testimonials_json character varying(8000),
    hero_subtitle character varying(255),
    hero_title character varying(255),
    seo_title character varying(255),
    template character varying(255)
);

CREATE TABLE leads (
    accrual_date date,
    date_of_birth date,
    governmental_defendant boolean NOT NULL,
    hit_and_run boolean NOT NULL,
    created_at timestamp(6) with time zone,
    case_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    description character varying(4000),
    case_type character varying(255),
    email character varying(255),
    name character varying(255),
    opposing_party character varying(255),
    phone character varying(255),
    status character varying(255)
);

CREATE TABLE messages (
    created_at timestamp(6) with time zone,
    conversation_id uuid,
    id uuid NOT NULL,
    sender_id uuid,
    tenant_id uuid,
    body character varying(4000),
    type character varying(255)
);

CREATE TABLE minutes_invoices (
    minutes integer NOT NULL,
    period character varying(7) NOT NULL,
    amount_cents bigint NOT NULL,
    charged_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    status character varying(16),
    tenant_id uuid,
    m_payment_id character varying(48),
    pf_payment_id character varying(64),
    note character varying(500)
);

CREATE TABLE modules (
    active boolean NOT NULL,
    core boolean NOT NULL,
    price_monthly numeric(38,2),
    sort_order integer,
    id uuid NOT NULL,
    description character varying(1000),
    category character varying(255),
    icon character varying(255),
    min_plan_slug character varying(255),
    name character varying(255),
    slug character varying(255)
);

CREATE TABLE newsletter_opt_ins (
    consent boolean NOT NULL,
    consented_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    email character varying(200) NOT NULL
);

CREATE TABLE notes (
    created_at timestamp(6) with time zone,
    case_id uuid,
    client_id uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    user_id uuid,
    body character varying(8000),
    title character varying(255),
    type character varying(255)
);

CREATE TABLE notifications (
    read boolean NOT NULL,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid,
    user_id uuid,
    body character varying(2000),
    link character varying(255),
    title character varying(255),
    type character varying(255)
);

CREATE TABLE outbound_messages (
    unit_cost numeric(38,2),
    created_at timestamp(6) with time zone,
    actor_id uuid,
    case_id uuid,
    channel character varying(16) NOT NULL,
    client_id uuid,
    contact_id uuid,
    id uuid NOT NULL,
    provider character varying(16),
    tenant_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    callback_token character varying(64),
    provider_sid character varying(64),
    subject character varying(200),
    from_address character varying(320),
    to_address character varying(320),
    error_message character varying(500),
    body text
);

CREATE TABLE payment_proofs (
    amount_claimed numeric(38,2),
    reviewed_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    document_id uuid,
    id uuid NOT NULL,
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

CREATE TABLE plans (
    active boolean NOT NULL,
    included_voice_minutes integer,
    max_cases integer,
    max_storage_gb integer,
    max_users integer,
    price_monthly numeric(38,2),
    sort_order integer,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    description character varying(1000),
    features_json character varying(4000),
    name character varying(255),
    slug character varying(255)
);

CREATE TABLE product_checkouts (
    seat_count integer NOT NULL,
    amount_cents bigint NOT NULL,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    kind character varying(16),
    period character varying(16),
    status character varying(16),
    tenant_id uuid,
    m_payment_id character varying(48) NOT NULL
);

CREATE TABLE product_payments (
    amount_cents bigint NOT NULL,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    kind character varying(16),
    period character varying(16),
    tenant_id uuid,
    payment_status character varying(32),
    m_payment_id character varying(48),
    pf_payment_id character varying(64),
    token character varying(128)
);

CREATE TABLE public_site_features (
    requested boolean NOT NULL,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    approval_status character varying(32) NOT NULL,
    feature_key character varying(64) NOT NULL,
    note character varying(1000)
);

CREATE TABLE refresh_tokens (
    created_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone,
    id uuid NOT NULL,
    user_id uuid,
    token character varying(1000)
);

CREATE TABLE signature_requests (
    created_at timestamp(6) with time zone,
    signed_at timestamp(6) with time zone,
    case_id uuid,
    client_id uuid,
    conflict_check_id uuid,
    id uuid NOT NULL,
    lead_id uuid,
    tenant_id uuid NOT NULL,
    document_hash character varying(128),
    signature_hash character varying(128),
    document_body text,
    purpose character varying(255),
    signature_data_url text,
    signature_standard character varying(255),
    signer_email character varying(255) NOT NULL,
    signer_identity_number character varying(255),
    signer_name character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL
);

CREATE TABLE tasks (
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    due_date timestamp(6) with time zone,
    assigned_to uuid,
    case_id uuid,
    created_by uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    checklist_json character varying(4000),
    description character varying(4000),
    priority character varying(255),
    source_key character varying(255),
    status character varying(255),
    title character varying(255)
);

CREATE TABLE tenant_modules (
    enabled boolean NOT NULL,
    activated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    module_id uuid,
    tenant_id uuid
);

CREATE TABLE tenant_public_sites (
    submitted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    live_theme character varying(16),
    tenant_id uuid NOT NULL,
    theme character varying(16) NOT NULL,
    accent character varying(32),
    live_accent character varying(32),
    live_tagline character varying(500),
    tagline character varying(500),
    branding_note character varying(1000),
    publish_note character varying(1000),
    about_text character varying(4000),
    live_about_text character varying(4000),
    recognition_json character varying(4000),
    branding_status character varying(255) NOT NULL,
    firm_name character varying(255),
    live_firm_name character varying(255),
    publish_status character varying(255) NOT NULL
);

CREATE TABLE tenants (
    ffc_expires_on date,
    onboarding_completed boolean NOT NULL,
    popia_operator_acknowledged boolean NOT NULL,
    bank_feed_imported_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    trial_ends_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    plan_id uuid,
    practice_areas_json character varying(2000),
    settings_json character varying(4000),
    paia_manual_body character varying(8000),
    address_line1 character varying(255),
    city character varying(255),
    country character varying(255),
    custom_domain character varying(255),
    email character varying(255),
    ffc_holder_name character varying(255),
    ffc_number character varying(255),
    firm_name character varying(255),
    information_officer_email character varying(255),
    information_officer_name character varying(255),
    last_bank_feed_source character varying(255),
    logo_url character varying(255),
    phone character varying(255),
    slug character varying(255) NOT NULL,
    state character varying(255),
    status character varying(255),
    tagline character varying(255),
    website character varying(255),
    zip character varying(255)
);

CREATE TABLE time_entries (
    billable boolean NOT NULL,
    billed boolean NOT NULL,
    date date,
    duration_minutes integer NOT NULL,
    hourly_rate numeric(38,2),
    total_amount numeric(38,2),
    created_at timestamp(6) with time zone,
    call_record_id uuid,
    case_id uuid,
    id uuid NOT NULL,
    invoice_id uuid,
    tenant_id uuid,
    user_id uuid,
    description character varying(2000),
    activity_type character varying(255),
    source character varying(255)
);

CREATE TABLE trust_accounts (
    balance numeric(38,2),
    bank_balance numeric(38,2),
    created_at timestamp(6) with time zone,
    last_reconciled_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid,
    account_name character varying(255),
    account_type character varying(255),
    bank_name character varying(255),
    status character varying(255)
);

CREATE TABLE trust_reconciliations (
    bank_balance numeric(38,2),
    book_balance numeric(38,2),
    certified boolean NOT NULL,
    client_ledger_total numeric(38,2),
    difference numeric(38,2),
    period_end date,
    certified_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    certified_by uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    trust_account_id uuid,
    notes character varying(4000),
    ledgers_json character varying(8000),
    status character varying(255)
);

CREATE TABLE trust_transactions (
    amount numeric(38,2),
    balance_after numeric(38,2),
    created_at timestamp(6) with time zone,
    case_id uuid,
    client_id uuid,
    created_by uuid,
    id uuid NOT NULL,
    tenant_id uuid,
    trust_account_id uuid,
    description character varying(2000),
    reference_number character varying(255),
    type character varying(255)
);

CREATE TABLE usage_invoices (
    date_issued date,
    messaging_subtotal numeric(38,2),
    modules_subtotal numeric(38,2),
    pstn_subtotal numeric(38,2),
    total numeric(38,2),
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid,
    line_items_json character varying(8000),
    invoice_number character varying(255),
    period character varying(255) NOT NULL,
    status character varying(255)
);

CREATE TABLE users (
    email_verified boolean NOT NULL,
    hourly_rate numeric(38,2),
    password_change_required boolean,
    totp_enabled boolean NOT NULL,
    created_at timestamp(6) with time zone,
    last_login_at timestamp(6) with time zone,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    practice_areas_json character varying(2000),
    bio character varying(4000),
    bar_number character varying(255),
    bar_state character varying(255),
    email character varying(255) NOT NULL,
    first_name character varying(255),
    last_name character varying(255),
    online_status character varying(255),
    password_hash character varying(255) NOT NULL,
    phone character varying(255),
    role character varying(255),
    status character varying(255),
    title character varying(255),
    totp_secret character varying(255)
);

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY billing_accounts
    ADD CONSTRAINT billing_accounts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY billing_accounts
    ADD CONSTRAINT billing_accounts_tenant_id_key UNIQUE (tenant_id);

ALTER TABLE ONLY call_records
    ADD CONSTRAINT call_records_bridge_token_key UNIQUE (bridge_token);

ALTER TABLE ONLY call_records
    ADD CONSTRAINT call_records_pkey PRIMARY KEY (id);

ALTER TABLE ONLY cases
    ADD CONSTRAINT cases_pkey PRIMARY KEY (id);

ALTER TABLE ONLY clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (id);

ALTER TABLE ONLY conflict_checks
    ADD CONSTRAINT conflict_checks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY connected_integrations
    ADD CONSTRAINT connected_integrations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY contacts
    ADD CONSTRAINT contacts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY document_templates
    ADD CONSTRAINT document_templates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY events
    ADD CONSTRAINT events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);

ALTER TABLE ONLY firm_phone_numbers
    ADD CONSTRAINT firm_phone_numbers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY invoice_payments
    ADD CONSTRAINT invoice_payments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY invoice_write_offs
    ADD CONSTRAINT invoice_write_offs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY landing_pages
    ADD CONSTRAINT landing_pages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY landing_pages
    ADD CONSTRAINT landing_pages_tenant_id_key UNIQUE (tenant_id);

ALTER TABLE ONLY leads
    ADD CONSTRAINT leads_pkey PRIMARY KEY (id);

ALTER TABLE ONLY messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY minutes_invoices
    ADD CONSTRAINT minutes_invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY modules
    ADD CONSTRAINT modules_pkey PRIMARY KEY (id);

ALTER TABLE ONLY modules
    ADD CONSTRAINT modules_slug_key UNIQUE (slug);

ALTER TABLE ONLY newsletter_opt_ins
    ADD CONSTRAINT newsletter_opt_ins_pkey PRIMARY KEY (id);

ALTER TABLE ONLY notes
    ADD CONSTRAINT notes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY outbound_messages
    ADD CONSTRAINT outbound_messages_callback_token_key UNIQUE (callback_token);

ALTER TABLE ONLY outbound_messages
    ADD CONSTRAINT outbound_messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT payment_proofs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY plans
    ADD CONSTRAINT plans_pkey PRIMARY KEY (id);

ALTER TABLE ONLY plans
    ADD CONSTRAINT plans_slug_key UNIQUE (slug);

ALTER TABLE ONLY product_checkouts
    ADD CONSTRAINT product_checkouts_m_payment_id_key UNIQUE (m_payment_id);

ALTER TABLE ONLY product_checkouts
    ADD CONSTRAINT product_checkouts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY product_payments
    ADD CONSTRAINT product_payments_pf_payment_id_key UNIQUE (pf_payment_id);

ALTER TABLE ONLY product_payments
    ADD CONSTRAINT product_payments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public_site_features
    ADD CONSTRAINT public_site_features_pkey PRIMARY KEY (id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_key UNIQUE (token);

ALTER TABLE ONLY signature_requests
    ADD CONSTRAINT signature_requests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY tenant_modules
    ADD CONSTRAINT tenant_modules_pkey PRIMARY KEY (id);

ALTER TABLE ONLY tenant_public_sites
    ADD CONSTRAINT tenant_public_sites_pkey PRIMARY KEY (id);

ALTER TABLE ONLY tenant_public_sites
    ADD CONSTRAINT tenant_public_sites_tenant_id_key UNIQUE (tenant_id);

ALTER TABLE ONLY tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);

ALTER TABLE ONLY tenants
    ADD CONSTRAINT tenants_slug_key UNIQUE (slug);

ALTER TABLE ONLY time_entries
    ADD CONSTRAINT time_entries_pkey PRIMARY KEY (id);

ALTER TABLE ONLY trust_accounts
    ADD CONSTRAINT trust_accounts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY trust_reconciliations
    ADD CONSTRAINT trust_reconciliations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY trust_transactions
    ADD CONSTRAINT trust_transactions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY newsletter_opt_ins
    ADD CONSTRAINT uk_newsletter_opt_in UNIQUE (tenant_id, email);

ALTER TABLE ONLY public_site_features
    ADD CONSTRAINT uk_public_site_feature UNIQUE (tenant_id, feature_key);

ALTER TABLE ONLY usage_invoices
    ADD CONSTRAINT usage_invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
