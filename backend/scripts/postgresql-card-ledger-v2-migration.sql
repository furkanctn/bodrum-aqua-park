-- card_ledger: ürün satışı / satış alanı / acenta bilet satırları (PostgreSQL, idempotent)
-- Hata: "cle1_0.sale_area_id sütunu mevcut değil" veya kart sorgusu 500
-- psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f postgresql-card-ledger-v2-migration.sql

ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS product_id BIGINT;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS sale_area_id BIGINT;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS ticket_age_group_id BIGINT;
ALTER TABLE card_ledger ADD COLUMN IF NOT EXISTS line_quantity INTEGER;

DO $$
BEGIN
	IF NOT EXISTS (
			SELECT 1
			FROM information_schema.table_constraints
			WHERE table_schema = current_schema()
				AND table_name = 'card_ledger'
				AND constraint_name = 'fk_card_ledger_product') THEN
		ALTER TABLE card_ledger
			ADD CONSTRAINT fk_card_ledger_product FOREIGN KEY (product_id) REFERENCES products(id);
	END IF;
	IF NOT EXISTS (
			SELECT 1
			FROM information_schema.table_constraints
			WHERE table_schema = current_schema()
				AND table_name = 'card_ledger'
				AND constraint_name = 'fk_card_ledger_sale_area') THEN
		ALTER TABLE card_ledger
			ADD CONSTRAINT fk_card_ledger_sale_area FOREIGN KEY (sale_area_id) REFERENCES sale_areas(id);
	END IF;
	IF NOT EXISTS (
			SELECT 1
			FROM information_schema.table_constraints
			WHERE table_schema = current_schema()
				AND table_name = 'card_ledger'
				AND constraint_name = 'fk_card_ledger_ticket_age_group') THEN
		ALTER TABLE card_ledger
			ADD CONSTRAINT fk_card_ledger_ticket_age_group FOREIGN KEY (ticket_age_group_id) REFERENCES ticket_age_groups(id);
	END IF;
END $$;

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
