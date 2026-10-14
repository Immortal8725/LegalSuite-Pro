#!/bin/bash
# Run the full backend test suite against a throwaway local Postgres database.
# Needs a local Postgres with a role "legalsuite" (password legalsuite) that can create databases.
set -euo pipefail
DB=${1:-legalsuite_test}
export PGPASSWORD=${DB_PASSWORD:-legalsuite}
psql -h 127.0.0.1 -U legalsuite -d postgres -qc "drop database if exists $DB"
psql -h 127.0.0.1 -U legalsuite -d postgres -qc "create database $DB"
cd "$(dirname "$0")/.."
SPRING_PROFILES_ACTIVE=postgres DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/$DB \
  LEGALSUITE_JWT_SECRET=${LEGALSUITE_JWT_SECRET:-test-only-$(date +%s)-not-for-production-use-xxxxxxxx} \
  ./mvnw -B test
