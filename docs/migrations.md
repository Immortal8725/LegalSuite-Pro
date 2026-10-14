# Database migrations

H2 (local dev and the default test run) builds the schema from the entities with `ddl-auto: create-drop`. Flyway is off there.

Postgres (the `postgres` profile, used for hosting) is owned by Flyway. Hibernate runs with `ddl-auto: validate`, so the app will not start if the database and the entity model disagree.

Scripts live in `backend/src/main/resources/db/migration/postgresql`:

| Version | What it does |
|---|---|
| V1 | Full baseline schema for a fresh database. |
| V2 | Idempotent upgrade for a database created by the older `ddl-auto: update` build. Widens `conflict_checks.results_json` to `text` (B4), adds the payment, write-off, proof and PayFast tables and the new columns. No-op on a fresh install. |

An existing database with tables but no `flyway_schema_history` is baselined at V1 on first start (`baseline-on-migrate`), then V2 runs. Take a backup first (`pg_dump -Fc`).

## Adding a change

1. Change the entity.
2. Create two scratch databases: one migrated to the current version (start the app once against it), one built by Hibernate from the new model (`--spring.jpa.hibernate.ddl-auto=create`).
3. `python3 backend/scripts/schemadiff.py <migrated_db> <hibernate_db> > V3__what_changed.sql` and review it by hand.
4. Run `backend/scripts/test-postgres.sh` and the H2 suite.

Never edit a migration that has already run on a hosted database.
