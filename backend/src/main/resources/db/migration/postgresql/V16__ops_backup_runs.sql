-- Record of each encrypted backup export, so the status page can show when the last one ran.
CREATE TABLE IF NOT EXISTS ops_backup_runs (
    id UUID PRIMARY KEY,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    tables_count INTEGER,
    rows_count BIGINT,
    bytes_count BIGINT,
    sha256 VARCHAR(64),
    detail VARCHAR(500)
);
