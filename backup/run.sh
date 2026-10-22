#!/usr/bin/env bash
# Container backup: chạy backup.sh mỗi ngày lúc 02:00 (giờ TZ của container). Lỗi đã được backup.sh báo qua alert,
# vòng lặp vẫn chạy tiếp để đêm sau thử lại.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

while true; do
    next=$(date -d "today 02:00" +%s)
    [ "$next" -gt "$(date +%s)" ] || next=$(date -d "tomorrow 02:00" +%s)
    log "Lần backup kế tiếp: $(date -d "@$next" '+%Y-%m-%d %H:%M')"
    sleep $(( next - $(date +%s) ))
    backup.sh || true
done
