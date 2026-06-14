-- =============================================================================
-- Akşam 23:00 kart sıfırlama — PostgreSQL fonksiyonu
--
-- Her akşam 23:00'te çalıştırıldığında:
--   • card_ledger: tüm hareket geçmişi silinir
--   • cards.balance → 0
--   • cards.entry_gate → 0
--   • rfid_card_passes.active → false (aktif günlük paslar)
--
-- pgAdmin / psql (Windows veya Linux — pg_cron gerekmez):
--   Bu dosyanın tamamını çalıştırın.
--
-- Zamanlama:
--   Windows → backend/scripts/windows/BodrumNightlyCardReset-Zamanla.ps1
--   Linux pg_cron → postgresql-nightly-card-reset-pgcron.sql (isteğe bağlı)
--   Linux cron → aşağıdaki crontab satırı
-- =============================================================================

CREATE OR REPLACE FUNCTION bodrum_nightly_card_reset()
RETURNS TABLE (
	balances_cleared bigint,
	entry_gates_cleared bigint,
	passes_deactivated bigint,
	ledger_deleted bigint
)
LANGUAGE plpgsql
AS $$
DECLARE
	v_ledger bigint;
	v_bal bigint;
	v_gate bigint;
	v_pass bigint;
BEGIN
	DELETE FROM card_ledger;

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
	'Bodrum Aqua Park: her akşam 23:00 hareket geçmişi silme, bakiye, entry_gate ve RFID pas sıfırlama';

-- Manuel test:
-- SELECT * FROM bodrum_nightly_card_reset();

-- Linux cron (pg_cron yoksa, DB sunucusunda crontab -e):
-- 0 23 * * * psql -h 127.0.0.1 -U postgres -d bodrum_aqua_park -c "SELECT bodrum_nightly_card_reset();" >> /var/log/bodrum-nightly-reset.log 2>&1
