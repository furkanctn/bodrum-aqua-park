-- access_logs + turnike izleme + pas indeksleri (PostgreSQL, idempotent)
-- Eski şemada "message" varsa "reason"a kopyalanır; yedek alın.

ALTER TABLE access_logs ADD COLUMN IF NOT EXISTS reason varchar(512);

DO $$
BEGIN
	IF EXISTS (
			SELECT 1
			FROM information_schema.columns
			WHERE table_schema = current_schema()
				AND table_name = 'access_logs'
				AND column_name = 'message') THEN
		EXECUTE 'UPDATE access_logs SET reason = message WHERE reason IS NULL';
	END IF;
END $$;

ALTER TABLE access_logs ADD COLUMN IF NOT EXISTS ip_address varchar(64);
ALTER TABLE access_logs ADD COLUMN IF NOT EXISTS user_agent varchar(512);

CREATE INDEX IF NOT EXISTS idx_access_logs_device_created ON access_logs (device_id, created_at);
CREATE INDEX IF NOT EXISTS idx_access_logs_allowed_created ON access_logs (allowed, created_at);
CREATE INDEX IF NOT EXISTS idx_access_logs_device_allowed_created ON access_logs (device_id, allowed, created_at);

ALTER TABLE turnstile_devices ADD COLUMN IF NOT EXISTS last_seen_at timestamptz;
ALTER TABLE turnstile_devices ADD COLUMN IF NOT EXISTS last_successful_access_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_turnstile_devices_last_seen ON turnstile_devices (last_seen_at);

CREATE INDEX IF NOT EXISTS idx_rfid_pass_card_valid_active ON rfid_card_passes (rfid_card_id, valid_date, active);
CREATE INDEX IF NOT EXISTS idx_rfid_pass_valid_active_used ON rfid_card_passes (valid_date, active, used);

-- reason hâlâ NULL satır kalmadıysa NOT NULL ekleyebilirsiniz:
-- ALTER TABLE access_logs ALTER COLUMN reason SET NOT NULL;
