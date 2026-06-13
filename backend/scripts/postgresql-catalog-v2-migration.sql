

CREATE TABLE IF NOT EXISTS sale_area_menu_pages (
	sale_area_id BIGINT NOT NULL,
	menu_page_id BIGINT NOT NULL,
	PRIMARY KEY (sale_area_id, menu_page_id),
	CONSTRAINT fk_samp_sale_area FOREIGN KEY (sale_area_id) REFERENCES sale_areas(id),
	CONSTRAINT fk_samp_menu_page FOREIGN KEY (menu_page_id) REFERENCES menu_pages(id)
);

DO $$
BEGIN
	IF EXISTS (
			SELECT 1
			FROM information_schema.columns
			WHERE table_schema = current_schema()
				AND table_name = 'menu_pages'
				AND column_name = 'sale_area_id') THEN
		EXECUTE $ins$
			INSERT INTO sale_area_menu_pages (sale_area_id, menu_page_id)
			SELECT sale_area_id, id
			FROM menu_pages
			WHERE sale_area_id IS NOT NULL
			ON CONFLICT DO NOTHING
		$ins$;
	END IF;
END $$;

ALTER TABLE ticket_age_groups ADD COLUMN IF NOT EXISTS agency_complimentary BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS product_id BIGINT;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS sale_area_id BIGINT;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS ticket_age_group_id BIGINT;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS line_quantity INTEGER;

-- Eski şema: menu_pages.sale_area_id zorunluydu; yeni menü eklerken kısıt ihlali veriyordu
DO $$
BEGIN
	IF EXISTS (
			SELECT 1 FROM information_schema.columns
			WHERE table_schema = current_schema() AND table_name = 'menu_pages' AND column_name = 'sale_area_id') THEN
		EXECUTE 'ALTER TABLE menu_pages ALTER COLUMN sale_area_id DROP NOT NULL';
	END IF;
	IF EXISTS (
			SELECT 1 FROM information_schema.columns
			WHERE table_schema = current_schema() AND table_name = 'products' AND column_name = 'sale_area_id') THEN
		EXECUTE 'ALTER TABLE products ALTER COLUMN sale_area_id DROP NOT NULL';
	END IF;
END $$;

DO $$
DECLARE
	r RECORD;
BEGIN
	FOR r IN
		SELECT tc.constraint_name
		FROM information_schema.table_constraints tc
		JOIN information_schema.key_column_usage kcu
			ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
		WHERE tc.table_schema = current_schema()
			AND tc.table_name = 'menu_pages'
			AND tc.constraint_type = 'FOREIGN KEY'
			AND kcu.column_name = 'sale_area_id'
	LOOP
		EXECUTE format('ALTER TABLE menu_pages DROP CONSTRAINT %I', r.constraint_name);
	END LOOP;
	FOR r IN
		SELECT tc.constraint_name
		FROM information_schema.table_constraints tc
		JOIN information_schema.key_column_usage kcu
			ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
		WHERE tc.table_schema = current_schema()
			AND tc.table_name = 'products'
			AND tc.constraint_type = 'FOREIGN KEY'
			AND kcu.column_name = 'sale_area_id'
	LOOP
		EXECUTE format('ALTER TABLE products DROP CONSTRAINT %I', r.constraint_name);
	END LOOP;
END $$;

ALTER TABLE menu_pages DROP COLUMN IF EXISTS sale_area_id;
ALTER TABLE products DROP COLUMN IF EXISTS sale_area_id;
