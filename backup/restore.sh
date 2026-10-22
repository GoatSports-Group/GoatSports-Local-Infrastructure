#!/usr/bin/env bash
# Diễn tập khôi phục: tải một bản backup (mặc định bản mới nhất), restore vào PostgreSQL + MongoDB TRỐNG,
# đối chiếu với manifest, đo thời gian và ghi biên bản lên vault:drills/.
#   restore.sh [latest|<stamp>]      vd: restore.sh 2026-10-23T0200
# KHÔNG BAO GIỜ trỏ vào DB production: script từ chối các host production.
set -Eeuo pipefail
source "$(dirname "$0")/lib.sh"

require_env RESTORE_PG_HOST RESTORE_PG_USER RESTORE_PG_PASSWORD RESTORE_MONGO_HOST
configure_rclone

# Khôi phục thật lên máy mới (host vẫn tên postgres / mongodb-search): đặt RESTORE_INTO_PRODUCTION=yes-recovering.
if [ "${RESTORE_INTO_PRODUCTION:-}" != "yes-recovering" ]; then
    case "$RESTORE_PG_HOST" in postgres|goatsports-postgres) echo "Từ chối: $RESTORE_PG_HOST là PostgreSQL production" >&2; exit 2;; esac
    case "${RESTORE_MONGO_HOST%%:*}" in mongodb-search|goatsports-mongodb-search) echo "Từ chối: $RESTORE_MONGO_HOST là MongoDB production" >&2; exit 2;; esac
fi

export PGPASSWORD="$RESTORE_PG_PASSWORD"
STAMP="${1:-latest}"
if [ "$STAMP" = "latest" ]; then
    STAMP="$(rclone lsf "vault:db" --dirs-only | sort | tail -n 1)"
    STAMP="${STAMP%/}"
fi
[ -n "$STAMP" ] || { echo "Chưa có bản backup nào trong vault:db" >&2; exit 1; }

WORK="/tmp/restore-$STAMP"
REPORT="$WORK/report.md"
problems=()
started=$(date +%s)
started_at="$(date '+%Y-%m-%d %H:%M:%S')"

log "Tải vault:db/$STAMP"
rclone copy "vault:db/$STAMP" "$WORK"
downloaded=$(( $(date +%s) - started ))

log "Kiểm checksum"
if ! (cd "$WORK" && sha256sum -c --quiet SHA256SUMS); then
    problems+=("Checksum không khớp: file backup đã hỏng hoặc bị sửa")
fi

until pg_isready -h "$RESTORE_PG_HOST" -U "$RESTORE_PG_USER" -q; do sleep 2; done
log "PostgreSQL: vai trò"
# Bỏ dòng CREATE/ALTER ROLE của chính user đang restore: user đó đã có, và ALTER ROLE ... PASSWORD sẽ đổi mật khẩu
# của nó sang mật khẩu production, làm các bước sau không đăng nhập được nữa.
grep -vE "^(CREATE|ALTER) ROLE \"?${RESTORE_PG_USER}\"?( |;)" "$WORK/pg-globals.sql" \
    | psql -h "$RESTORE_PG_HOST" -U "$RESTORE_PG_USER" -d postgres -qX -v ON_ERROR_STOP=1 \
        > "$WORK/pg-globals.restore.log" 2>&1 \
    || problems+=("Khôi phục vai trò PostgreSQL lỗi: $(tail -n 2 "$WORK/pg-globals.restore.log" | tr '\n' ' ')")

