#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
data_dir="$project_dir/.postgres-data"

if [[ -n "${PG_BIN:-}" ]]; then
  pg_bin="$PG_BIN"
elif [[ -x /opt/homebrew/opt/postgresql@17/bin/pg_ctl ]]; then
  pg_bin=/opt/homebrew/opt/postgresql@17/bin
elif [[ -x /usr/local/opt/postgresql@17/bin/pg_ctl ]]; then
  pg_bin=/usr/local/opt/postgresql@17/bin
else
  pg_bin="$(dirname "$(command -v pg_ctl)")"
fi

if [[ -f "$data_dir/PG_VERSION" ]] && "$pg_bin/pg_ctl" status -D "$data_dir" >/dev/null 2>&1; then
  "$pg_bin/pg_ctl" stop -D "$data_dir" -m fast -w
else
  printf 'PostgreSQL test database is not running.\n'
fi
