#!/usr/bin/env bash
set -euo pipefail

# macOS does not provide the Linux-style C.UTF-8 locale. PostgreSQL refuses
# to start when that invalid locale is inherited from the terminal.
export LANG=C
export LC_ALL=C

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
data_dir="$project_dir/.postgres-data"
log_file="$project_dir/.postgres.log"
db_host="${DB_HOST:-localhost}"
db_port="${DB_PORT:-5432}"
db_name="${DB_NAME:-courses_variant04_test}"
db_user="${DB_USER:-courses_student}"

if [[ -n "${PG_BIN:-}" ]]; then
  pg_bin="$PG_BIN"
elif [[ -x /opt/homebrew/opt/postgresql@17/bin/pg_ctl ]]; then
  pg_bin=/opt/homebrew/opt/postgresql@17/bin
elif [[ -x /usr/local/opt/postgresql@17/bin/pg_ctl ]]; then
  pg_bin=/usr/local/opt/postgresql@17/bin
else
  pg_bin="$(dirname "$(command -v pg_ctl)")"
fi

if [[ ! -f "$data_dir/PG_VERSION" ]]; then
  "$pg_bin/initdb" -D "$data_dir" -U "$db_user" --auth-local=trust --auth-host=trust \
    --encoding=UTF8 --no-locale -c dynamic_shared_memory_type=posix
fi

if ! "$pg_bin/pg_ctl" status -D "$data_dir" >/dev/null 2>&1; then
  "$pg_bin/pg_ctl" start -D "$data_dir" -l "$log_file" -o "-p $db_port -h 127.0.0.1" -w
fi

if ! "$pg_bin/psql" -h "$db_host" -p "$db_port" -U "$db_user" -d postgres -Atqc \
  "SELECT 1 FROM pg_database WHERE datname='$db_name'" | grep -q 1; then
  "$pg_bin/createdb" -h "$db_host" -p "$db_port" -U "$db_user" -O "$db_user" "$db_name"
fi

if [[ "$("$pg_bin/psql" -h "$db_host" -p "$db_port" -U "$db_user" -d "$db_name" -Atqc \
  "SELECT to_regclass('public.students') IS NOT NULL")" != "t" ]]; then
  "$pg_bin/psql" -h "$db_host" -p "$db_port" -U "$db_user" -d "$db_name" \
    -v ON_ERROR_STOP=1 -f "$project_dir/migrations/V1__create_tables.sql"
fi

printf 'PostgreSQL test database is ready.\n'
printf 'DB_HOST=%s DB_PORT=%s DB_NAME=%s DB_USER=%s\n' "$db_host" "$db_port" "$db_name" "$db_user"