pg_rows=0
for dump in "$WORK"/pg-*.dump; do
    db="$(basename "$dump" .dump)"; db="${db#pg-}"
    log "PostgreSQL: $db"
    createdb -h "$RESTORE_PG_HOST" -U "$RESTORE_PG_USER" "$db"
    if ! pg_restore -h "$RESTORE_PG_HOST" -U "$RESTORE_PG_USER" -d "$db" --exit-on-error "$dump" 2> "$WORK/pg-$db.restore.log"; then
        problems+=("pg_restore $db lỗi: $(tail -n 3 "$WORK/pg-$db.restore.log" | tr '\n' ' ')")
        continue
    fi
    psql -h "$RESTORE_PG_HOST" -U "$RESTORE_PG_USER" -d "$db" -AtqX -c "$PG_COUNT_SQL" | only_counts > "$WORK/pg-$db.restored"
    if ! diff -u "$WORK/pg-$db.counts" "$WORK/pg-$db.restored" > "$WORK/pg-$db.diff"; then
        problems+=("Số dòng $db lệch so với lúc backup: $(grep -c '^[-+][^-+]' "$WORK/pg-$db.diff") dòng khác")
    fi
    pg_rows=$(( pg_rows + $(awk -F'|' '{ s += $2 } END { print s + 0 }' "$WORK/pg-$db.restored") ))
done
pg_done=$(( $(date +%s) - started ))

# MongoDB đích đã sẵn sàng: compose diễn tập chờ healthcheck trước khi chạy script này. Đích diễn tập không bật
# xác thực; khôi phục thật vào mongodb-search thì truyền RESTORE_MONGO_USERNAME / RESTORE_MONGO_PASSWORD (user root).
log "MongoDB"
mongo_auth=()
if [ -n "${RESTORE_MONGO_USERNAME:-}" ]; then
    mongo_auth=(--username "$RESTORE_MONGO_USERNAME" --password "$RESTORE_MONGO_PASSWORD" --authenticationDatabase admin)
fi
if ! mongorestore --host "$RESTORE_MONGO_HOST" "${mongo_auth[@]}" --gzip --archive="$WORK/mongo.archive.gz" --oplogReplay \
        2> "$WORK/mongo-restore.log"; then
    problems+=("mongorestore lỗi: $(tail -n 3 "$WORK/mongo-restore.log" | tr '\n' ' ')")
fi
mongo_counts < "$WORK/mongo-restore.log" > "$WORK/mongo.restored"
if ! diff -u "$WORK/mongo.counts" "$WORK/mongo.restored" > "$WORK/mongo.diff"; then
    problems+=("Số document MongoDB lệch so với lúc backup: $(grep -c '^[-+][^-+]' "$WORK/mongo.diff") collection khác")
fi
if grep -qE '[1-9][0-9]* document\(s\) failed to restore' "$WORK/mongo-restore.log"; then
    problems+=("mongorestore báo có document không restore được")
fi
mongo_docs=$(awk -F'|' '{ s += $2 } END { print s + 0 }' "$WORK/mongo.restored")
total=$(( $(date +%s) - started ))

result="ĐẠT"
[ "${#problems[@]}" -eq 0 ] || result="KHÔNG ĐẠT"
{
    echo "# Biên bản diễn tập khôi phục GOAT Sports"
    echo
    echo "| Mục | Giá trị |"
    echo "|---|---|"
    echo "| Bản backup | \`$STAMP\` |"
    echo "| Bắt đầu | $started_at |"
    echo "| Tải + giải mã | ${downloaded} giây |"
    echo "| Xong PostgreSQL | ${pg_done} giây |"
    echo "| Tổng thời gian khôi phục dữ liệu | ${total} giây |"
    echo "| PostgreSQL | $(ls "$WORK"/pg-*.dump | wc -l) DB, $(cat "$WORK"/pg-*.restored 2>/dev/null | wc -l) bảng, $pg_rows dòng |"
    echo "| MongoDB | $(wc -l < "$WORK/mongo.restored") collection, $mongo_docs document |"
    echo "| Kết quả | **$result** |"
    echo
    if [ "${#problems[@]}" -gt 0 ]; then
        echo "## Vấn đề"
        printf -- '- %s\n' "${problems[@]}"
        echo
    fi
    echo "## Manifest lúc backup"
    echo '```'
    cat "$WORK/manifest.txt"
    echo '```'
} > "$REPORT"

cat "$REPORT"
rclone copyto "$REPORT" "vault:drills/$STAMP-$(date +%Y%m%dT%H%M).md"
log "Biên bản đã lưu ở vault:drills/"
[ "$result" = "ĐẠT" ]
