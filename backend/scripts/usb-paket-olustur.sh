#!/usr/bin/env bash
# Mac: JAR + Windows BAT dosyalarını tek klasörde toplar (USB'ye kopyalamak için).
# Kullanım: proje kökünden:  bash backend/scripts/usb-paket-olustur.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
	chmod +x backend/mvnw 2>/dev/null || true
	MVN="./backend/mvnw"
fi

echo "==> Maven package (Windows hedefli JAR, javafx.platform=win)"
$MVN -f backend/pom.xml clean package -DskipTests

VERSION="$(grep -A1 '<artifactId>bodrum-aqua-park-api</artifactId>' backend/pom.xml | tail -1 | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')"
if [[ -z "$VERSION" ]]; then
	echo "[HATA] pom.xml içinden sürüm okunamadı." >&2
	exit 1
fi

JAR_NAME="bodrum-aqua-park-api-${VERSION}.jar"
LAUNCHER_JAR="bodrum-aqua-park-api-${VERSION}-launcher.jar"
OUT="$ROOT/dist/usb-windows-pos"
mkdir -p "$OUT"
rm -f "$OUT"/bodrum-aqua-park-api-*.jar

if [[ ! -f "backend/target/$JAR_NAME" ]]; then
	echo "[HATA] backend/target/$JAR_NAME bulunamadı." >&2
	exit 1
fi
if [[ ! -f "backend/target/$LAUNCHER_JAR" ]]; then
	echo "[HATA] backend/target/$LAUNCHER_JAR bulunamadı." >&2
	exit 1
fi

cp "backend/target/$JAR_NAME" "$OUT/"
cp "backend/target/$LAUNCHER_JAR" "$OUT/"

# BAT içindeki JAR adlarını pom sürümüne göre üret (kaynak dosyada eski sürüm kalabilir)
sed -E \
	-e "s/bodrum-aqua-park-api-[0-9]+(\.[0-9]+){3}\.jar/bodrum-aqua-park-api-${VERSION}.jar/g" \
	-e "s/bodrum-aqua-park-api-[0-9]+(\.[0-9]+){3}-launcher\.jar/bodrum-aqua-park-api-${VERSION}-launcher.jar/g" \
	-e "s/--app-version [0-9]+(\.[0-9]+){3}/--app-version ${VERSION}/g" \
	backend/scripts/windows/BodrumAquaPark.bat > "$OUT/BodrumAquaPark.bat"

mkdir -p "$OUT/config"
cp backend/scripts/windows/config/application.properties.example "$OUT/config/"
cp backend/scripts/windows/javafx-logging.properties "$OUT/"
cp backend/scripts/windows/Olustur-Masaustu-Kisayolu.ps1 "$OUT/"
cp backend/scripts/windows/POS-USB-KURULUM.txt "$OUT/" 2>/dev/null || true

ZIP="$ROOT/dist/usb-windows-pos-${VERSION}-$(date +%Y%m%d).zip"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
	(cd "$OUT/.." && zip -rq "$(basename "$ZIP")" "$(basename "$OUT")")
else
	ZIP="(zip yok — klasörü doğrudan kopyalayın)"
fi

echo ""
echo "==> Tamam (sürüm ${VERSION}). USB'ye şunu kopyalayın:"
echo "    $OUT"
echo "    veya zip: $ZIP"
echo ""
echo "    POS'ta çift tıklayın: BodrumAquaPark.bat"
echo ""
