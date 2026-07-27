-- =============================================================================
-- Kart bakiyelerini GERİ YÜKLE — card_ledger'dan (sunucu PostgreSQL)
--
-- Gece job'ı (bodrum_nightly_card_reset) bakiyeleri 0 yaptıysa ve
-- card_ledger SİLİNMEDİYSE bu script son ledger kaydındaki balance_after
-- değerini cards.balance'a yazar.
--
-- ÖNEMLİ:
--   1) Önce AŞAMA 1 (önizleme) çalıştırın — sayıları kontrol edin.
--   2) Doğruysa AŞAMA 2 (geri yükleme) çalıştırın.
--   3) Ledger boşsa / silinmişse bu script işe yaramaz → pg_restore ile
--      C:\BodrumAquaPark\backup\ yedeğinden dönün (22:45 yedek, sıfırlama 23:00).
--
--   psql -h 127.0.0.1 -p 5433 -U postgres -d bodrum_aqua_park -f restore-cards-balances-from-ledger.sql
--
-- =============================================================================

-- -----------------------------------------------------------------------------
-- AŞAMA 0 — Ledger var mı?
-- -----------------------------------------------------------------------------
SELECT
	(SELECT COUNT(*) FROM cards) AS kart_sayisi,
	(SELECT COUNT(*) FROM cards WHERE balance > 0) AS su_an_bakiyeli,
	(SELECT COUNT(*) FROM card_ledger) AS ledger_kayit,
	(SELECT COUNT(DISTINCT card_id) FROM card_ledger) AS ledger_li_kart;

-- -----------------------------------------------------------------------------
-- AŞAMA 1 — ÖNİZLEME (değiştirmez)
-- Son ledger satırı DAILY_RESET ise atlanır (POS sorgulama ile bilinçli sıfırlama).
-- Gece SQL sıfırlaması ledger yazmadığı için son balance_after = doğru bakiye.
-- -----------------------------------------------------------------------------
WITH last_ledger AS (
	SELECT DISTINCT ON (cl.card_id)
		cl.card_id,
		cl.type,
		cl.amount_change,
		cl.balance_after,
		cl.created_at,
		cl.id AS ledger_id
	FROM card_ledger cl
	ORDER BY cl.card_id, cl.created_at DESC, cl.id DESC
),
plan AS (
	SELECT
		c.id,
		c.uid,
		c.balance AS suanki_bakiye,
		c.entry_gate AS suanki_entry_gate,
		ll.type AS son_hareket_tipi,
		ll.balance_after AS son_balance_after,
		ll.created_at AS son_hareket_zamani,
		CASE
			WHEN ll.type = 'DAILY_RESET' THEN NULL  -- bilinçli sıfırlama; dokunma
			ELSE ll.balance_after
		END AS geri_yuklenecek_bakiye
	FROM cards c
	INNER JOIN last_ledger ll ON ll.card_id = c.id
)
SELECT
	id,
	uid,
	suanki_bakiye,
	geri_yuklenecek_bakiye,
	(geri_yuklenecek_bakiye - suanki_bakiye) AS fark,
	son_hareket_tipi,
	son_hareket_zamani
FROM plan
WHERE geri_yuklenecek_bakiye IS NOT NULL
  AND geri_yuklenecek_bakiye > 0
  AND suanki_bakiye IS DISTINCT FROM geri_yuklenecek_bakiye
ORDER BY geri_yuklenecek_bakiye DESC, id;

-- Özet önizleme
WITH last_ledger AS (
	SELECT DISTINCT ON (cl.card_id)
		cl.card_id,
		cl.type,
		cl.balance_after
	FROM card_ledger cl
	ORDER BY cl.card_id, cl.created_at DESC, cl.id DESC
),
plan AS (
	SELECT
		c.id,
		c.balance AS suanki,
		CASE WHEN ll.type = 'DAILY_RESET' THEN NULL ELSE ll.balance_after END AS hedef
	FROM cards c
	INNER JOIN last_ledger ll ON ll.card_id = c.id
)
SELECT
	COUNT(*) FILTER (
		WHERE hedef IS NOT NULL AND hedef > 0 AND suanki IS DISTINCT FROM hedef
	) AS guncellenecek_kart,
	COALESCE(SUM(hedef) FILTER (
		WHERE hedef IS NOT NULL AND hedef > 0 AND suanki IS DISTINCT FROM hedef
	), 0) AS yuklenecek_toplam_bakiye,
	COUNT(*) FILTER (WHERE hedef IS NULL) AS atlanan_daily_reset,
	(SELECT COUNT(*) FROM cards c2
	 WHERE NOT EXISTS (SELECT 1 FROM card_ledger cl WHERE cl.card_id = c2.id)
	) AS ledger_yok_kart
