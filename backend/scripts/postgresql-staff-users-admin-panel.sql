-- staff_users: yönetim paneli yetkisi (kasiyer/süpervizör için JWT claim)
-- Bir kez çalıştırın, örnek:
--   psql -h localhost -U postgres -d bodrum_aqua_park -f scripts/postgresql-staff-users-admin-panel.sql

ALTER TABLE staff_users
	ADD COLUMN IF NOT EXISTS admin_panel_access boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN staff_users.admin_panel_access IS 'Yönetim paneli (/admin.html) erişimi; ADMIN rolünde JWT her zaman tam yetki';
