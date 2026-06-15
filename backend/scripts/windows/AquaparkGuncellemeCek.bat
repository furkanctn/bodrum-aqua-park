@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Bodrum Aqua Park — FTP sunucudan guncelleme kontrolu (POS bilgisayari)
rem Sunucudaki Aquapark_Update/latest.txt + Aquapark_Update/{surum}/app.jar dosyalarini
rem yerel C:\Aquapark_Update klasorune indirir. AQUAPARK_UPDATE_ROOT varsayilani da
rem C:\Aquapark_Update oldugu icin POS'ta ek ortam degiskeni gerekmez.
rem Kurulum: AquaparkGuncellemeCek-Zamanla.ps1 ile Gorev Zamanlayici'ya eklenir.

rem ---------- Ayarlar (kurulumda duzenleyin) ----------
set "FTP_HOST=10.100.172.145"
set "FTP_PORT=21"
set "FTP_USER=client"
set "FTP_PASS=1154"
set "FTP_REMOTE_DIR="

set "LOCAL_ROOT=C:\Aquapark_Update"
rem -----------------------------------------------------

if not exist "%LOCAL_ROOT%" mkdir "%LOCAL_ROOT%" 2>nul
set "LOG=%LOCAL_ROOT%\guncelleme-kontrol.log"

where curl >nul 2>&1
if errorlevel 1 (
	echo [%date% %time%] HATA: curl bulunamadi (Windows 10/11 ile gelir, PATH kontrol edin).>> "%LOG%"
	exit /b 1
)

set "REMOTE_LATEST=%LOCAL_ROOT%\latest.txt.indirilen"
del "%REMOTE_LATEST%" 2>nul

curl -sS --user "%FTP_USER%:%FTP_PASS%" "ftp://%FTP_HOST%:%FTP_PORT%!FTP_REMOTE_DIR!/latest.txt" -o "%REMOTE_LATEST%" 2>>"%LOG%"
if errorlevel 1 (
	echo [%date% %time%] HATA: latest.txt indirilemedi (sunucu/FTP bilgisi kontrol edin).>> "%LOG%"
	exit /b 1
)
if not exist "%REMOTE_LATEST%" (
	echo [%date% %time%] HATA: latest.txt indirilemedi (dosya olusmadi).>> "%LOG%"
	exit /b 1
)

set "REMOTE_VERSION="
set /p REMOTE_VERSION=<"%REMOTE_LATEST%"
set "REMOTE_VERSION=%REMOTE_VERSION: =%"

if "%REMOTE_VERSION%"=="" (
	echo [%date% %time%] HATA: latest.txt bos geldi.>> "%LOG%"
	del "%REMOTE_LATEST%" 2>nul
	exit /b 1
)

set "LOCAL_LATEST=%LOCAL_ROOT%\latest.txt"
set "LOCAL_VERSION="
if exist "%LOCAL_LATEST%" set /p LOCAL_VERSION=<"%LOCAL_LATEST%"

if "%REMOTE_VERSION%"=="%LOCAL_VERSION%" (
	del "%REMOTE_LATEST%" 2>nul
	exit /b 0
)

echo [%date% %time%] Yeni surum: "%LOCAL_VERSION%" -^> "%REMOTE_VERSION%", indiriliyor...>> "%LOG%"

set "VER_DIR=%LOCAL_ROOT%\%REMOTE_VERSION%"
if not exist "%VER_DIR%" mkdir "%VER_DIR%"

curl -sS --user "%FTP_USER%:%FTP_PASS%" "ftp://%FTP_HOST%:%FTP_PORT%!FTP_REMOTE_DIR!/%REMOTE_VERSION%/app.jar" -o "%VER_DIR%\app.jar.indiriliyor" 2>>"%LOG%"
if errorlevel 1 (
	echo [%date% %time%] HATA: app.jar indirilemedi.>> "%LOG%"
	del "%REMOTE_LATEST%" 2>nul
	exit /b 1
)

set "JARSIZE=0"
for %%F in ("%VER_DIR%\app.jar.indiriliyor") do set "JARSIZE=%%~zF"
if "%JARSIZE%"=="0" (
	echo [%date% %time%] HATA: app.jar bos/eksik indi.>> "%LOG%"
	del "%VER_DIR%\app.jar.indiriliyor" 2>nul
	del "%REMOTE_LATEST%" 2>nul
	exit /b 1
)

move /y "%VER_DIR%\app.jar.indiriliyor" "%VER_DIR%\app.jar" >nul
move /y "%REMOTE_LATEST%" "%LOCAL_LATEST%" >nul

echo [%date% %time%] Tamam: %REMOTE_VERSION% indirildi -^> %VER_DIR%\app.jar>> "%LOG%"
exit /b 0
