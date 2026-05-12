@echo off
setlocal
rem =============================================================================
rem Bodrum kasa PC — Windows "Baslangic" klasorune bu BAT'in kisayolunu koyun.
rem Bu BAT ile ayni klasorde tutun: *-launcher.jar, (senkron sonrasi) app.jar, version.txt
rem Acilis zinciri:
rem   1) Smart Launcher: C:\Aquapark_Update\latest.txt + {surum}\app.jar -> bu klasorde app.jar
rem   2) Ana JAR (--desktop): JavaFX POS + Spring Boot
rem İstanbul'da surum klasoru guncellenince: latest.txt degisir; bir sonraki acilista
rem   veya calisan uygulamada "Evet" ile ayni Launcher tekrar senkron yapar.
rem =============================================================================

cd /d "%~dp0"
for %%I in ("%~dp0.") do set "HERE=%%~fI"

rem ---- Paylasim (sunucu) + yerel kurulum = bu BAT'in klasoru (HERE) ----
if not defined AQUAPARK_UPDATE_ROOT set "AQUAPARK_UPDATE_ROOT=C:\Aquapark_Update"
if not defined AQUAPARK_LOCAL_DIR set "AQUAPARK_LOCAL_DIR=%HERE%"
rem Hot-update icin Spring ayni launcher'i kullanir; JAR adini pom surumuyle eslestirin:
if not defined AQUAPARK_LAUNCHER_JAR set "AQUAPARK_LAUNCHER_JAR=%HERE%\bodrum-aqua-park-api-1.2.0.0-launcher.jar"
rem Surum sabit kalsin diye app.jar adi (Launcher bunu yazar):
if not defined AQUAPARK_LOCAL_JAR set "AQUAPARK_LOCAL_JAR=app.jar"

rem Spring Boot log / profil — kasa profilinize gore (ornek POS):
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=pos"
set "LOG_FILE=%AQUAPARK_LOCAL_DIR%\bodrum-sunucu.log"

rem ---- PostgreSQL (pos profili): sifre ZORUNLU; yoksa "SCRAM ... no password" hatasi ----
rem Asagidaki satirlari kendi sunucunuza gore acin (rem'i kaldirin) veya Windows'ta
rem Kalici ortam degiskeni olarak SPRING_DATASOURCE_* tanimlayin.
set "SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/bodrum_aqua_park"
set "SPRING_DATASOURCE_USERNAME=postgres"
set "SPRING_DATASOURCE_PASSWORD=123123"
echo %SPRING_PROFILES_ACTIVE% | findstr /i "pos" >nul 2>&1
if not errorlevel 1 if not defined SPRING_DATASOURCE_PASSWORD (
	echo [HATA] PostgreSQL: sifre yok. pos profili icin SPRING_DATASOURCE_PASSWORD ayarlayin.
	echo        BAT icinde SET satirlarini acin ^(rem kaldir^) veya ortam degiskeni kullanin.
	echo        Gecici test: set SPRING_PROFILES_ACTIVE=dev ^(H2; uretimde kullanmayin^)
	pause
	exit /b 1
)
	echo [HATA] Launcher JAR yok: "%AQUAPARK_LAUNCHER_JAR%"
	echo        Maven: mvn -DskipTests package  sonrasi *-launcher.jar bu dosyaya kopyalanmali.
	pause
	exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
	echo [HATA] java bulunamadi. JDK 21 PATH'te olmali.
	pause
	exit /b 1
)

mkdir "%AQUAPARK_LOCAL_DIR%" 2>nul
rem Launcher alt surumu kopyalar; --desktop ana uygulamaya iletilir.
java -jar "%AQUAPARK_LAUNCHER_JAR%" --desktop
set "EC=%ERRORLEVEL%"
if not "%EC%"=="0" echo [BILGI] Launcher cikis kodu: %EC%
exit /b %EC%
