@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Bodrum Aqua Park — PostgreSQL gunluk yedek (Windows)
rem Kurulum: backend/scripts/windows/POSTGRESQL-YEDEK-KURULUM.txt
rem Hedef klasor: C:\BodrumAquaPark\backup

rem ---------- Ayarlar ----------
rem Bu script DB sunucusunun kendisinde calisir.
rem 100.78.186.3 pg_hba'da yoksa / SSL zorunluysa baglanti reddedilir → 127.0.0.1 kullanin.
set "PGHOST=127.0.0.1"
set "PGPORT=5433"
set "PGUSER=postgres"
set "PGDATABASE=bodrum_aqua_park"
set "PGPASSWORD=123123"
rem Yerel baglanti icin (pg_hba "no encryption" hatasini onler)
set "PGSSLMODE=disable"

set "BACKUP_DIR=C:\BodrumAquaPark\backup"
set "KEEP_COUNT=30"
rem -----------------------------------------------------

set "LOG=%BACKUP_DIR%\bodrum-db-backup.log"
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%" 2>nul

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmmss"') do set "TS=%%i"
set "BACKUP_FILE=%BACKUP_DIR%\%PGDATABASE%_%TS%.backup"

set "PG_DUMP="
for %%V in (17 16 15 14 13 12) do (
	if exist "C:\Program Files\PostgreSQL\%%V\bin\pg_dump.exe" set "PG_DUMP=C:\Program Files\PostgreSQL\%%V\bin\pg_dump.exe"
)
if not defined PG_DUMP (
	where pg_dump >nul 2>&1
	if not errorlevel 1 set "PG_DUMP=pg_dump"
)

if not defined PG_DUMP (
	echo [%date% %time%] HATA: pg_dump bulunamadi. PostgreSQL bin klasorunu PATH'e ekleyin.>> "%LOG%"
	echo [HATA] pg_dump bulunamadi.
	exit /b 1
)

echo [%date% %time%] Yedek basliyor: %BACKUP_FILE%>> "%LOG%"

set "PGPASSWORD=%PGPASSWORD%"
"%PG_DUMP%" -h %PGHOST% -p %PGPORT% -U %PGUSER% -d %PGDATABASE% -F c -Z 6 -f "%BACKUP_FILE%"
if errorlevel 1 (
	echo [%date% %time%] HATA: pg_dump basarisiz.>> "%LOG%"
	echo [HATA] Yedek alinamadi. Log: %LOG%
	exit /b 1
)

echo [%date% %time%] Tamam: %BACKUP_FILE%>> "%LOG%"

powershell -NoProfile -Command ^
  "$dir='%BACKUP_DIR%'; $keep=%KEEP_COUNT%;" ^
  "$files=Get-ChildItem -Path $dir -Filter '%PGDATABASE%_*.backup' | Sort-Object LastWriteTime -Descending;" ^
  "if($files.Count -gt $keep){$toRemove=$files | Select-Object -Skip $keep; foreach($f in $toRemove){Remove-Item -LiteralPath $f.FullName -Force; Write-Host ('Silindi: '+$f.FullName)}}"

for /f %%c in ('dir /b "%BACKUP_DIR%\%PGDATABASE%_*.backup" 2^>nul ^| find /c /v ""') do set "CNT=%%c"
echo [%date% %time%] Aktif yedek sayisi: !CNT!>> "%LOG%"
echo [OK] Yedek alindi: %BACKUP_FILE%
exit /b 0
