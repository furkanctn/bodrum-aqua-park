@echo off
chcp 65001 >nul
setlocal

rem ========== Bodrum Aqua Park POS - Windows Baslat ==========
rem PostgreSQL: 192.168.0.15:5432
rem Profil: pos (uretim)

cd /d "%~dp0"
for %%I in ("%~dp0.") do set "HERE=%%~fI"

set "JAR=bodrum-aqua-park-api-1.2.0.0.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SERVER_LOG=%HERE%\bodrum-sunucu.log"
set "LOG_FILE=%SERVER_LOG%"

rem === PostgreSQL Ayarlari ===
set "SPRING_PROFILES_ACTIVE=pos"
set "SPRING_DATASOURCE_URL=jdbc:postgresql://192.168.0.15:5432/bodrum_aqua_park"
set "SPRING_DATASOURCE_USERNAME=postgres"
set "SPRING_DATASOURCE_PASSWORD=123123"

rem === JVM Ayarlari ===
set "JAVA_OPTS=-Djava.awt.headless=false -Xms192m -Xmx512m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"

rem === Yazici Ayarlari (Sewo) ===
rem Windows kuyruk adi: Aygitlar ve Yazicilar'daki isim
set "APP_PRINTER_WINDOWS_QUEUE=Sewo"

echo.
echo ========================================
echo   Bodrum Aqua Park POS
echo ========================================
echo.
echo [%date% %time%] Baslatiliyor...>> "%LOG%"

if not exist "%JAR%" (
    echo [HATA] "%JAR%" bulunamadi!
    echo JAR dosyasini bu klasore kopyalayin.
    pause
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo [HATA] Java bulunamadi!
    echo JDK 21 kurun: https://adoptium.net
    pause
    exit /b 1
)

echo PostgreSQL: 192.168.0.15:5432
echo Veritabani: bodrum_aqua_park
echo.
echo Sunucu baslatiliyor...
echo [%date% %time%] Sunucu basliyor>> "%SERVER_LOG%"

rem Sunucuyu ayri pencerede baslat
start "BodrumAquaPark Sunucu" /min cmd /k "cd /d ""%HERE%"" && java %JAVA_OPTS% -jar ""%HERE%\%JAR%"""

rem Sunucu hazir olana kadar bekle (max 90 saniye)
set "WAIT_MAX=90"
echo Sunucu hazir olana kadar bekleniyor (max %WAIT_MAX% sn)...
for /L %%S in (1,1,%WAIT_MAX%) do (
    powershell -NoProfile -Command ^
        "try { $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 'http://127.0.0.1:8081/actuator/health'; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
    if not errorlevel 1 goto :server_ready
    timeout /t 1 /nobreak >nul
)
echo [UYARI] Sunucu %WAIT_MAX% saniyede hazir olmadi.
echo PostgreSQL sunucusunun (192.168.0.15) acik oldugundan emin olun.
pause
exit /b 1

:server_ready
echo.
echo [OK] Sunucu hazir!
echo.

rem POS adresi
set "URL=http://127.0.0.1:8081/index.html"
set "BROWSER_EXTRA=--disable-extensions --no-first-run --disable-default-apps --disable-features=Translate,MediaRouter --disk-cache-size=1048576"

rem Tam ekran istiyorsaniz asagidaki satiri aktif edin:
rem set "BROWSER_EXTRA=%BROWSER_EXTRA% --start-fullscreen"

echo POS adresi: %URL%
echo.

rem Edge veya Chrome ile ac
if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" %BROWSER_EXTRA% --app=%URL%
    goto :browser_done
)
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" %BROWSER_EXTRA% --app=%URL%
    goto :browser_done
)
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" %BROWSER_EXTRA% --app=%URL%
    goto :browser_done
)
rem Varsayilan tarayici
start "" "%URL%"

:browser_done
echo.
echo ========================================
echo   POS ACILDI
echo ========================================
echo.
echo - Sunucu penceresi gorev cubugunda (minimize)
echo - Sunucu penceresini KAPATMAYIN
echo - Gunluk: %SERVER_LOG%
echo.
echo Bu pencereyi kapatabilirsiniz.
echo.
pause
