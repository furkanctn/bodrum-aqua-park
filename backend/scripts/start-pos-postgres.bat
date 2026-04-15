@echo off
chcp 65001 >nul
setlocal
REM Bodrum Aqua Park — POS bank + yerel PostgreSQL
REM 1) PostgreSQL kurun, CREATE DATABASE bodrum_aqua_park;
REM 2) Bu dosyayı JAR ile aynı klasöre koyun.
REM 3) Aşağıdaki şifreyi düzenleyin. JWT için APP_JWT_SECRET önerilir (en az 32 karakter).

set "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bodrum_aqua_park"
set "SPRING_DATASOURCE_USERNAME=postgres"
set "SPRING_DATASOURCE_PASSWORD=CHANGEME"
set "APP_JWT_SECRET=bodrum-pos-uzun-gizli-anahtar-en-az-32-karakter-olmalidir"

cd /d "%~dp0"
java -jar bodrum-aqua-park-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=pos
endlocal
pause
