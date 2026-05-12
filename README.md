# Bodrum Aqua Park - Hizli Operasyon Notlari

Bu dokuman bugun yapilan kurulum ve sorun giderme adimlarinin ozetidir.

## 1) Mimari (Sahadaki Gercek Kurulum)

- 1 adet DB server (PostgreSQL)
- Coklu client kasa (JAR uygulamasi)
- Client cihazlar server'a kablolu yerel agdan baglanir

## 2) Client Uygulama Baslatma

Client PC'de `Start-Pos-Local-Postgres.bat` calistirilir.

BAT icinde:

- `DB_HOST` server IP'si olmali
- `SPRING_DATASOURCE_*` degerleri dogru olmali

## 3) Yazici Akisi (Ozet)

POS tarafinda iki yol vardir:

- `local` -> bu PC'nin USB yazicisi (Web Serial)
- `server` -> server tarafinda tanimli yazici hedefi

Pratikte cihaz/surucuya gore Web Serial her kasada stabil calismayabilir.
Bu nedenle fallback icin `server` secenegi korunmustur.

## 4) Bugun Cozulen Ana Problemler

1. **Aglanti problemleri**
   - Client -> DB baglantisinda IP/subnet/port kontrolleri netlestirildi.
2. **JAR dagitim problemi**
   - Bozuk kopya durumlari icin temiz build + yeniden paketleme akisi netlestirildi.
3. **Satis fisinde gorunurluk**
   - `EscPosUtil` icinde satis f isi feed satirlari artirildi.
4. **Yazici hedefi yonetimi**
   - POS tarafinda local/server secim davranisi duzenlendi; fallback akisi korunuyor.
5. **Windows calistirma scripti**
   - `Start-Pos-Local-Postgres.bat` guncellendi:
     - DB host ayari
     - Yazici hedef env ayarlari
     - `headless=false` ile baslatma

## 5) Hata Durumunda Hizli Kontrol

1. DB ping var mi?
2. `5432` portu acik mi?
3. BAT icindeki `DB_HOST` dogru mu?
4. POS'ta yazici hedefi dogru mu (`local` / `server`)?
5. Log dosyasi: `bodrum-sunucu.log`

## 6) Not

Saha operasyonunda oncelik stabilitedir. Cihaza gore local USB veya server queue yolu secilebilir; ikisi de desteklenecek sekilde birakilmistir.

