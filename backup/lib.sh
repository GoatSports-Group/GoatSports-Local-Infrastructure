#!/usr/bin/env bash
# Dùng chung cho backup.sh và restore.sh. Mọi remote rclone khai báo bằng biến môi trường, không có file cấu hình:
#   source: bucket R2 chính (ảnh, CCCD, giấy phép)      backup: bucket R2 riêng chứa bản backup
#   vault:  lớp mã hóa rclone crypt trên "backup" — mọi thứ ghi vào bucket backup đều đi qua đây.

require_env() {
    local missing=0
    for name in "$@"; do
        if [ -z "${!name:-}" ]; then
            echo "Thiếu biến môi trường $name" >&2
            missing=1
        fi
    done
    [ "$missing" -eq 0 ] || exit 2
}

configure_rclone() {
    require_env CLOUDFLARE_R2_ENDPOINT BACKUP_R2_ACCESS_KEY BACKUP_R2_SECRET_KEY BACKUP_R2_BUCKET \
        BACKUP_ENCRYPTION_PASSWORD BACKUP_ENCRYPTION_SALT

    export RCLONE_CONFIG_BACKUP_TYPE=s3
    export RCLONE_CONFIG_BACKUP_PROVIDER=Cloudflare
    export RCLONE_CONFIG_BACKUP_ACCESS_KEY_ID="$BACKUP_R2_ACCESS_KEY"
    export RCLONE_CONFIG_BACKUP_SECRET_ACCESS_KEY="$BACKUP_R2_SECRET_KEY"
    export RCLONE_CONFIG_BACKUP_ENDPOINT="$CLOUDFLARE_R2_ENDPOINT"
    export RCLONE_CONFIG_BACKUP_NO_CHECK_BUCKET=true

    export RCLONE_CONFIG_VAULT_TYPE=crypt
    export RCLONE_CONFIG_VAULT_REMOTE="backup:$BACKUP_R2_BUCKET"
    RCLONE_CONFIG_VAULT_PASSWORD="$(rclone obscure "$BACKUP_ENCRYPTION_PASSWORD")"
    RCLONE_CONFIG_VAULT_PASSWORD2="$(rclone obscure "$BACKUP_ENCRYPTION_SALT")"
    export RCLONE_CONFIG_VAULT_PASSWORD RCLONE_CONFIG_VAULT_PASSWORD2

    if [ -n "${CLOUDFLARE_R2_ACCESS_KEY:-}" ]; then
        export RCLONE_CONFIG_SOURCE_TYPE=s3
        export RCLONE_CONFIG_SOURCE_PROVIDER=Cloudflare
        export RCLONE_CONFIG_SOURCE_ACCESS_KEY_ID="$CLOUDFLARE_R2_ACCESS_KEY"
        export RCLONE_CONFIG_SOURCE_SECRET_ACCESS_KEY="$CLOUDFLARE_R2_SECRET_KEY"
        export RCLONE_CONFIG_SOURCE_ENDPOINT="$CLOUDFLARE_R2_ENDPOINT"
        export RCLONE_CONFIG_SOURCE_NO_CHECK_BUCKET=true
    fi
}

# Số dòng thật (không phải ước lượng n_live_tup) của mọi bảng, mỗi dòng "schema.bảng|số", xếp theo tên.
PG_COUNT_SQL="SELECT table_schema || '.' || table_name,
       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name),
                                            false, true, '')))[1]::text
FROM information_schema.tables
WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema')
ORDER BY 1;"

# Giữ đúng dòng "tên|số": psql có thể in kèm thẻ lệnh như BEGIN / SET / COMMIT.
only_counts() {
    grep -E '^[^|]+\|[0-9]+$' || true
}

# Từ log mongodump ("done dumping db.coll (N documents)") hoặc mongorestore
# ("finished restoring db.coll (N documents, F failures)"): mỗi dòng "db.coll|N", xếp theo tên.
# Bỏ admin/config/local: user và role được mongorestore khôi phục theo đường riêng, không in dòng "finished".
mongo_counts() {
    sed -nE 's/.*(done dumping|finished restoring) ([^ ]+) \(([0-9]+) documents?.*/\2|\3/p' \
        | { grep -vE '^(admin|config|local)\.' || true; } | sort
}

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# Báo lỗi ra ngoài nếu có BACKUP_ALERT_URL:
#   Telegram: https://api.telegram.org/bot<TOKEN>/sendMessage?chat_id=<CHAT_ID>
#   Slack / Discord: URL webhook (gửi JSON có cả "text" lẫn "content").
alert() {
    local message="$1"
    log "CẢNH BÁO: $message"
    if [[ "${BACKUP_ALERT_URL:-}" == *api.telegram.org* ]]; then
        local chat_id="${BACKUP_ALERT_URL#*chat_id=}"
        chat_id="${chat_id%%&*}"
        curl -fsS -m 10 "${BACKUP_ALERT_URL%%\?*}" \
            --data-urlencode "chat_id=$chat_id" --data-urlencode "text=$message" >/dev/null || true
    elif [ -n "${BACKUP_ALERT_URL:-}" ]; then
        local json
        json=$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g')
        curl -fsS -m 10 -H 'Content-Type: application/json' \
            -d "{\"text\":\"$json\",\"content\":\"$json\"}" "$BACKUP_ALERT_URL" >/dev/null || true
    fi
}
