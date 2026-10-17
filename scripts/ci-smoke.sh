#!/usr/bin/env bash
# CI 启动冒烟：跑**真实打包 jar**（不是 MockMvc），验证：
#   1. /health 200（Spring 上下文、Flyway、数据源都真的起来了）；
#   2. 默认关闭 Swagger 时 /v3/api-docs 404；
#   3. 进程能被正常停掉。
#
# 依赖：backend job 的 PG（默认 localhost:15432 / WeKnora）；本地 dev DB 同端口即可复用。
# Redis 不强制：冒烟只验证 HTTP 启动面，能力报告关闭后不会因 Redis 不可达而失败。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${SMOKE_PORT:-18083}"
LOG="${SMOKE_LOG:-/tmp/ragagent-ci-smoke.log}"
JAR="$(find "$ROOT/boot/build/libs" -maxdepth 1 -name 'boot-*-SNAPSHOT.jar' ! -name '*-plain.jar' | head -1)"

if [[ -z "$JAR" ]]; then
    echo "✗ 未找到 boot jar，请先 ./gradlew :boot:bootJar" >&2
    exit 1
fi

if lsof -ti ":$PORT" >/dev/null 2>&1; then
    echo "✗ 端口 $PORT 已占用；请换 SMOKE_PORT 或先停旧进程" >&2
    exit 1
fi

STORAGE_DIR="$(mktemp -d)"
PID=""

cleanup() {
    if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
        kill "$PID" 2>/dev/null || true
        wait "$PID" 2>/dev/null || true
    fi
    rm -rf "$STORAGE_DIR"
}
trap cleanup EXIT

echo "==> smoke: $(basename "$JAR") on :$PORT"
(
    cd "$ROOT/boot"
    SERVER_PORT="$PORT" \
    DB_HOST="${DB_HOST:-localhost}" \
    DB_PORT="${DB_PORT:-15432}" \
    DB_NAME="${DB_NAME:-WeKnora}" \
    DB_USER="${DB_USER:-postgres}" \
    DB_PASSWORD="${DB_PASSWORD:-postgres123!@#}" \
    REDIS_HOST="${REDIS_HOST:-localhost}" \
    REDIS_PORT="${REDIS_PORT:-16379}" \
    JWT_SECRET="${JWT_SECRET:-ci-smoke-jwt-secret-0123456789abcdef}" \
    SYSTEM_AES_KEY="${SYSTEM_AES_KEY:-0123456789abcdef0123456789abcdef}" \
    LOCAL_STORAGE_BASE_DIR="$STORAGE_DIR" \
    RETRIEVE_DRIVER="${RETRIEVE_DRIVER:-}" \
    DOCREADER_ADDR="${DOCREADER_ADDR:-}" \
    CAPABILITY_REPORT_ENABLED=false \
    SPRINGDOC_ENABLED=false \
    java -jar "$JAR"
) >"$LOG" 2>&1 &
PID=$!

for _ in $(seq 1 90); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health" 2>/dev/null || true)"
    if [[ "$code" == "200" ]]; then
        break
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "✗ 进程提前退出；日志尾部：" >&2
        tail -80 "$LOG" >&2 || true
        exit 1
    fi
    sleep 1
done

health="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health" 2>/dev/null || true)"
api_docs="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/v3/api-docs" 2>/dev/null || true)"
scalar="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/scalar" 2>/dev/null || true)"

if [[ "$health" != "200" ]]; then
    echo "✗ /health 期望 200，实际 $health；日志尾部：" >&2
    tail -80 "$LOG" >&2 || true
    exit 1
fi
if [[ "$api_docs" != "404" ]]; then
    echo "✗ /v3/api-docs 默认应 404（API 文档关闭），实际 $api_docs" >&2
    exit 1
fi
if [[ "$scalar" != "404" ]]; then
    echo "✗ /scalar 默认应 404（Scalar 关闭），实际 $scalar" >&2
    exit 1
fi

echo "✓ smoke passed: /health=200, /v3/api-docs=404, /scalar=404（文档默认关闭）"
