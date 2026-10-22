# Backup và khôi phục GOAT Sports

## Mục tiêu

| | Giá trị | Ý nghĩa |
|---|---|---|
| **RPO** | 24 giờ | Backup mỗi đêm 02:00 (giờ Việt Nam). Sự cố xảy ra thì mất tối đa dữ liệu của một ngày. |
| **RTO** | ≤ 1 giờ | Từ lúc có máy chạy được đến lúc hệ thống phục vụ lại. |
| **Lưu giữ** | 7 bản hằng ngày + bản Chủ nhật trong 4 tuần | File R2 bị xóa hay ghi đè được giữ thêm 28 ngày. |

## Backup những gì

| Nguồn | Cách | Ghi chú |
|---|---|---|
| PostgreSQL (keycloak, auth, venue, payment, club, social, notification, report) | `pg_dump -Fc` từng DB + `pg_dumpall --globals-only` (vai trò, mật khẩu) | Số dòng từng bảng được đếm **trong cùng snapshot** với dump, ghi vào `pg-<db>.counts` |
| MongoDB (audit, social, ai) | `mongodump --oplog` cả replica set | Nhất quán tại một thời điểm; số document lấy từ log của mongodump |
| File trên R2 (ảnh, CCCD, giấy phép, ảnh chat) | `rclone sync` sang `files/current`, bản bị xóa / ghi đè sang `files/deleted/<ngày>` | |
| Redis, Kafka | **Không** backup | Chỉ là dữ liệu tạm (phiên, OTP, hàng đợi) |
| Camunda / Elasticsearch | **Không** backup | Khôi phục bằng cách cho venue-service mở lại process (bước 5 phần Khôi phục thật) |

Mọi thứ ghi lên bucket backup đều qua **rclone crypt** (mã hóa cả nội dung lẫn tên file), vì dump có email, số điện thoại
và đường dẫn ảnh CCCD. Mỗi bản có `SHA256SUMS` và `manifest.txt`.

Cấu trúc trong bucket (đã giải mã): `db/<yyyy-MM-ddTHHmm>/`, `files/current/`, `files/deleted/<ngày>/`,
`status/last-success`, `drills/<bản>-<lúc diễn tập>.md`.

## Cài đặt (một lần)

1. **Tạo bucket R2 riêng**, ví dụ `goatsports-backup`. Không bật public access.
2. **Tạo API token R2** chỉ có quyền *Object Read & Write* trên **riêng bucket đó**.
3. **Tạo mật khẩu mã hóa** và lưu ở chỗ khác VPS (trình quản lý mật khẩu). **Mất hai giá trị này thì không giải mã được backup**:
   ```bash
   openssl rand -base64 32   # BACKUP_ENCRYPTION_PASSWORD
   openssl rand -base64 32   # BACKUP_ENCRYPTION_SALT
   ```
4. **Trên Dokploy**: Create Service → **Compose**, kiểu **Docker Compose** (không chọn Stack vì Stack không build được
   image), nguồn là repo GitLab `GoatSports-Local-Infrastructure`, nhánh như các stack khác, **Compose Path**
   `./compose/docker-compose-backup.yml`. Dokploy clone cả repo nên `build: ../backup` tìm thấy script. Khai báo các biến:

   | Biến | Lấy từ |
   |---|---|
   | `POSTGRES_USER`, `POSTGRES_PASSWORD` | như stack infra |
   | `MONGO_ROOT_USERNAME`, `MONGO_ROOT_PASSWORD` | như stack mongodb-search |
   | `CLOUDFLARE_R2_ENDPOINT`, `CLOUDFLARE_R2_BUCKET`, `CLOUDFLARE_R2_ACCESS_KEY`, `CLOUDFLARE_R2_SECRET_KEY` | như storage-service |
   | `BACKUP_R2_BUCKET`, `BACKUP_R2_ACCESS_KEY`, `BACKUP_R2_SECRET_KEY` | bước 1–2 |
   | `BACKUP_ENCRYPTION_PASSWORD`, `BACKUP_ENCRYPTION_SALT` | bước 3 |
   | `BACKUP_ALERT_URL` (tùy chọn) | webhook Discord / Slack nhận tin khi backup lỗi |

5. **Chạy thử ngay** thay vì đợi tới 02:00:
   ```bash
   docker exec goatsports-backup backup.sh
   ```
   Dòng cuối phải là `Backup <bản> xong trong ...`. Thử kênh cảnh báo bằng một lần backup cố tình lỗi (sai host):
   ```bash
   docker exec -e BACKUP_PG_HOST=khong-ton-tai goatsports-backup backup.sh
   ```
   Bản thành công gần nhất xem bằng:
   ```bash
   docker exec goatsports-backup bash -c '. /usr/local/bin/lib.sh; configure_rclone; rclone cat vault:status/last-success'
   ```

## Diễn tập khôi phục trên VPS

Diễn tập dựng PostgreSQL + MongoDB **trống** trong compose project riêng `goatsports-restore-drill`. Project này chỉ nằm
trên mạng riêng của nó (không vào `goatsports-network`), nên **không thể chạm DB production**. `restore.sh` còn từ chối
thêm nếu đích là `postgres` / `mongodb-search`.

