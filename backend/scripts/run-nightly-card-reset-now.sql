-- =============================================================================
-- HEMEN SIFIRLA — sunucu veritabanında doğrudan çalıştır
--
-- pgAdmin / DBeaver / psql: bu dosyanın tamamını seçip çalıştır.
--
--   psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f run-nightly-card-reset-now.sql
-- =============================================================================

BEGIN;

-- DAILY_RESET defter kaydı için (zaten varsa sorun çıkarmaz)
ALTER TABLE card_ledger DROP CONSTRAINT IF EXISTS card_ledger_type_check;

ALTER TABLE card_ledger ADD CONSTRAINT card_ledger_type_check CHECK (
	type IN (
		'ENTRY',
		'SALE',
		'LOAD_CASH',
		'LOAD_CARD',
		'LOAD_AGENCY',
		'TICKET_CASH',
		'TICKET_CARD',
		'TICKET_CREDIT',
		'REFUND_CASH',
		'DAILY_RESET'
	)
);

-- 1) Kalan bakiyeler için defter kaydı
INSERT INTO card_ledger (card_id, type, amount_change, balance_after, description, created_at)
SELECT
	c.id,
	'DAILY_RESET',
	-c.balance,
	0,
	'Manuel günlük sıfırlama — bakiye ve turnike hakları',
	NOW()
FROM cards c
WHERE c.balance > 0;

-- 2) Bakiyeleri sıfırla
UPDATE cards
SET balance = 0,
	updated_at = NOW(),
	version = version + 1
WHERE balance > 0;

-- 3) Turnike giriş haklarını sıfırla
UPDATE cards
SET entry_gate = 0,
	updated_at = NOW(),
	version = version + 1
WHERE entry_gate <> 0;

-- 4) Aktif RFID günlük pasları kapat
UPDATE rfid_card_passes
SET active = false,
	updated_at = NOW()
WHERE active = true;

COMMIT;

-- Özet (COMMIT sonrası)
SELECT
	(SELECT COUNT(*) FROM cards WHERE balance = 0) AS sifir_bakiye_kart,
	(SELECT COUNT(*) FROM cards WHERE entry_gate = 0) AS sifir_turnike_kart,
	(SELECT COUNT(*) FROM rfid_card_passes WHERE active = false) AS pasif_pas;
