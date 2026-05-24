@echo off
setlocal EnableExtensions
if /i "%~1"=="exe" goto :build_exe
if /i "%~1"=="desktop" goto :run_desktop
if /i "%~1"=="dev" goto :run_dev_mvn

rem ========== Varsayilan: yerel Spring (8081) + Edge; DB = config\application.properties ==========
rem Profiller: pos (PostgreSQL) + lowresource (yazici / dusuk RAM)
rem Ilk kurulum: config\application.properties.example -> properties (sifre duzenle)
rem JavaFX: BodrumAquaPark.bat desktop | Gelistirme: BodrumAquaPark.bat dev

cd /d "%~dp0"
for %%I in ("%~dp0.") do set "HERE=%%~fI"
set "JAR=bodrum-aqua-park-api-1.2.0.1.jar"
set "LAUNCHER_JAR=bodrum-aqua-park-api-1.2.0.1-launcher.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SERVER_LOG=%HERE%\bodrum-sunucu.log"
set "LOG_FILE=%SERVER_LOG%"
set "SPRING_PROFILES_ACTIVE=pos,lowresource"
set "JAVA_OPTS_SERVER=-Djava.awt.headless=false -Xms192m -Xmx352m -XX:MaxMetaspaceSize=112m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"
set "APP_PRINTER_WINDOWS_QUEUE=Sewo"

call :ensure_pos_config
if errorlevel 1 exit /b 1

echo [%date% %time%] Edge modu. Klasor: %CD%>> "%LOG%"

if not exist "%JAR%" (
	echo [HATA] "%JAR%" bu klasorde yok.
	pause
	exit /b 1
)
where java >>"%LOG%" 2>&1
if errorlevel 1 (
	echo [HATA] java bulunamadi. JDK 21 kurun.
	pause
	exit /b 1
)

echo Sunucu baslatiliyor (ayri pencere)...
echo [%date% %time%] Sunucu gunlugu: %SERVER_LOG%>> "%LOG%"
echo [%date% %time%] Profil: %SPRING_PROFILES_ACTIVE% config: %HERE%\config>> "%LOG%"
start "BodrumAquaPark Sunucu" /min cmd /k "cd /d ""%HERE%"" && set ""SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%"" && set ""LOG_FILE=%LOG_FILE%"" && set ""APP_PRINTER_WINDOWS_QUEUE=%APP_PRINTER_WINDOWS_QUEUE%"" && set ""AQUAPARK_LAUNCHER_JAR=%HERE%\%LAUNCHER_JAR%"" && java %JAVA_OPTS_SERVER% -jar ""%HERE%\%JAR%"""

set "WAIT_MAX=60"
echo Edge icin bekleniyor (sunucu hazir olunca acilir; max %WAIT_MAX% sn)...
for /L %%S in (1,1,%WAIT_MAX%) do (
	powershell -NoProfile -Command ^
		"try { $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 'http://127.0.0.1:8081/api/health'; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
	if not errorlevel 1 goto :server_ready
	timeout /t 1 /nobreak >nul
)
:server_ready

set "POS_BASE=http://127.0.0.1:8081"
set "URL=%POS_BASE%/index.html"
set "BROWSER_EXTRA=--disable-extensions --no-first-run --disable-default-apps --disable-features=Translate,MediaRouter --disk-cache-size=1048576"
if /i "%POS_EDGE_FULLSCREEN%"=="1" set "BROWSER_EXTRA=%BROWSER_EXTRA% --start-fullscreen"

echo.
echo POS adresi: %URL%
echo DB ayarlari: %HERE%\config\application.properties
echo Sunucu gunlugu: %SERVER_LOG%
echo.

if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
	start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" %BROWSER_EXTRA% --app=%URL%
	goto :edge_done
)
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
	start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" %BROWSER_EXTRA% --app=%URL%
	goto :edge_done
)
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
	start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" %BROWSER_EXTRA% --app=%URL%
	goto :edge_done
)
if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
	start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" %BROWSER_EXTRA% --app=%URL%
	goto :edge_done
)
start "" "%URL%"

:edge_done
echo Edge / tarayici acildi. Sunucu penceresini KAPATMAYIN.
echo JavaFX: BodrumAquaPark.bat desktop
pause
goto :eof

rem ========== config\application.properties (Spring ./config/ klasorunu okur) ==========
:ensure_pos_config
set "CFG_DIR=%HERE%\config"
set "CFG_FILE=%CFG_DIR%\application.properties"
set "CFG_EXAMPLE=%CFG_DIR%\application.properties.example"
if not exist "%CFG_DIR%" mkdir "%CFG_DIR%"
if exist "%CFG_FILE%" goto :eof
if exist "%CFG_EXAMPLE%" (
	copy /Y "%CFG_EXAMPLE%" "%CFG_FILE%" >nul
) else (
	echo # PostgreSQL - bu dosyayi duzenleyin> "%CFG_FILE%"
	echo spring.datasource.url=jdbc:postgresql://DATA-SERVER.aqua.local:5433/bodrum_aqua_park>> "%CFG_FILE%"
	echo spring.datasource.username=postgres>> "%CFG_FILE%"
	echo spring.datasource.password=DEGISTIR>> "%CFG_FILE%"
	echo app.jwt.secret=DEGISTIR-en-az-32-karakter-gizli-anahtar>> "%CFG_FILE%"
)
echo.
echo [BILGI] Ilk kurulum: %CFG_FILE%
echo         PostgreSQL sifresi ve JWT anahtarini duzenleyin, kaydedin.
echo.
notepad "%CFG_FILE%"
echo Duzenleme bittikten sonra Enter ile devam edin...
pause >nul
goto :eof

