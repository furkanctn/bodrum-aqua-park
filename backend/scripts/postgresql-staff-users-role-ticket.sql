-- Bilet satış (TICKET) rolü: eski veritabanlarında role sütunu CHECK ile yalnızca
-- ADMIN, SUPERVISOR, CASHIER kabul ediliyordu; TICKET eklenir.
--
-- Hata: new row for relation "staff_users" violates check constraint "staff_users_role_check"
-- Çalıştırma (örnek):
--   psql -h localhost -U postgres -d bodrum_aqua_park -f scripts/postgresql-staff-users-role-ticket.sql

ALTER TABLE staff_users DROP CONSTRAINT IF EXISTS staff_users_role_check;

ALTER TABLE staff_users
	ADD CONSTRAINT staff_users_role_check CHECK (
		role IN ('ADMIN', 'SUPERVISOR', 'CASHIER', 'TICKET')
	);
