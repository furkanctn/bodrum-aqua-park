-- =============================================================================
-- HEMEN SIFIRLA — sunucu veritabanında doğrudan çalıştır
--
-- pgAdmin / DBeaver / psql: bu dosyanın tamamını seçip çalıştır.
--
--   psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f run-nightly-card-reset-now.sql
--
-- Yapılanlar:
--   • cards.balance → 0
--   • cards.entry_gate → 0
--   • rfid_card_passes.active → false (aktif günlük paslar)
--
-- SILINMEZ: card_ledger (satış geçmişi / raporlar)
-- =============================================================================

BEGIN;

-- 1) Bakiyeleri sıfırla
UPDATE cards
SET balance = 0,
	updated_at = NOW(),
	version = version + 1
WHERE balance > 0;

-- 2) Turnike giriş haklarını sıfırla
UPDATE cards
SET entry_gate = 0,
	updated_at = NOW(),
	version = version + 1
WHERE entry_gate <> 0;

-- 3) Aktif RFID günlük pasları kapat
UPDATE rfid_card_passes
SET active = false,
	updated_at = NOW()
WHERE active = true;

COMMIT;

-- Özet (COMMIT sonrası)
SELECT
	(SELECT COUNT(*) FROM cards WHERE balance = 0) AS sifir_bakiye_kart,
	(SELECT COUNT(*) FROM cards WHERE entry_gate = 0) AS sifir_turnike_kart,
	(SELECT COUNT(*) FROM rfid_card_passes WHERE active = false) AS pasif_pas,
	(SELECT COUNT(*) FROM card_ledger) AS kalan_hareket;
