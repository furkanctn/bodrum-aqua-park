#!/usr/bin/env bash
# Mac: JAR + Windows BAT dosyalarını tek klasörde toplar (USB'ye kopyalamak için).
# Kullanım: proje kökünden:  bash backend/scripts/usb-paket-olustur.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> Maven package (Windows hedefli JAR, javafx.platform=win)"
mvn -f backend/pom.xml clean package -DskipTests

JAR_NAME="bodrum-aqua-park-api-0.0.1-SNAPSHOT.jar"
OUT="$ROOT/dist/usb-windows-pos"
mkdir -p "$OUT"

cp "backend/target/$JAR_NAME" "$OUT/"
cp backend/scripts/windows/BodrumAquaPark.bat "$OUT/"
cp backend/scripts/windows/javafx-logging.properties "$OUT/"
cp backend/scripts/windows/POS-USB-KURULUM.txt "$OUT/" 2>/dev/null || true

echo ""
echo "==> Tamam. USB'ye şunu kopyalayın:"
echo "    $OUT"
echo ""
echo "    POS'ta çift tıklayın: BodrumAquaPark.bat"
echo ""
