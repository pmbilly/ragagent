#!/usr/bin/env bash
# 启动 Java 版（Spring Boot，host-run），用于 e2e 验证。
#
# 用法：
#   scripts/java-server-up.sh
#   SSRF_WHITELIST_EXTRA=mcp.example.com scripts/java-server-up.sh
#
# ⚠️ **改完代码必须重启本脚本起的服务**（2026-10-10 实测，B184）：`:boot:bootRun` 的类路径里是
# **模块 jar**（`domains-0.0.1-SNAPSHOT.jar` 等，栈里显示为 `~[domains-…jar:na]`），而构建会
# **就地重写**这些 jar ⇒ 运行中的 JVM 已加载的类走旧视图、**惰性加载的类直接加载不到** ⇒
# 症状：**全端点 500** + 日志 `unhandled exception: … ClassNotFoundException /
# NoClassDefFoundError / TypeNotPresentException`（全是本仓自己的类）。与业务代码无关，
# 停掉重启即可；在服务运行中跑 `gradlew build` / `--rerun` 会稳定触发。
#
# 与 Go 版**共用同一个 dev 数据库**——这正是 e2e 的价值：H2 测不出的差异
# （NOT NULL 零值、jsonb 键序、DDL 默认值）只会在真 PG 上暴露。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-env.sh
source "${SCRIPT_DIR}/dev-env.sh"

LOG="${JAVA_LOG:-/tmp/ragagent-java-server.log}"

echo "==> starting Java server on :${JAVA_PORT}"
echo "    DB       = ${DB_HOST}:${DB_PORT}"
echo "    Redis    = ${REDIS_HOST}:${REDIS_PORT}"
echo "    AES key  = $([ -n "${SYSTEM_AES_KEY}" ] && echo "set (${#SYSTEM_AES_KEY} chars) — 与 Go 一致，加密可互操作" || echo UNSET)"
echo "    SSRF extra whitelist = ${SSRF_WHITELIST_EXTRA:-<none>}"
echo "    log      = ${LOG}"

cd "${RAGAGENT_ROOT}"
SERVER_PORT="${JAVA_PORT}" \
JWT_SECRET="${JWT_SECRET}" \
SYSTEM_AES_KEY="${SYSTEM_AES_KEY}" \
LOCAL_STORAGE_BASE_DIR="${LOCAL_STORAGE_BASE_DIR}" \
./gradlew :boot:bootRun --console=plain > "${LOG}" 2>&1 &   # 模块拆分后组合根在 :boot（B165）

echo "==> waiting for readiness（Spring 启动 + Flyway 校验，约 20-40 秒）"
wait_for_port "${JAVA_PORT}" /api/v1/knowledge-bases 40
echo "==> Java server ready. token: TOKEN=\$(scripts/token.sh ${JAVA_PORT})"
