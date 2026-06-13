-- =============================================================================
-- Bodrum Aqua Park — DB yedek geri yükleme (Windows)
--
-- Kurulum: backend/scripts/windows/POSTGRESQL-YEDEK-KURULUM.txt
-- Yedek:   backend/scripts/windows/BodrumDbBackup.bat
-- =============================================================================

-- Yedek konumu (varsayılan):
--   C:\Backups\bodrum-aqua-park\bodrum_aqua_park_TARIH.backup

-- -----------------------------------------------------------------------------
-- GERİ YÜKLEME — Komut İstemi (pg_restore yolunu sürümünüze göre düzenleyin)
-- -----------------------------------------------------------------------------
--
--   set PGPASSWORD=123123
--   "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" ^
--     -h 127.0.0.1 -U postgres -d bodrum_aqua_park ^
--     --clean --if-exists ^
--     C:\Backups\bodrum-aqua-park\bodrum_aqua_park_2026-06-13_030001.backup
--
-- pgAdmin: bodrum_aqua_park → sağ tık → Restore... → .backup dosyasını seç
--
-- ÖNEMLİ: Geri yüklemeden önce POS uygulamalarını durdurun.

-- -----------------------------------------------------------------------------
-- Yedekleri listele (Komut İstemi)
-- -----------------------------------------------------------------------------
-- dir /o-d C:\Backups\bodrum-aqua-park\*.backup

-- -----------------------------------------------------------------------------
-- Log
-- -----------------------------------------------------------------------------
-- notepad C:\Backups\bodrum-aqua-park\bodrum-db-backup.log
