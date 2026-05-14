# Turnike / RFID erişim denetimi (Spring Boot)

Bu belge `POST /api/access/check`, `X-DEVICE-TOKEN`, Raspberry Pi entegrasyonu ve üretim kurulumunu özetler.

## Ortam değişkenleri

| Değişken | Açıklama |
|----------|----------|
| `APP_ACCESS_BOOTSTRAP_DEVICE_ID` | İlk kurulumda otomatik `TurnstileDevice.device_id` (boş = bootstrap kapalı) |
| `APP_ACCESS_BOOTSTRAP_DEVICE_TOKEN` | Düz metin token; veritabanında BCrypt ile saklanır |
| `APP_CORS_ALLOWED_ORIGINS` | Virgülle ayrı izinli kökenler (boş = CORS mapping yok) |
| `APP_ACCESS_RATE_LIMIT_PER_MINUTE` | `POST /api/access/check` için IP başına dakika limiti (`0` = sınırsız) |
| `APP_ACCESS_DEVICE_ONLINE_THRESHOLD_SEC` | Son kaç saniye içinde doğrulanmış istek varsa cihaz “online” |
| `SPRING_DATASOURCE_URL` / `USERNAME` / `Şifre` | PostgreSQL (prod) |
| `APP_JWT_SECRET` | POS / admin JWT (turnike endpoint’i JWT kullanmaz) |

## Güvenlik

- **`POST /api/access/check`**: JWT **beklenmez**; yalnızca gövde + `X-DEVICE-TOKEN` ile cihaz doğrulaması.
- Token veritabanında **BCrypt** hash; doğrulama `PasswordEncoder.matches`.
- Bilinmeyen cihazlarda bile sabit bir geçerli BCrypt deseni ile `matches` çağrılır (**timing** sızıntısı azaltma).
- Üretimde **HTTPS** (ters vekil TLS sonlandırması) zorunlu kabul edilmelidir.
- **Rate limit**: tek düğümde bellek içi IP limiti; çoklu instance için API Gateway / Redis önerilir.

## PostgreSQL

1. Veritabanı oluşturun, `application-prod.properties` veya ortam ile bağlantıyı verin.
2. `spring.jpa.hibernate.ddl-auto=update` (ilk kurulum) veya `validate` + elle migration.
3. Eski `access_logs.message` sütununuz varsa:

```sql
-- scripts/postgresql-access-logs-v2-migration.sql dosyasına bakın
```

## Actuator

- `GET /actuator/health` — canlılık / hazır kontrolü.
- Varsayılan: `show-details: never`.

## API

### Turnike kontrolü

```http
POST /api/access/check
Content-Type: application/json
X-DEVICE-TOKEN: <düz-token>
```

Gövde:

```json
{ "cardId": "04A1B2C3D4", "deviceId": "TURNSTILE-RPI-1" }
```

Başarılı örnek yanıt (`200`):

```json
{ "allowed": true, "message": "Geçiş onaylandı" }
```

Red (`200`, iş kuralı):

```json
{ "allowed": false, "message": "Geçersiz cihaz anahtarı" }
```

Token yok (`401`):

```json
{ "allowed": false, "message": "X-DEVICE-TOKEN gerekli" }
```

### curl örnekleri

Başarılı (gerçek kart + pas + token):

```bash
curl -sS -X POST "http://127.0.0.1:8081/api/access/check" \
  -H "Content-Type: application/json" \
  -H "X-DEVICE-TOKEN: gizli-token" \
  -d '{"cardId":"04A1B2C3D4","deviceId":"TURNSTILE-RPI-1"}'
```

Geçersiz token:

```bash
curl -sS -o /dev/stderr -w "%{http_code}" -X POST "http://127.0.0.1:8081/api/access/check" \
  -H "Content-Type: application/json" \
  -H "X-DEVICE-TOKEN: yanlis" \
  -d '{"cardId":"04A1B2C3D4","deviceId":"TURNSTILE-RPI-1"}'
```

Timeout (istemci tarafı 2 sn):

```bash
curl -sS --max-time 2 -X POST "http://127.0.0.1:8081/api/access/check" \
  -H "Content-Type: application/json" \
  -H "X-DEVICE-TOKEN: gizli-token" \
  -d '{"cardId":"04A1B2C3D4","deviceId":"TURNSTILE-RPI-1"}' || echo "curl timeout veya ağ hatası"
```

### Admin — turnike listesi

`GET /api/admin/turnstiles` — **ADMIN** JWT gerekir.

Dönen alanlar: `deviceId`, `label`, `active`, `online`, `lastSeenAt`, `lastSuccessfulAccessAt`, `failedAttemptsToday` (İstanbul günü başından beri reddedilen deneme sayısı).

### Admin — erişim günlükleri

`GET /api/access/logs` — **ADMIN** JWT (mevcut uç).

## Raspberry Pi

Örnek betik: `scripts/raspberry/access_check_reader.py`

Ortam: `API_BASE`, `DEVICE_ID`, `DEVICE_TOKEN`.

## Oran sınırı (öneri)

Çoklu sunucu veya yüksek hacim için:

- Nginx `limit_req` / API Gateway throttling
- Redis tabanlı rate limit (ör. Bucket4j + Redis)

## Loglama

- `AccessControlService`: her deneme için `access.check outcome=ALLOW|DENY` (kart kimliği maskelemeli).
- `AccessControlController`: HTTP `latencyMs`, `deviceId`, `allowed`.
- Tüm denemeler `access_logs` tablosuna (`reason`, `ip_address`, `user_agent`).
