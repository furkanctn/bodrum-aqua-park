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
rem JAR klasorunun tam yolu (start ile acilan cmd bazen baska dizinde baslar; log/H2 yanlis yere yazilmasin)
for %%I in ("%~dp0.") do set "HERE=%%~fI"
set "JAR=bodrum-aqua-park-api-1.2.0.0.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SERVER_LOG=%HERE%\bodrum-sunucu.log"
rem Spring Boot: LOG_FILE ortam degiskeni = gunluk dosyasi (mutlak yol)
set "LOG_FILE=%SERVER_LOG%"
rem Spring gunlugu: bodrum-sunucu.log = SERVER_LOG (bodrum-baslat.log sadece BAT)
set "SPRING_PROFILES_ACTIVE=lowresource,dev"
rem Sunucu JVM (JavaFX yok - Prism gerekmez)
rem javax.print / Windows yazici kuyrugu: headless=false (ayrica application-lowresource spring.main.headless=false)
set "JAVA_OPTS_SERVER=-Djava.awt.headless=false -Xms192m -Xmx352m -XX:MaxMetaspaceSize=112m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"

rem Fiş (Windows): Aygiclar ve Yazicilar'daki kuyruk adi; doluysa COM gerekmez. Baska kasada farkli ad ise duzenleyin.
set "APP_PRINTER_WINDOWS_QUEUE=Sewo"

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
echo [%date% %time%] Sunucu gunlugu: %SERVER_LOG%>> "%LOG%"
echo [%date% %time%] BodrumAquaPark: java basliyor (LOG_FILE ayarli)>> "%SERVER_LOG%"
rem Alt cmd: cd + LOG_FILE miras; ic ice tirnak cmd icin "" seklinde
start "BodrumAquaPark Sunucu" /min cmd /k "cd /d ""%HERE%"" && java %JAVA_OPTS_SERVER% -jar ""%HERE%\%JAR%"""

