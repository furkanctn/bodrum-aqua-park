-- card_ledger.type: POS bilet tahsilatı türleri (TransactionType ile uyumlu)
-- Hata: "violates check constraint card_ledger_type_check" + TICKET_CARD / TICKET_CASH / TICKET_CREDIT
-- psql veya pgAdmin ile bir kez çalıştırın.

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
