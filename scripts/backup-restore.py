#!/usr/bin/env python3
"""Decrypt an Owlaw nightly backup (.owlb) and turn it into SQL you can load with psql.

Usage:
  BACKUP_ENCRYPTION_KEY=... python3 scripts/backup-restore.py owlaw-2026-10-22.owlb > restore.sql
  psql "$TARGET_DATABASE_URL" -v ON_ERROR_STOP=1 -f restore.sql

The target must already have the schema: start the API once against the empty database so Flyway
creates every table, stop it, then load restore.sql. See docs/backup-restore.md.
Needs: pip install cryptography
"""
import base64, gzip, json, os, sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, dict) and "b64" in v:
        return "decode('%s','base64')" % v["b64"]
    return "'" + str(v).replace("'", "''") + "'"

def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    key = base64.b64decode(os.environ["BACKUP_ENCRYPTION_KEY"])
    data = open(sys.argv[1], "rb").read()
    if data[:5] != b"OWLB1":
        sys.exit("Not an Owlaw backup file")
    plain = gzip.decompress(AESGCM(key).decrypt(data[5:17], data[17:], None))
    out = sys.stdout
    out.write("BEGIN;\nSET session_replication_role = replica;\n")
    cols = {}
    for line in plain.decode("utf-8").splitlines():
        rec = json.loads(line)
        if "table" in rec:
            cols[rec["table"]] = rec["columns"]
            out.write('TRUNCATE "%s" CASCADE;\n' % rec["table"])
            continue
        t = rec["t"]
        out.write('INSERT INTO "%s" (%s) VALUES (%s);\n' % (
            t, ",".join('"%s"' % c for c in cols[t]), ",".join(lit(v) for v in rec["r"])))
    out.write("SET session_replication_role = DEFAULT;\nCOMMIT;\n")

if __name__ == "__main__":
    main()
