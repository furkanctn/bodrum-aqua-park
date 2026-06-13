-- =============================================================================
-- Akşam 22:00 kart sıfırlama (PostgreSQL sunucusunda — uygulama kapalı olsa da çalışır)
--
-- Her akşam 22:00 Europe/Istanbul:
--   • cards.balance → 0
--   • cards.entry_gate → 0
--   • rfid_card_passes.active → false (aktif günlük paslar)
--   • card_ledger: kalan bakiye için DAILY_RESET satırı
--
-- Kurulum (sunucuda bir kez):
--   psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f postgresql-nightly-card-reset.sql
-- =============================================================================

-- Defter türü: DAILY_RESET (Java TransactionType ile uyumlu)
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

CREATE OR REPLACE FUNCTION bodrum_nightly_card_reset()
RETURNS TABLE (
	balances_cleared bigint,
	entry_gates_cleared bigint,
	passes_deactivated bigint,
	ledger_rows bigint
)
LANGUAGE plpgsql
AS $$
DECLARE
	v_ledger bigint;
	v_bal bigint;
	v_gate bigint;
	v_pass bigint;
BEGIN
	INSERT INTO card_ledger (card_id, type, amount_change, balance_after, description, created_at)
	SELECT
		c.id,
		'DAILY_RESET',
		-c.balance,
		0,
		'Akşam 22:00 günlük sıfırlama — bakiye ve turnike hakları',
		NOW()
	FROM cards c
	WHERE c.balance > 0;

	GET DIAGNOSTICS v_ledger = ROW_COUNT;

	UPDATE cards
	SET balance = 0,
		updated_at = NOW(),
		version = version + 1
	WHERE balance > 0;

	GET DIAGNOSTICS v_bal = ROW_COUNT;

	UPDATE cards
	SET entry_gate = 0,
		updated_at = NOW(),
		version = version + 1
	WHERE entry_gate <> 0;

	GET DIAGNOSTICS v_gate = ROW_COUNT;

	UPDATE rfid_card_passes
	SET active = false,
		updated_at = NOW()
	WHERE active = true;

	GET DIAGNOSTICS v_pass = ROW_COUNT;

	RETURN QUERY SELECT v_bal, v_gate, v_pass, v_ledger;
END;
$$;

COMMENT ON FUNCTION bodrum_nightly_card_reset() IS
	'Bodrum Aqua Park: her akşam 22:00 bakiye, entry_gate ve RFID pas sıfırlama';

CREATE EXTENSION IF NOT EXISTS pg_cron;
SELECT cron.unschedule(jobid) FROM cron.job WHERE jobname = 'bodrum-nightly-card-reset';
 SELECT cron.schedule(
 	'bodrum-nightly-card-reset',
 	'0 22 * * *',
 	$$SELECT bodrum_nightly_card_reset()$$
);

-- -----------------------------------------------------------------------------
-- Zamanlama B — Linux cron (pg_cron yoksa, DB sunucusunda crontab -e)
-- -----------------------------------------------------------------------------
-- 0 22 * * * psql -h 127.0.0.1 -U postgres -d bodrum_aqua_park -c "SELECT bodrum_nightly_card_reset();" >> /var/log/bodrum-nightly-reset.log 2>&1

-- Manuel test:
-- SELECT * FROM bodrum_nightly_card_reset();