FROM plan;

-- -----------------------------------------------------------------------------
-- AŞAMA 2 — GERİ YÜKLEME (bakiyeleri yazar)
-- Önizleme (AŞAMA 1) doğruysa aşağıdaki /* ... */ yorumunu KALDIRIP çalıştırın.
-- -----------------------------------------------------------------------------
/*
BEGIN;

WITH last_ledger AS (
	SELECT DISTINCT ON (cl.card_id)
		cl.card_id,
		cl.type,
		cl.balance_after
	FROM card_ledger cl
	ORDER BY cl.card_id, cl.created_at DESC, cl.id DESC
),
plan AS (
	SELECT
		c.id AS card_id,
		CASE WHEN ll.type = 'DAILY_RESET' THEN NULL ELSE ll.balance_after END AS hedef
	FROM cards c
	INNER JOIN last_ledger ll ON ll.card_id = c.id
)
UPDATE cards c
SET
	balance = p.hedef,
	updated_at = NOW(),
	version = c.version + 1
FROM plan p
WHERE c.id = p.card_id
  AND p.hedef IS NOT NULL
  AND p.hedef > 0
  AND c.balance IS DISTINCT FROM p.hedef;

SELECT
	COUNT(*) FILTER (WHERE balance > 0) AS bakiyeli_kart,
	COALESCE(SUM(balance), 0) AS toplam_bakiye
FROM cards;

COMMIT;
*/

-- -----------------------------------------------------------------------------
-- AŞAMA 3 (isteğe bağlı) — Bugün bilet alıp turnikeden GEÇMEMİŞ kartlara
-- entry_gate = 1 geri ver.
--
-- Mantık: İstanbul gününde TICKET_* var ve sonrasında
-- "Turnike — bilet girişi" ENTRY yok → hak hâlâ kullanılmamış sayılır.
-- -----------------------------------------------------------------------------
/*
BEGIN;

WITH bounds AS (
	SELECT
		(date_trunc('day', NOW() AT TIME ZONE 'Europe/Istanbul')
			AT TIME ZONE 'Europe/Istanbul') AS day_start,
		(date_trunc('day', NOW() AT TIME ZONE 'Europe/Istanbul')
			AT TIME ZONE 'Europe/Istanbul') + INTERVAL '1 day' AS day_end
),
ticketed AS (
	SELECT
		cl.card_id,
		MAX(cl.created_at) AS last_ticket_at
	FROM card_ledger cl, bounds b
	WHERE cl.type IN ('TICKET_CASH', 'TICKET_CARD', 'TICKET_CREDIT')
	  AND cl.created_at >= b.day_start
	  AND cl.created_at < b.day_end
	GROUP BY cl.card_id
),
used AS (
	SELECT DISTINCT cl.card_id
	FROM card_ledger cl
	INNER JOIN ticketed t ON t.card_id = cl.card_id
	WHERE cl.type = 'ENTRY'
	  AND cl.description = 'Turnike — bilet girişi'
	  AND cl.created_at >= t.last_ticket_at
)
UPDATE cards c
SET
	entry_gate = 1,
	updated_at = NOW(),
	version = c.version + 1
FROM ticketed t
WHERE c.id = t.card_id
  AND c.entry_gate = 0
  AND NOT EXISTS (SELECT 1 FROM used u WHERE u.card_id = c.id);

SELECT COUNT(*) AS entry_gate_1_kart FROM cards WHERE entry_gate = 1;

COMMIT;
*/
