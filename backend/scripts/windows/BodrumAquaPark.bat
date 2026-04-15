@echo off
setlocal
if /i "%~1"=="exe" goto :build_exe
if /i "%~1"=="desktop" goto :run_desktop
if /i "%~1"=="dev" goto :run_dev_mvn

rem ========== Varsayilan: Sunucu + Edge (hizli, Chromium) ==========
rem JavaFX WebView cok daha yavas; POS icin Edge onerilir.
rem JavaFX tek pencere: BodrumAquaPark.bat desktop
rem Gelistirme (Maven, JAR sart degil): BodrumAquaPark.bat dev
rem POS arayuzu JAR icindedir - kod degisince: mvn package, sonra JAR'i degistirin

cd /d "%~dp0"

set "JAR=bodrum-aqua-park-api-0.0.1-SNAPSHOT.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SPRING_PROFILES_ACTIVE=lowresource,dev"
rem Sunucu JVM (JavaFX yok - Prism gerekmez)
set "JAVA_OPTS_SERVER=-Xms192m -Xmx352m -XX:MaxMetaspaceSize=112m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"

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

echo Sunucu baslatiliyor (ayri pencere, gorev cubugunda kalabilir)...
rem Calisma dizini yukarida ayarli; alt cmd ayni klasoru miras alir
start "BodrumAquaPark Sunucu" /min cmd /k java %JAVA_OPTS_SERVER% -jar "%CD%\%JAR%"

echo Edge icin bekleniyor (yavas PC: 25 sn)...
timeout /t 20 /nobreak >nul

rem posPerf=1: zayif POS / zoom kapali (aqua_pos_perf). Varsayilan: tam zoom.
rem localhost ile 127.0.0.1 farkli origin (oturum/zoom ayri). Edge --app ile de localhost kullan.
set "URL=http://localhost:8081/index.html"
set "BROWSER_EXTRA=--disable-extensions --no-first-run --disable-default-apps --disable-features=Translate,MediaRouter --disk-cache-size=1048576"

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
echo.
echo Edge / tarayici acildi. Sunucu penceresini KAPATMAYIN.
echo JavaFX ile acmak icin: BodrumAquaPark.bat desktop
echo.
pause
goto :eof

rem ========== JavaFX tek pencere (yavas POS'ta onerilmez) ==========
:run_desktop
cd /d "%~dp0"
set "JAR=bodrum-aqua-park-api-0.0.1-SNAPSHOT.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SPRING_PROFILES_ACTIVE=lowresource,dev"
set "JAVA_OPTS=-Xms192m -Xmx352m -XX:MaxMetaspaceSize=112m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"
set "JAVA_OPTS=%JAVA_OPTS% -Dprism.order=sw -Dprism.verbose=false"
if exist "%~dp0javafx-logging.properties" (
	set "JAVA_OPTS=%JAVA_OPTS% -Djava.util.logging.config.file=%~dp0javafx-logging.properties"
)
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
echo JavaFX masaustu modu (yavas olabilir)...
java %JAVA_OPTS% -jar "%CD%\%JAR%" --desktop
if errorlevel 1 (
	echo Hata kodu: %ERRORLEVEL%
	pause
)
goto :eof

rem ========== Maven: spring-boot:run (backend kaynak; mvnw gerekir) ==========
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
echo.
echo Bodrum Aqua Park API - spring-boot:run
echo Dizin: !BACKEND_ROOT!
echo Profil: !SPRING_PROFILES_ACTIVE!
echo.
call "!BACKEND_ROOT!\mvnw.cmd" spring-boot:run
set "EC=!ERRORLEVEL!"
if not "!EC!"=="0" echo [HATA] Cikis: !EC!
echo.
pause
exit /b !EC!

rem ========== exe: jpackage (JavaFX gomulu; genelde Edge modu tercih edin) ==========
:build_exe
chcp 65001 >nul
cd /d "%~dp0..\.."
set "BACKEND=%CD%"
set "JARFILE=bodrum-aqua-park-api-0.0.1-SNAPSHOT.jar"
set "OUT=%BACKEND%\dist-exe"

echo Bodrum Aqua Park - EXE olusturma (jpackage)
echo Backend: %BACKEND%
echo.

if not defined JAVA_HOME (
	echo [HATA] JAVA_HOME tanimli degil. JDK 21 kurun.
	pause
	exit /b 1
)
set "JPKG=%JAVA_HOME%\bin\jpackage.exe"
if not exist "%JPKG%" (
	echo [HATA] jpackage yok: %JPKG%
	pause
	exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
	echo [HATA] mvn bulunamadi.
	pause
	exit /b 1
)

echo [1/2] mvn package...
call mvn -f "%BACKEND%\pom.xml" -DskipTests package
if errorlevel 1 ( pause & exit /b 1 )

if not exist "%BACKEND%\target\%JARFILE%" (
	echo [HATA] JAR yok: %BACKEND%\target\%JARFILE%
	pause
	exit /b 1
)

if exist "%OUT%\BodrumAquaPark" (
	echo Eski cikis siliniyor...
	rmdir /s /q "%OUT%\BodrumAquaPark"
)

echo [2/2] jpackage...
"%JPKG%" ^
	--input "%BACKEND%\target" ^
	--main-jar "%JARFILE%" ^
	--main-class org.springframework.boot.loader.launch.JarLauncher ^
	--java-options "-Dlaunch.desktop=true,-Xms192m,-Xmx352m,-XX:MaxMetaspaceSize=112m,-XX:+UseG1GC,-XX:MaxGCPauseMillis=250,-Dprism.order=sw,-Dspring.profiles.active=lowresource,dev" ^
	--name BodrumAquaPark ^
	--app-version 1.0.0 ^
	--vendor "Bodrum Aqua Park" ^
	--type app-image ^
	--dest "%OUT%" ^
	--win-shortcut

if errorlevel 1 ( pause & exit /b 1 )

echo.
echo Tamam: %OUT%\BodrumAquaPark\BodrumAquaPark.exe
echo Not: EXE JavaFX icerir; hiz icin masaustunde BodrumAquaPark.bat (Edge) kullanin.
pause
