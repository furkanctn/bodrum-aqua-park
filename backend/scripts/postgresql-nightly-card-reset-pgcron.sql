-- =============================================================================
-- pg_cron ile akşam 23:00 kart sıfırlama (yalnızca Linux — pg_cron kurulu sunucular)
--
-- Önce postgresql-nightly-card-reset.sql ile fonksiyonu oluşturun / güncelleyin
-- (card_ledger artık silinmez; yalnızca bakiye, entry_gate, RFID pas).
-- Windows PostgreSQL'de pg_cron yoktur; BodrumNightlyCardReset-Zamanla.ps1 kullanın.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.unschedule(jobid) FROM cron.job WHERE jobname = 'bodrum-nightly-card-reset';

SELECT cron.schedule(
	'bodrum-nightly-card-reset',
	'0 23 * * *',
	$$SELECT bodrum_nightly_card_reset()$$
);
