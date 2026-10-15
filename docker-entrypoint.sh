#!/bin/sh
set -e
mkdir -p /app/uploads
chown -R dibidani:dibidani /app/uploads 2>/dev/null || chmod -R a+rwX /app/uploads || true
exec su-exec dibidani java $JAVA_OPTS -jar /app/app.jar
