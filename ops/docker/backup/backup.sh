#!/bin/sh
set -eu

timestamp="$(date +%Y%m%d-%H%M%S)"
target="/data/${DB_NAME}-${timestamp}.sql.gz"

mysqldump -hmysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  "${DB_NAME}" | gzip > "${target}"

echo "backup written to ${target}"
