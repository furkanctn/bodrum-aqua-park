#!/usr/bin/env bash
# Manuel test: kart bakiyelerini, entry_gate ve RFID pasları şimdi sıfırlar.
# Önce bir kez kurulum: psql ... -f backend/scripts/postgresql-nightly-card-reset.sql
#
# Kullanım:
#   bash backend/scripts/run-nightly-card-reset-now.sh
#
# Bağlantıyı değiştirmek için (opsiyonel):
#   PGHOST=127.0.0.1 PGUSER=postgres PGDATABASE=bodrum_aqua_park PGPASSWORD=... bash backend/scripts/run-nightly-card-reset-now.sh

set -euo pipefail

PGHOST="${PGHOST:-192.168.0.15}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-bodrum_aqua_park}"

if ! command -v psql >/dev/null 2>&1; then
	echo "Hata: psql bulunamadı. PostgreSQL client kurulu olmalı." >&2
	exit 1
fi

echo "==> Kart sıfırlama çalıştırılıyor ($PGHOST:$PGPORT / $PGDATABASE)"
echo

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "SELECT * FROM bodrum_nightly_card_reset();"

echo
echo "Tamam. Sonuç sütunları: balances_cleared | entry_gates_cleared | passes_deactivated | ledger_rows"
