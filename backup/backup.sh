#!/usr/bin/env bash
# Backup một lần: PostgreSQL (từng DB), MongoDB (cả replica set), file R2 → bucket backup đã mã hóa.
# Chạy hằng đêm từ run.sh; chạy tay: docker exec goatsports-backup backup.sh
set -Eeuo pipefail
source "$(dirname "$0")/lib.sh"

require_env POSTGRES_USER POSTGRES_PASSWORD MONGO_ROOT_USERNAME MONGO_ROOT_PASSWORD
configure_rclone

STAMP="$(date +%Y-%m-%dT%H%M)"
WORK="/tmp/backup-$STAMP"
PG_HOST="${BACKUP_PG_HOST:-postgres}"
MONGO_HOST="${BACKUP_MONGO_HOST:-mongodb-search:27017}"
export PGPASSWORD="$POSTGRES_PASSWORD"

trap 'alert "Backup GOAT Sports $STAMP thất bại ở dòng $LINENO: $BASH_COMMAND"; rm -rf "$WORK"' ERR
mkdir -p "$WORK"
started=$(date +%s)

# pg_dump và câu đếm dòng dùng chung một snapshot, nên số dòng trong manifest khớp đúng nội dung dump
# dù lúc đó vẫn có người ghi.
dump_postgres_db() {
    local db="$1" snapshot="" line
    coproc HOLDER { psql -h "$PG_HOST" -U "$POSTGRES_USER" -d "$db" -AtqX -v ON_ERROR_STOP=1; }
    echo "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY; SELECT pg_export_snapshot();" >&"${HOLDER[1]}"
    while read -r line <&"${HOLDER[0]}"; do
        if [[ "$line" =~ ^[0-9A-F]+-[0-9A-F]+-[0-9]+$ ]]; then snapshot="$line"; break; fi
    done
    [ -n "$snapshot" ] || { echo "Không lấy được snapshot của $db" >&2; return 1; }

    pg_dump -h "$PG_HOST" -U "$POSTGRES_USER" -d "$db" -Fc --snapshot="$snapshot" -f "$WORK/pg-$db.dump"
    psql -h "$PG_HOST" -U "$POSTGRES_USER" -d "$db" -AtqX -v ON_ERROR_STOP=1 <<SQL | only_counts > "$WORK/pg-$db.counts"
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET TRANSACTION SNAPSHOT '$snapshot';
$PG_COUNT_SQL
COMMIT;
SQL

    echo "COMMIT;" >&"${HOLDER[1]}"
    local holder_pid="$HOLDER_PID"
    eval "exec ${HOLDER[1]}>&-"
    wait "$holder_pid"
}

log "PostgreSQL: vai trò và quyền"
pg_dumpall -h "$PG_HOST" -U "$POSTGRES_USER" --globals-only > "$WORK/pg-globals.sql"
mapfile -t databases < <(psql -h "$PG_HOST" -U "$POSTGRES_USER" -d postgres -AtqX \
    -c "SELECT datname FROM pg_database WHERE NOT datistemplate AND datname <> 'postgres' ORDER BY 1")
for db in "${databases[@]}"; do
    log "PostgreSQL: $db"
    dump_postgres_db "$db"
done

log "MongoDB: cả replica set (--oplog để bản dump nhất quán tại một thời điểm)"
mongodump --host "$MONGO_HOST" --username "$MONGO_ROOT_USERNAME" --password "$MONGO_ROOT_PASSWORD" \
    --authenticationDatabase admin --oplog --gzip --archive="$WORK/mongo.archive.gz" 2> "$WORK/mongo-dump.log"
mongo_counts < "$WORK/mongo-dump.log" > "$WORK/mongo.counts"

cat > "$WORK/manifest.txt" <<EOF
stamp=$STAMP
created_at=$(date -Iseconds)
postgres_databases=${databases[*]}
postgres_tables=$(cat "$WORK"/pg-*.counts | wc -l)
mongo_collections=$(wc -l < "$WORK/mongo.counts")
EOF
(cd "$WORK" && sha256sum -- * > SHA256SUMS)

log "Đẩy lên vault:db/$STAMP"
rclone copy "$WORK" "vault:db/$STAMP"

if [ -n "${CLOUDFLARE_R2_BUCKET:-}" ] && [ -n "${RCLONE_CONFIG_SOURCE_TYPE:-}" ]; then
    # Bản sao file luôn mới nhất; file bị xóa hay ghi đè ở bucket chính được giữ lại theo ngày ở files/deleted.
    log "R2: đồng bộ file sang vault:files/current"
    rclone sync "source:$CLOUDFLARE_R2_BUCKET" "vault:files/current" --fast-list \
        --backup-dir "vault:files/deleted/$STAMP"
fi

# Giữ 7 ngày gần nhất và bản Chủ nhật trong 4 tuần; bản file bị xóa giữ 28 ngày.
log "Dọn bản cũ"
now=$(date +%s)
while read -r dir; do
    name="${dir%/}"
    day="${name:0:10}"
    stamp_time=$(date -d "$day" +%s 2>/dev/null) || continue
    age_days=$(( (now - stamp_time) / 86400 ))
    weekday=$(date -d "$day" +%u)
    if [ "$age_days" -ge 7 ] && { [ "$weekday" -ne 7 ] || [ "$age_days" -ge 28 ]; }; then
        log "Xóa vault:db/$name"
        rclone purge "vault:db/$name"
    fi
done < <(rclone lsf "vault:db" --dirs-only)
while read -r dir; do
    name="${dir%/}"
    stamp_time=$(date -d "${name:0:10}" +%s 2>/dev/null) || continue
    if [ $(( (now - stamp_time) / 86400 )) -ge 28 ]; then
        rclone purge "vault:files/deleted/$name"
    fi
done < <(rclone lsf "vault:files/deleted" --dirs-only 2>/dev/null || true)

elapsed=$(( $(date +%s) - started ))
size=$(du -sh "$WORK" | cut -f1)
echo "$STAMP ${elapsed}s $size" | rclone rcat "vault:status/last-success"
rm -rf "$WORK"
log "Backup $STAMP xong trong ${elapsed}s ($size trước khi mã hóa)"
