-- Yinelenen menu_pages.code satırlarını birleştirir (eski sale_area_id şemasından kalan kopyalar)
-- Hata: "Query did not return a unique result: 4 results were returned" (GENEL vb.)
-- psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f postgresql-menu-pages-dedup.sql

-- Önce kontrol:
-- SELECT code, COUNT(*) FROM menu_pages GROUP BY code HAVING COUNT(*) > 1;

DO $$
DECLARE
	r RECORD;
	keeper_id BIGINT;
	dup_id BIGINT;
BEGIN
	FOR r IN
		SELECT code FROM menu_pages GROUP BY code HAVING COUNT(*) > 1
	LOOP
		SELECT MIN(id) INTO keeper_id FROM menu_pages WHERE code = r.code;
		FOR dup_id IN
			SELECT id FROM menu_pages WHERE code = r.code AND id <> keeper_id ORDER BY id
		LOOP
			UPDATE products SET menu_page_id = keeper_id WHERE menu_page_id = dup_id;
			IF EXISTS (
					SELECT 1 FROM information_schema.tables
					WHERE table_schema = current_schema() AND table_name = 'sale_area_menu_pages') THEN
				INSERT INTO sale_area_menu_pages (sale_area_id, menu_page_id)
				SELECT sale_area_id, keeper_id FROM sale_area_menu_pages WHERE menu_page_id = dup_id
				ON CONFLICT DO NOTHING;
				DELETE FROM sale_area_menu_pages WHERE menu_page_id = dup_id;
			END IF;
			DELETE FROM menu_pages WHERE id = dup_id;
			RAISE NOTICE 'Birleştirildi: code=% dup_id=% keeper_id=%', r.code, dup_id, keeper_id;
		END LOOP;
	END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_menu_pages_code ON menu_pages (code);
