@echo off
chcp 65001 >nul
setlocal

REM JAR ile aynı klasöre koyup çalıştırın.
REM DB_HOST satırına PostgreSQL server IP yazın (örn: 192.168.50.10).
REM Şifreyi kendi PostgreSQL şifrenizle değiştirin.
set "SPRING_PROFILES_ACTIVE=pos"
set "DB_HOST=192.168.10.1"
set "SPRING_DATASOURCE_URL=jdbc:postgresql://%DB_HOST%:5432/bodrum_aqua_park"
set "SPRING_DATASOURCE_USERNAME=postgres"
set "SPRING_DATASOURCE_PASSWORD=2228"

REM FIS YAZICI HEDEFI (server yolu):
REM 1) Windows yazici kuyrugu kullanacaksaniz (onerilen): asagidaki adi Aygitlar ve Yazicilar'dan birebir yazin.
REM 2) COM port kullanacaksaniz APP_PRINTER_WINDOWS_QUEUE satirini bos birakin, APP_PRINTER_PORT=COM3 gibi verin.
set "APP_PRINTER_WINDOWS_QUEUE=Sewo"
set "APP_PRINTER_PORT="
set "APP_PRINTER_BAUD=9600"
set "SPRING_MAIN_HEADLESS=false"

set "JAR=bodrum-aqua-park-api-1.2.0.0.jar"
cd /d "%~dp0"

if not exist "%JAR%" (
  echo [HATA] "%JAR%" bu klasorde bulunamadi.
  echo Bu .bat dosyasini JAR ile ayni klasore koyun.
  pause
  exit /b 1
)

java -Djava.awt.headless=false -jar "%JAR%"
endlocal
pause
