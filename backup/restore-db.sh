#!/usr/bin/env bash
# Khôi phục một hoặc vài DB vào PRODUCTION từ một bản backup, ghi đè dữ liệu hiện tại của các DB đó.
#   restore-db.sh <stamp|latest> <đích>... --yes
#   đích: tên DB PostgreSQL (sports_auth, keycloak, ...) hoặc mongo:<db> (mongo:sports_social)
#   vd:   restore-db.sh 2026-10-23T0200 sports_auth keycloak --yes
# Trước khi ghi đè, trạng thái hiện tại của từng đích được dump lên vault:pre-restore/<lúc chạy>/ để còn quay lại.
# Dừng các service dùng những DB này trước khi chạy.
set -Eeuo pipefail
source "$(dirname "$0")/lib.sh"

confirmed=0
args=()
for arg in "$@"; do
    if [ "$arg" = "--yes" ]; then confirmed=1; else args+=("$arg"); fi
done
if [ "${#args[@]}" -lt 2 ]; then
    echo "Cách dùng: restore-db.sh <stamp|latest> <db|mongo:db>... --yes" >&2
    exit 2
fi

require_env POSTGRES_USER POSTGRES_PASSWORD
configure_rclone
export PGPASSWORD="$POSTGRES_PASSWORD"
PG_HOST="${BACKUP_PG_HOST:-postgres}"
MONGO_HOST="${BACKUP_MONGO_HOST:-mongodb-search:27017}"

STAMP="${args[0]}"
targets=("${args[@]:1}")
if [ "$STAMP" = "latest" ]; then
    STAMP="$(rclone lsf "vault:db" --dirs-only | sort | tail -n 1)"
    STAMP="${STAMP%/}"
fi

echo "Sẽ GHI ĐÈ dữ liệu production của: ${targets[*]}"
echo "bằng bản backup: $STAMP"
if [ "$confirmed" -ne 1 ]; then
    echo "Chưa làm gì. Kiểm tra lại rồi chạy kèm --yes." >&2
    exit 2
fi

NOW="$(date +%Y-%m-%dT%H%M%S)"
WORK="/tmp/restore-db-$NOW"
mkdir -p "$WORK/backup" "$WORK/before"
trap 'rm -rf "$WORK"' EXIT

includes=(--include SHA256SUMS)
for target in "${targets[@]}"; do
    case "$target" in
        mongo:*) includes+=(--include mongo.archive.gz --include mongo.counts) ;;
        *) includes+=(--include "pg-$target.dump" --include "pg-$target.counts") ;;
    esac
done
log "Tải các file cần từ vault:db/$STAMP"
rclone copy "vault:db/$STAMP" "$WORK/backup" "${includes[@]}"
(cd "$WORK/backup" && sha256sum -c --quiet --ignore-missing SHA256SUMS)
for target in "${targets[@]}"; do
    case "$target" in
        mongo:*) file="mongo.archive.gz" ;;
        *) file="pg-$target.dump" ;;
    esac
    [ -f "$WORK/backup/$file" ] || { echo "Bản $STAMP không có $file" >&2; exit 1; }
done

mongo_auth=(--username "${MONGO_ROOT_USERNAME:-}" --password "${MONGO_ROOT_PASSWORD:-}" --authenticationDatabase admin)
log "Lưu trạng thái hiện tại lên vault:pre-restore/$NOW"
for target in "${targets[@]}"; do
    case "$target" in
        mongo:*)
            db="${target#mongo:}"
            mongodump --host "$MONGO_HOST" "${mongo_auth[@]}" --db "$db" --gzip \
                --archive="$WORK/before/mongo-$db.archive.gz" 2>/dev/null ;;
        *)
            pg_dump -h "$PG_HOST" -U "$POSTGRES_USER" -d "$target" -Fc -f "$WORK/before/pg-$target.dump" ;;
    esac
done
rclone copy "$WORK/before" "vault:pre-restore/$NOW"

problems=0
for target in "${targets[@]}"; do
    case "$target" in
        mongo:*)
            db="${target#mongo:}"
            log "MongoDB: $db"
            mongorestore --host "$MONGO_HOST" "${mongo_auth[@]}" --gzip --archive="$WORK/backup/mongo.archive.gz" \
                --nsInclude "$db.*" --drop 2> "$WORK/mongo-$db.log"
            mongo_counts < "$WORK/mongo-$db.log" > "$WORK/mongo-$db.restored"
            if ! diff -u <(grep "^$db\." "$WORK/backup/mongo.counts" || true) "$WORK/mongo-$db.restored"; then
                problems=1
            fi ;;
        *)
            log "PostgreSQL: $target"
            # Một transaction: lỗi giữa chừng thì DB vẫn y như trước, không bị khôi phục dở.
            pg_restore -h "$PG_HOST" -U "$POSTGRES_USER" -d "$target" --clean --if-exists --single-transaction \
                --exit-on-error "$WORK/backup/pg-$target.dump"
            psql -h "$PG_HOST" -U "$POSTGRES_USER" -d "$target" -AtqX -c "$PG_COUNT_SQL" | only_counts \
                > "$WORK/pg-$target.restored"
            if ! diff -u "$WORK/backup/pg-$target.counts" "$WORK/pg-$target.restored"; then
                problems=1
            fi ;;
    esac
done

if [ "$problems" -ne 0 ]; then
    alert "Khôi phục ${targets[*]} từ $STAMP xong nhưng số dòng / document lệch (xem diff ở trên). Bản trước khi khôi phục: vault:pre-restore/$NOW"
    exit 1
fi
log "Đã khôi phục ${targets[*]} về bản $STAMP. Bản trước khi khôi phục: vault:pre-restore/$NOW"
