# pg_dump phải cùng hoặc mới hơn bản PostgreSQL production (postgres:16).
FROM postgres:16-bookworm

ARG MONGO_TOOLS_VERSION=100.10.0
ARG RCLONE_VERSION=1.71.1
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && curl -fsSL -o /tmp/mongodb-database-tools.deb \
    "https://fastdl.mongodb.org/tools/db/mongodb-database-tools-debian12-x86_64-${MONGO_TOOLS_VERSION}.deb" \
    && curl -fsSL -o /tmp/rclone.deb \
    "https://downloads.rclone.org/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-amd64.deb" \
    && apt-get install -y --no-install-recommends /tmp/mongodb-database-tools.deb /tmp/rclone.deb \
    && rm -rf /var/lib/apt/lists/* /tmp/mongodb-database-tools.deb /tmp/rclone.deb

COPY lib.sh backup.sh restore.sh restore-db.sh run.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/restore-db.sh /usr/local/bin/run.sh

ENTRYPOINT ["run.sh"]