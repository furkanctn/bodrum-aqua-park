@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Bodrum Aqua Park — gece 23:00 kart sifirlama (Windows Gorev Zamanlayici)
rem Once pgAdmin'de postgresql-nightly-card-reset.sql calistirin (fonksiyon kurulumu).

set "PGHOST=100.78.186.3"
set "PGPORT=5433"
set "PGUSER=postgres"
set "PGDATABASE=bodrum_aqua_park"
set "PGPASSWORD=123123"

set "LOG_DIR=C:\Backups\bodrum-aqua-park"
set "LOG=%LOG_DIR%\bodrum-nightly-card-reset.log"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" 2>nul

set "PSQL="
for %%V in (17 16 15 14 13 12) do (
	if exist "C:\Program Files\PostgreSQL\%%V\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\%%V\bin\psql.exe"
)
if not defined PSQL (
	where psql >nul 2>&1
	if not errorlevel 1 set "PSQL=psql"
)

if not defined PSQL (
	echo [%date% %time%] HATA: psql bulunamadi.>> "%LOG%"
	echo [HATA] psql bulunamadi.
	exit /b 1
)

echo [%date% %time%] Kart sifirlama basliyor...>> "%LOG%"

set "PGPASSWORD=%PGPASSWORD%"
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %PGUSER% -d %PGDATABASE% -v ON_ERROR_STOP=1 -c "SELECT * FROM bodrum_nightly_card_reset();" >> "%LOG%" 2>&1
if errorlevel 1 (
	echo [%date% %time%] HATA: bodrum_nightly_card_reset basarisiz.>> "%LOG%"
	echo [HATA] Kart sifirlama basarisiz. Log: %LOG%
	exit /b 1
)

echo [%date% %time%] Tamam.>> "%LOG%"
echo [OK] Kart sifirlama tamamlandi. Log: %LOG%
exit /b 0
