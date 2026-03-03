#!/bin/bash
# =============================================================
# FIX Engine — Node Start Script
# Deploy to: /opt/fix-engine/bin/start.sh
# =============================================================

set -euo pipefail

APP_HOME="/opt/fix-engine"
JAR="$APP_HOME/lib/fix-engine-2.1.0.jar"
PID_FILE="$APP_HOME/fix-engine.pid"
LOG_FILE="$APP_HOME/logs/fix-engine.log"

NODE_ID="${NODE_ID:-$(hostname)}"
NODE_HOST="${NODE_HOST:-$(hostname -I | awk '{print $1}')}"
SERVER_PORT="${SERVER_PORT:-8080}"

DB_HOST="${DB_HOST:-10.0.1.100}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-fixengine}"
DB_USER="${DB_USER:-fixuser}"
DB_PASS="${DB_PASS:-CHANGE_ME}"

ZK_CONNECT="${ZK_CONNECT:-10.0.1.10:2181,10.0.1.11:2181,10.0.1.12:2181}"
HZ_NODE1="${HZ_NODE1:-10.0.1.10}"
HZ_NODE2="${HZ_NODE2:-10.0.1.11}"
HZ_NODE3="${HZ_NODE3:-10.0.1.12}"

JWT_SECRET="${JWT_SECRET:-CHANGE_THIS_TO_A_256BIT_SECRET_KEY_IN_PROD}"

mkdir -p "$APP_HOME/logs" "$APP_HOME/data/fix-store" "$APP_HOME/data/fix-messages"

JAVA_OPTS=(
  "-Xmx4g"
  "-Xms2g"
  "-XX:+UseG1GC"
  "-XX:MaxGCPauseMillis=200"
  "-XX:+HeapDumpOnOutOfMemoryError"
  "-XX:HeapDumpPath=$APP_HOME/logs/heap-dump.hprof"
  "-Dfile.encoding=UTF-8"
  "-Dnode.id=$NODE_ID"
  "-DNODE_ID=$NODE_ID"
  "-DNODE_HOST=$NODE_HOST"
  "-DSERVER_PORT=$SERVER_PORT"
  "-DDB_HOST=$DB_HOST"
  "-DDB_PORT=$DB_PORT"
  "-DDB_NAME=$DB_NAME"
  "-DDB_USER=$DB_USER"
  "-DDB_PASS=$DB_PASS"
  "-DZK_CONNECT=$ZK_CONNECT"
  "-DHZ_NODE1=$HZ_NODE1"
  "-DHZ_NODE2=$HZ_NODE2"
  "-DHZ_NODE3=$HZ_NODE3"
  "-DJWT_SECRET=$JWT_SECRET"
  "-Dspring.config.location=$APP_HOME/conf/application.yaml"
  "-Dlogging.config=$APP_HOME/conf/logback-spring.xml"
)

echo "======================================================"
echo "  Starting FIX Engine"
echo "  Node ID  : $NODE_ID"
echo "  Host     : $NODE_HOST:$SERVER_PORT"
echo "  ZooKeeper: $ZK_CONNECT"
echo "  Database : $DB_HOST:$DB_PORT/$DB_NAME"
echo "======================================================"

nohup java "${JAVA_OPTS[@]}" -jar "$JAR" \
  >> "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > "$PID_FILE"
echo "FIX Engine started. PID: $PID | Log: $LOG_FILE"
