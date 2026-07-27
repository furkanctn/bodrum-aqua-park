-- =============================================================================
-- TEHLIKELI — HEMEN SIFIRLA (YEDEK ALMAZ)
--
-- Bu dosya pg_dump yapmaz. Canlida KULLANMAYIN.
-- Guvenli yol (once yedek dosyaya, sonra sifirlama):
--   BodrumNightlyCardReset.bat
--
-- Bilerek yedeksiz sifirlamak istiyorsaniz asagidaki BEGIN...COMMIT blogundaki
-- yorum isaretlerini kaldirip calistirin.
-- =============================================================================

/*
BEGIN;

UPDATE cards
SET balance = 0,
	updated_at = NOW(),
	version = version + 1
WHERE balance > 0;

UPDATE cards
SET entry_gate = 0,
	updated_at = NOW(),
	version = version + 1
WHERE entry_gate <> 0;

UPDATE rfid_card_passes
SET active = false,
	updated_at = NOW()
WHERE active = true;

COMMIT;

SELECT
	(SELECT COUNT(*) FROM cards WHERE balance = 0) AS sifir_bakiye_kart,
	(SELECT COUNT(*) FROM cards WHERE entry_gate = 0) AS sifir_turnike_kart,
	(SELECT COUNT(*) FROM rfid_card_passes WHERE active = false) AS pasif_pas,
	(SELECT COUNT(*) FROM card_ledger) AS kalan_hareket;
*/
