-- Public People page opt-in. Null (rows from before this change) stays listed; new invites start hidden.

alter table users add column if not exists public_profile boolean;
