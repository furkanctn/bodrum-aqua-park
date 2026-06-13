-- Manuel test: kart bakiyelerini, entry_gate ve RFID pasları şimdi sıfırlar.
-- Önce bir kez kurulum: psql ... -f postgresql-nightly-card-reset.sql
--
-- Kullanım:
--   psql -h 192.168.0.15 -U postgres -d bodrum_aqua_park -f run-nightly-card-reset-now.sql

SELECT * FROM bodrum_nightly_card_reset();
