FROM questdb/questdb:8.2.3

USER root

ENTRYPOINT ["/bin/sh", "-c", "mkdir -p /data/questdb && /app/bin/java -ea -Dnoebug -XX:+UseParallelGC -m io.questdb/io.questdb.ServerMain -d /data/questdb"]