rem ========== JavaFX tek pencere ==========
:run_desktop
cd /d "%~dp0"
for %%I in ("%~dp0.") do set "HERE=%%~fI"
set "JAR=bodrum-aqua-park-api-1.2.0.1.jar"
set "LAUNCHER_JAR=bodrum-aqua-park-api-1.2.0.1-launcher.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SERVER_LOG=%HERE%\bodrum-sunucu.log"
set "LOG_FILE=%SERVER_LOG%"
set "SPRING_PROFILES_ACTIVE=pos,lowresource"
set "APP_PRINTER_WINDOWS_QUEUE=Sewo"
set "JAVA_OPTS=-Djava.awt.headless=false -Xms192m -Xmx352m -XX:MaxMetaspaceSize=112m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"
set "JAVA_OPTS=%JAVA_OPTS% -Dprism.order=sw -Dprism.verbose=false"
if exist "%~dp0javafx-logging.properties" (
	set "JAVA_OPTS=%JAVA_OPTS% -Djava.util.logging.config.file=%~dp0javafx-logging.properties"
)
call :ensure_pos_config
if errorlevel 1 exit /b 1
if not exist "%JAR%" (
	echo [HATA] "%JAR%" yok.
	pause
	exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
	echo [HATA] java bulunamadi.
	pause
	exit /b 1
)
set "AQUAPARK_LAUNCHER_JAR=%HERE%\%LAUNCHER_JAR%"
echo JavaFX masaustu modu...
java %JAVA_OPTS% -jar "%HERE%\%JAR%" --desktop
if errorlevel 1 pause
goto :eof

rem ========== Maven: spring-boot:run ==========
:run_dev_mvn
setlocal EnableDelayedExpansion
for %%I in ("%~dp0..\..") do set "BACKEND_ROOT=%%~fI"
cd /d "!BACKEND_ROOT!"
if not exist "!BACKEND_ROOT!\mvnw.cmd" (
	echo [HATA] mvnw.cmd bulunamadi: !BACKEND_ROOT!
	pause
	exit /b 1
)
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev"
call "!BACKEND_ROOT!\mvnw.cmd" spring-boot:run
set "EC=!ERRORLEVEL!"
if not "!EC!"=="0" echo [HATA] Cikis: !EC!
pause
exit /b !EC!

rem ========== exe: jpackage ==========
:build_exe
chcp 65001 >nul
cd /d "%~dp0..\.."
set "BACKEND=%CD%"
set "JARFILE=bodrum-aqua-park-api-1.2.0.1.jar"
set "OUT=%BACKEND%\dist-exe"
if not defined JAVA_HOME (
	echo [HATA] JAVA_HOME tanimli degil.
	pause
	exit /b 1
)
set "JPKG=%JAVA_HOME%\bin\jpackage.exe"
if not exist "%JPKG%" (
	echo [HATA] jpackage yok: %JPKG%
	pause
	exit /b 1
)
cd /d "%BACKEND%"
if exist "%BACKEND%\mvnw.cmd" (
	call "%BACKEND%\mvnw.cmd" -DskipTests package
) else (
	call mvn -f "%BACKEND%\pom.xml" -DskipTests package
)
if errorlevel 1 ( pause & exit /b 1 )
if exist "%OUT%\BodrumAquaPark" rmdir /s /q "%OUT%\BodrumAquaPark"
set "WINICON=%BACKEND%\packaging\windows\app.ico"
set "JPKG_ICON_ARG="
if exist "%WINICON%" set JPKG_ICON_ARG=--icon "%WINICON%"
"%JPKG%" --input "%BACKEND%\target" --main-jar "%JARFILE%" --main-class org.springframework.boot.loader.launch.JarLauncher --java-options "-Dlaunch.desktop=true,-Xms192m,-Xmx352m,-XX:MaxMetaspaceSize=112m,-XX:+UseG1GC,-XX:MaxGCPauseMillis=250,-Dprism.order=sw,-Dspring.profiles.active=pos,lowresource" --name BodrumAquaPark --app-version 1.2.0.1 --vendor "Bodrum Aqua Park" --type app-image --dest "%OUT%" --win-shortcut --win-menu --win-menu-group "Bodrum Aqua Park" %JPKG_ICON_ARG%
pause
goto :eof