rem Sabit timeout yerine sunucu hazir olana kadar bekle (yavas PC'lerde daha iyi).
set "WAIT_MAX=60"
echo Edge icin bekleniyor (sunucu hazir olunca acilir; max %WAIT_MAX% sn)...
for /L %%S in (1,1,%WAIT_MAX%) do (
	powershell -NoProfile -Command ^
		"try { $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 'http://127.0.0.1:8081/api/health'; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
	if not errorlevel 1 goto :server_ready
	timeout /t 1 /nobreak >nul
)
:server_ready

rem posPerf=1: zayif POS / zoom kapali (aqua_pos_perf). Varsayilan: tam zoom.
rem Yerel POS: 127.0.0.1 (localhost ile karisik acmayin — ayri cerez/oturum olur).
set "POS_BASE=http://127.0.0.1:8081"
set "URL=%POS_BASE%/index.html"
rem Edge: varsayilan pencere (uygulama gibi --app). Tam ekran istenirse: set POS_EDGE_FULLSCREEN=1 bu satirdan once.
set "BROWSER_EXTRA=--disable-extensions --no-first-run --disable-default-apps --disable-features=Translate,MediaRouter --disk-cache-size=1048576"
if /i "%POS_EDGE_FULLSCREEN%"=="1" set "BROWSER_EXTRA=%BROWSER_EXTRA% --start-fullscreen"

echo.
echo POS adresi: %URL%
echo Beyaz sayfa: tam olarak 127.0.0.1 ve 8081 portu olmali — yukaridaki satiri adres cubuguna yapistirin.
echo Gorev cubugunda "BodrumAquaPark Sunucu" penceresinde kirmizi Java hatasi var mi kontrol edin.
echo Sunucu ayrintili gunluk (hata ayiklama): %SERVER_LOG%
echo BAT kisa notlar: %LOG%
echo Asagidaki "Press any key" sadece bu pencereyi kapatmak icin; sunucu ayri pencerede calisir.
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
echo.
echo Edge / tarayici acildi. Sunucu penceresini KAPATMAYIN.
echo Sorun devam ederse Edge adres cubugunda tam olarak su olmali: %URL%
echo JavaFX ile acmak icin: BodrumAquaPark.bat desktop
echo.
pause
goto :eof

rem ========== JavaFX tek pencere (yavas POS'ta onerilmez) ==========
:run_desktop
cd /d "%~dp0"
for %%I in ("%~dp0.") do set "HERE=%%~fI"
set "JAR=bodrum-aqua-park-api-1.2.0.0.jar"
set "LOG=%~dp0bodrum-baslat.log"
set "SERVER_LOG=%HERE%\bodrum-sunucu.log"
set "LOG_FILE=%SERVER_LOG%"
set "SPRING_PROFILES_ACTIVE=lowresource,dev"
set "APP_PRINTER_WINDOWS_QUEUE=Sewo"
set "JAVA_OPTS=-Djava.awt.headless=false -Xms192m -Xmx352m -XX:MaxMetaspaceSize=112m -XX:+UseG1GC -XX:MaxGCPauseMillis=250 -XX:+UseStringDeduplication"
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
java %JAVA_OPTS% -jar "%HERE%\%JAR%" --desktop
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
set "JARFILE=bodrum-aqua-park-api-1.2.0.0.jar"
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
echo [1/2] JAR derleniyor (oncelik: backend\mvnw.cmd, yoksa mvn)...
cd /d "%BACKEND%"
if exist "%BACKEND%\mvnw.cmd" (
	call "%BACKEND%\mvnw.cmd" -DskipTests package
) else (
	where mvn >nul 2>&1
	if errorlevel 1 (
		echo [HATA] mvnw.cmd yok ve sistemde mvn bulunamadi.
		echo        Cozum: repoda backend\mvnw.cmd olmali VEYA Apache Maven kurup PATH'e ekleyin.
		pause
		exit /b 1
	)
	call mvn -f "%BACKEND%\pom.xml" -DskipTests package
)
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

set "WINICON=%BACKEND%\packaging\windows\app.ico"
set "JPKG_ICON_ARG="
if exist "%WINICON%" (
	set JPKG_ICON_ARG=--icon "%WINICON%"
	echo Masaustu / EXE simgesi: %WINICON%
) else (
	echo [BILGI] Simge yok. jpackage varsayilan simge kullanir.
	echo        Istediginiz .ico dosyasini su yola koyun: packaging\windows\app.ico
	echo        Sonra tekrar: BodrumAquaPark.bat exe
)

echo [2/2] jpackage...
"%JPKG%" ^
	--input "%BACKEND%\target" ^
	--main-jar "%JARFILE%" ^
	--main-class org.springframework.boot.loader.launch.JarLauncher ^
	--java-options "-Dlaunch.desktop=true,-Xms192m,-Xmx352m,-XX:MaxMetaspaceSize=112m,-XX:+UseG1GC,-XX:MaxGCPauseMillis=250,-Dprism.order=sw,-Dspring.profiles.active=lowresource,dev" ^
	--name BodrumAquaPark ^
	--app-version 1.2.0.0 ^
	--vendor "Bodrum Aqua Park" ^
	--type app-image ^
	--dest "%OUT%" ^
	--win-shortcut ^
	--win-menu ^
	--win-menu-group "Bodrum Aqua Park" %JPKG_ICON_ARG%

if errorlevel 1 ( pause & exit /b 1 )

echo.
echo Tamam: %OUT%\BodrumAquaPark\BodrumAquaPark.exe
echo Masaustu kisayolu: jpackage --win-shortcut ile olusturulur; Baslat menusu: Bodrum Aqua Park grubu.
echo Not: Bu EXE icinde Spring Boot + POS calisir (yerel sunucu). Uzak sunucu icin Edge modu BAT kullanin.
pause
