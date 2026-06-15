#!/usr/bin/env bash
# Mac: JAR + Windows BAT dosyalarını tek klasörde toplar (USB'ye kopyalamak için).
# Kullanım: proje kökünden:  bash backend/scripts/usb-paket-olustur.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

INCLUDE_STATIC=false
if [[ "${1:-}" == "--with-static" ]]; then
	INCLUDE_STATIC=true
elif [[ -n "${1:-}" ]]; then
	echo "Kullanim: bash backend/scripts/usb-paket-olustur.sh [--with-static]" >&2
	exit 1
fi

MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
	chmod +x backend/mvnw 2>/dev/null || true
	MVN="./backend/mvnw"
fi

# Her paketlemede sürümün son rakamını otomatik artır (örn. 1.2.0.8 -> 1.2.0.9).
# pom.xml tek sürüm kaynağıdır; AppVersion (build-info) ve JAR adları buradan türer.
CURRENT_VERSION="$(grep -A1 '<artifactId>bodrum-aqua-park-api</artifactId>' backend/pom.xml | tail -1 | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')"
if [[ -z "$CURRENT_VERSION" ]]; then
	echo "[HATA] backend/pom.xml içinden mevcut sürüm okunamadı." >&2
	exit 1
fi

LAST="${CURRENT_VERSION##*.}"
PREFIX="${CURRENT_VERSION%.*}"
if ! [[ "$LAST" =~ ^[0-9]+$ ]]; then
	echo "[HATA] Sürüm formatı beklenmiyor (son segment sayı değil): $CURRENT_VERSION" >&2
	exit 1
fi
VERSION="${PREFIX}.$((LAST + 1))"

awk -v old="<version>${CURRENT_VERSION}</version>" -v new="<version>${VERSION}</version>" '
	/<artifactId>bodrum-aqua-park-api<\/artifactId>/ { print; getline; gsub(old, new); print; next }
	{ print }
' backend/pom.xml > backend/pom.xml.tmp && mv backend/pom.xml.tmp backend/pom.xml

echo "==> Sürüm artırıldı: ${CURRENT_VERSION} -> ${VERSION}"

echo "==> Maven package (Windows hedefli JAR, javafx.platform=win)"
$MVN -f backend/pom.xml clean package -DskipTests

JAR_NAME="bodrum-aqua-park-api-${VERSION}.jar"
LAUNCHER_JAR="bodrum-aqua-park-api-${VERSION}-launcher.jar"
OUT="$ROOT/dist/usb-windows-pos"
mkdir -p "$OUT"
rm -f "$OUT"/bodrum-aqua-park-api-*.jar
# Sadece runtime paket kalsin; onceki calistirmalardan kalan ekstra statik dosyalari temizle.
rm -f "$OUT"/POS-USB-KURULUM.txt
rm -f "$OUT"/AquaparkGuncellemeCek.bat
rm -f "$OUT"/AquaparkGuncellemeCek-Zamanla.ps1
rm -f "$OUT"/AquaparkIlkKurulum.ps1

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
if [[ -f "backend/scripts/windows/app.ico" ]]; then
	cp "backend/scripts/windows/app.ico" "$OUT/app.ico"
elif [[ -f "backend/packaging/windows/app.ico" ]]; then
	cp "backend/packaging/windows/app.ico" "$OUT/app.ico"
else
	rm -f "$OUT/app.ico"
fi

STATIC_OUT="$ROOT/dist/usb-windows-pos-static"
if [[ "$INCLUDE_STATIC" == "true" ]]; then
	mkdir -p "$STATIC_OUT"
	cp backend/scripts/windows/Olustur-Masaustu-Kisayolu.ps1 "$STATIC_OUT/"
	cp backend/scripts/windows/POS-USB-KURULUM.txt "$STATIC_OUT/" 2>/dev/null || true
	cp backend/scripts/windows/AquaparkGuncellemeCek.bat "$STATIC_OUT/"
	cp backend/scripts/windows/AquaparkGuncellemeCek-Zamanla.ps1 "$STATIC_OUT/"
	cp backend/scripts/windows/AquaparkIlkKurulum.ps1 "$STATIC_OUT/"
fi

ZIP="$ROOT/dist/usb-windows-pos-${VERSION}-$(date +%Y%m%d).zip"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
	(cd "$OUT/.." && zip -rq "$(basename "$ZIP")" "$(basename "$OUT")")
else
	ZIP="(zip yok — klasörü doğrudan kopyalayın)"
fi

# Sunucudaki Aquapark_Update klasör yapısı:
#   Aquapark_Update/latest.txt          -> sadece sürüm metni
#   Aquapark_Update/{VERSION}/app.jar   -> HotUpdateListenerService / SmartLauncher bunu okur (sadece JAR güncellemesi)
#   Aquapark_Update/{VERSION}/...       -> runtime paketi (JAR + launcher + BAT + config ornegi)
UPDATE_OUT="$ROOT/dist/aquapark-update"
rm -rf "$UPDATE_OUT"
mkdir -p "$UPDATE_OUT/$VERSION"
cp -R "$OUT/." "$UPDATE_OUT/$VERSION/"
cp "backend/target/$JAR_NAME" "$UPDATE_OUT/$VERSION/app.jar"
printf '%s' "$VERSION" > "$UPDATE_OUT/latest.txt"

echo ""
echo "==> Tamam (sürüm ${VERSION}). USB'ye şunu kopyalayın:"
echo "    $OUT"
echo "    veya zip: $ZIP"
echo ""
echo "    POS'ta çift tıklayın: BodrumAquaPark.bat"
echo ""
echo "==> Sunucuya (FileZilla ile) yüklenecek otomatik güncelleme klasörü:"
echo "    $UPDATE_OUT"
echo "    İçeriğini sunucudaki Aquapark_Update klasörünün üzerine yükleyin"
echo "    (latest.txt'nin üzerine yazılmalı; $VERSION klasörü eklenmeli)."
echo "    Bu klasör runtime paketini içerir (JAR + launcher + BAT + config örneği)."
if [[ "$INCLUDE_STATIC" == "true" ]]; then
	echo ""
	echo "==> Statik kurulum/dokuman dosyalari (ayri):"
	echo "    $STATIC_OUT"
else
	echo ""
	echo "==> Statik kurulum/dokuman dosyalari pakete eklenmedi."
	echo "    Gerektiginde uretilir: bash backend/scripts/usb-paket-olustur.sh --with-static"
fi
echo ""
echo "    - Çalışan POS'lar: app.jar'ı (~15 dk'da bir) görüp kendini günceller, devam eder."
echo "    - Yeni POS / tam tazeleme: gerekli oldugunda static klasorden AquaparkIlkKurulum.ps1"
echo "      alin, Yonetici PowerShell'de calistirin."
echo ""
