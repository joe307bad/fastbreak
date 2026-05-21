#!/bin/sh
# Create QuestDB data directory on the volume if it doesn't exist
mkdir -p /data/questdb

# Symlink QuestDB's default data dir to the volume
# Remove existing dir/symlink if it exists
rm -rf /var/lib/questdb
ln -s /data/questdb /var/lib/questdb

# Start QuestDB using the original entrypoint
exec /app/bin/java -ea -Dnoebug -XX:+UseParallelGC \
    -XX:ErrorFile=/data/questdb/hs_err_pid+%p.log \
    -m io.questdb/io.questdb.ServerMain \
    -d /data/questdb