Cần khoảng 1,3 GB RAM trong lúc chạy, nên làm ngoài giờ cao điểm.

```bash
cd <repo>/infrastructure/compose
# File env chứa POSTGRES_USER, CLOUDFLARE_R2_ENDPOINT, BACKUP_R2_*, BACKUP_ENCRYPTION_* (giống app backup)
docker compose -f docker-compose-restore-drill.yml --env-file /root/backup.env down -v       # luôn bắt đầu từ trống
time docker compose -f docker-compose-restore-drill.yml --env-file /root/backup.env \
    up --build --abort-on-container-exit --exit-code-from restore restore
docker compose -f docker-compose-restore-drill.yml --env-file /root/backup.env down -v       # dọn sạch
```

Chọn một bản cụ thể thay vì bản mới nhất: thêm `RESTORE_STAMP=2026-10-23T0200` trước lệnh `up`.

`restore.sh` tự làm:
1. tải bản backup từ bucket và giải mã;
2. kiểm `SHA256SUMS`, phát hiện file hỏng hoặc bị sửa;
3. khôi phục vai trò, rồi `pg_restore --exit-on-error` từng DB;
4. đếm lại **từng bảng** và so với số lúc backup;
5. `mongorestore --oplogReplay` rồi so số document **từng collection**;
6. in **biên bản** (thời gian từng bước, số DB, bảng, dòng, collection, document, các vấn đề, kết quả ĐẠT / KHÔNG ĐẠT)
   và lưu lên `vault:drills/`. Exit code khác 0 khi KHÔNG ĐẠT.

### Biên bản diễn tập (điền sau mỗi lần)

| Mục | Ghi |
|---|---|
| Ngày, người thực hiện | |
| Bản backup dùng | (từ biên bản tự sinh) |
| Thời gian khôi phục dữ liệu | (từ biên bản tự sinh) |
| Kết quả kiểm tra tự động | ĐẠT / KHÔNG ĐẠT |
| Kiểm tra tay: mở một DB đã khôi phục, xem vài đơn đặt sân và người dùng mới nhất có đúng không | |
| Sự cố gặp phải, cách xử lý | |
| RTO ước tính cho khôi phục thật = thời gian khôi phục dữ liệu + dựng lại service (bước 4–6 bên dưới) | |

Kiểm tra tay trong lúc stack diễn tập còn chạy (trước lệnh `down -v`):
```bash
docker compose -f docker-compose-restore-drill.yml exec drill-postgres \
    psql -U "$POSTGRES_USER" -d sports_venue -c "select booking_id, status, created_at from bookings order by created_at desc limit 5"
```

## Khôi phục thật (mất VPS hoặc mất dữ liệu)

1. Dựng lại VPS: Dokploy, `goatsports-network`, stack infra (postgres, redis, kafka) và stack mongodb-search, **chưa** deploy các service.
2. Chạy `restore.sh` vào DB mới, với cờ cho phép ghi vào host production:
   ```bash
   docker run --rm --network goatsports-network --env-file /root/backup.env \
       -e RESTORE_INTO_PRODUCTION=yes-recovering \
       -e RESTORE_PG_HOST=postgres -e RESTORE_PG_USER=<POSTGRES_USER> -e RESTORE_PG_PASSWORD=<POSTGRES_PASSWORD> \
       -e RESTORE_MONGO_HOST=mongodb-search:27017 \
       -e RESTORE_MONGO_USERNAME=<MONGO_ROOT_USERNAME> -e RESTORE_MONGO_PASSWORD=<MONGO_ROOT_PASSWORD> \
       --entrypoint restore.sh goatsports-backup:local latest
   ```
   Postgres phải **trống** (container mới, volume mới).
3. Khôi phục file R2 (bucket chính mới hoặc đã trống):
   ```bash
   docker exec goatsports-backup bash -c '. /usr/local/bin/lib.sh; configure_rclone; \
       rclone sync vault:files/current "source:$CLOUDFLARE_R2_BUCKET"'
   ```
4. Tạo lại vector index: chạy `infrastructure/mongodb-search/production/config/ensure-vector-index.js` như lúc dựng stack mongodb-search.
5. Camunda (dựng mới, trống): cho venue-service mở lại process các yêu cầu hủy đang dở. Job `CancellationWorkflowAdoptionJob`
   (mỗi 5 phút) chỉ nhận dòng chưa có process, nên xóa key process cũ:
   ```sql
   -- sports_venue
   UPDATE booking_cancellations SET workflow_instance_key = NULL, workflow_version = NULL
   WHERE status IN ('PENDING', 'REFUND_AWAITING_CLAIM', 'REFUND_AWAITING_BANK_ACCOUNT',
                    'REFUND_PROCESSING', 'REFUND_MANUAL_REVIEW', 'REFUND_FAILED');
   ```
   Phải xóa cả `workflow_version`: Zeebe mới đánh số job lại từ đầu, version cũ lớn hơn sẽ làm venue bỏ qua mọi cập nhật mới.
6. Deploy lại các service, rồi kiểm tra: đăng nhập, mở một vé đặt sân, xem ảnh, và gọi `GET /audit-service/api/v1/logs/integrity`
   (chuỗi hash nhật ký phải `intact: true`).
