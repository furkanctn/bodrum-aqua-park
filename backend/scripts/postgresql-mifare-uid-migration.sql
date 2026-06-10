-- Mifare UID gecisi: mevcut proximity ondalik UID'leri kanonik 8/14 hex formuna cevirir.
-- Yedek alin, test ortaminda dogrulayin, ardindan uretimde calistirin.
--
-- Onizleme (PostgreSQL):
-- SELECT uid,
--        LPAD(UPPER(to_hex(CAST(uid AS BIGINT))), 8, '0') AS mifare_hex
--   FROM cards
--  WHERE uid ~ '^[0-9]+$'
--  LIMIT 20;
--
-- Guncelleme (bayt sirasini okuyucu ile dogrulayin):
-- UPDATE cards
--    SET uid = LPAD(UPPER(to_hex(CAST(uid AS BIGINT))), 8, '0')
--  WHERE uid ~ '^[0-9]+$'
--    AND LENGTH(uid) <= 10;
--
-- Gecis doneminde: app.card.reverse-byte-order-lookup=true

SELECT 1 AS mifare_migration_readme;
