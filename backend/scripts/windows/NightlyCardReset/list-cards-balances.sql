-- =============================================================================
-- Kart listesi + bakiyeler — sunucu PostgreSQL (yalnızca SELECT, değiştirmez)
--
-- pgAdmin / DBeaver / psql: bu dosyanın tamamını seçip çalıştır.
--
--   psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f list-cards-balances.sql
--
-- =============================================================================

-- Özet
SELECT
	COUNT(*) AS toplam_kart,
	COUNT(*) FILTER (WHERE balance > 0) AS bakiyeli_kart,
	COUNT(*) FILTER (WHERE balance = 0) AS sifir_bakiye_kart,
	COUNT(*) FILTER (WHERE entry_gate <> 0) AS turnike_hakki_olan,
	COUNT(*) FILTER (WHERE status = 'ACTIVE') AS aktif_kart,
	COUNT(*) FILTER (WHERE status = 'BLOCKED') AS blokeli_kart,
	COALESCE(SUM(balance), 0) AS toplam_bakiye
FROM cards;

-- Tüm kartlar (bakiye yüksekten düşüğe)
SELECT
	id,
	uid,
	balance,
	entry_gate,
	status,
	created_at,
	updated_at
FROM cards
ORDER BY balance DESC, id;

-- Yalnızca bakiyesi > 0 olanlar
SELECT
	id,
	uid,
	balance,
	entry_gate,
	status,
	updated_at
FROM cards
WHERE balance > 0
ORDER BY balance DESC, id;
