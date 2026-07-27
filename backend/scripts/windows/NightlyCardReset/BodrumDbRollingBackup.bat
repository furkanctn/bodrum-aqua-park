@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Bodrum Aqua Park — 30 dk rolling yedek (HER ZAMAN AYNI DOSYA)
rem Once gecici dosyaya yazar, basariliysa asil dosyanin uzerine tasir.
rem Boylece yarim kalan dump onceki iyi yedegi bozmaz.
rem
rem Dosya: C:\BodrumAquaPark\backup\bodrum_aqua_park_rolling.backup
rem Zamanlama: BodrumDbRollingBackup-Zamanla.ps1 (her 30 dk)

rem ---------- Ayarlar ----------
rem Bu script DB sunucusunun kendisinde calisir (127.0.0.1).
set "PGHOST=127.0.0.1"
set "PGPORT=5433"
set "PGUSER=postgres"
set "PGDATABASE=bodrum_aqua_park"
set "PGPASSWORD=123123"
set "PGSSLMODE=disable"

set "BACKUP_DIR=C:\BodrumAquaPark\backup"
set "BACKUP_FILE=%BACKUP_DIR%\%PGDATABASE%_rolling.backup"
set "BACKUP_TMP=%BACKUP_DIR%\%PGDATABASE%_rolling.tmp.backup"
set "LOG=%BACKUP_DIR%\bodrum-db-rolling-backup.log"
rem -----------------------------------------------------

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%" 2>nul

set "PG_DUMP="
for %%V in (17 16 15 14 13 12) do (
	if exist "C:\Program Files\PostgreSQL\%%V\bin\pg_dump.exe" set "PG_DUMP=C:\Program Files\PostgreSQL\%%V\bin\pg_dump.exe"
)
if not defined PG_DUMP (
	where pg_dump >nul 2>&1
	if not errorlevel 1 set "PG_DUMP=pg_dump"
)

if not defined PG_DUMP (
	echo [%date% %time%] HATA: pg_dump bulunamadi.>> "%LOG%"
	echo [HATA] pg_dump bulunamadi.
	exit /b 1
)

echo [%date% %time%] Rolling yedek basliyor...>> "%LOG%"

if exist "%BACKUP_TMP%" del /f /q "%BACKUP_TMP%" >nul 2>&1

set "PGPASSWORD=%PGPASSWORD%"
"%PG_DUMP%" -h %PGHOST% -p %PGPORT% -U %PGUSER% -d %PGDATABASE% -F c -Z 6 -f "%BACKUP_TMP%"
if errorlevel 1 (
	echo [%date% %time%] HATA: pg_dump basarisiz. Eski rolling dosyaya dokunulmadi.>> "%LOG%"
	if exist "%BACKUP_TMP%" del /f /q "%BACKUP_TMP%" >nul 2>&1
	echo [HATA] Yedek alinamadi. Onceki dosya korundu: %BACKUP_FILE%
	echo Log: %LOG%
	exit /b 1
)

if not exist "%BACKUP_TMP%" (
	echo [%date% %time%] HATA: tmp yedek yok.>> "%LOG%"
	echo [HATA] Gecici yedek olusmadi.
	exit /b 1
)

for %%A in ("%BACKUP_TMP%") do set "TMP_SIZE=%%~zA"
if "!TMP_SIZE!"=="0" (
	echo [%date% %time%] HATA: tmp yedek 0 byte.>> "%LOG%"
	del /f /q "%BACKUP_TMP%" >nul 2>&1
	echo [HATA] Yedek bos — eski dosyaya dokunulmadi.
	exit /b 1
)

rem Atomik degisim: onceki iyi yedegin uzerine yaz
move /Y "%BACKUP_TMP%" "%BACKUP_FILE%" >nul
if errorlevel 1 (
	echo [%date% %time%] HATA: move basarisiz.>> "%LOG%"
	echo [HATA] Yedek tasinamadi.
	exit /b 1
)

echo [%date% %time%] Tamam: %BACKUP_FILE% (!TMP_SIZE! byte) — uzerine yazildi>> "%LOG%"
echo [OK] Rolling yedek guncellendi: %BACKUP_FILE%
exit /b 0
