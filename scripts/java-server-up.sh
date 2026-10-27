#!/usr/bin/env bash
# 启动 Java 版（Spring Boot，host-run），用于 e2e 验证。
#
# 用法：
#   scripts/java-server-up.sh
#   SSRF_WHITELIST_EXTRA=mcp.example.com scripts/java-server-up.sh
#
# ⚠️ **改完代码必须重启本脚本起的服务**（2026-10-10 实测，B184）：这里起的是
# `boot/target/*.jar` 的**一次性快照**，重新构建只会替换磁盘上的 jar，进程仍跑旧代码
# （表现为"改了没生效"，与业务代码无关）。停掉重启即可；
# 在服务运行中跑 `mvn package` / `mvn verify` 后记得重启。
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

# 起服前挡一道（B216，2026-10-10 实测踩到）：端口被**旧进程**占用时，新 bootRun 会启动失败
# 退出，而下面的 wait_for_port 会**打到旧进程**上 ⇒ 脚本照样打印 "ready" ✗。
# 后果比"起不来"更坏：旧进程正处在 §上文的陈旧 jar 状态（全端点 500），于是
# `curl` 拿到的 500 会被误读成"刚改的代码炸了"——B215 实测把一次环境问题误判成代码 bug。
if lsof -ti :"${JAVA_PORT}" >/dev/null 2>&1; then
    echo "✗ 端口 ${JAVA_PORT} 已被占用 —— 旧服务仍在跑，本次启动会被它挡住："
    lsof -ti :"${JAVA_PORT}" | sed 's/^/      pid /'
    echo "  先停再起：bash scripts/java-server-down.sh --stop"
    echo "  （或强制：lsof -ti :${JAVA_PORT} | xargs kill -9；README「高频坑」同款记录）"
    exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
    echo "✗ 未找到 JDK：请先 export JAVA_HOME（如 /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home）"
    exit 1
fi
JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

# 先构建再起 jar：Maven 的 spring-boot:run 在多模块 reactor 里会对每个 -am 模块执行该 goal，
# 非组合根模块没有主类 ⇒ 直接用 package + java -jar。工作目录是仓库根，
# application.yml 的相对路径（build/generated-migrations）口径不变。
mvn -B -q -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -pl boot -am package >> "${LOG}" 2>&1 \
    || { echo "✗ 构建失败，见 ${LOG}"; exit 1; }

# 运行副本：任何一次 mvn package/install 都会覆盖 boot/target/*.jar，
# 运行中的进程若直接指向它，懒加载类会 ClassNotFoundException（本地踩过 3 次）。
# 放在用户主目录：不进仓库、mvn clean 也碰不到（本地已踩过 target/ 下的副本被 clean 删掉）
RUN_DIR="${HOME}/.ragagent/run"
mkdir -p "${RUN_DIR}"
cp -f boot/target/boot-0.0.1-SNAPSHOT.jar "${RUN_DIR}/boot.jar"

SERVER_PORT="${JAVA_PORT}" \
JWT_SECRET="${JWT_SECRET}" \
SYSTEM_AES_KEY="${SYSTEM_AES_KEY}" \
LOCAL_STORAGE_BASE_DIR="${LOCAL_STORAGE_BASE_DIR}" \
"${JAVA_BIN}" -jar "${RUN_DIR}/boot.jar" >> "${LOG}" 2>&1 &

echo "==> waiting for readiness（Spring 启动 + Flyway 校验，约 20-40 秒）"
wait_for_port "${JAVA_PORT}" /api/v1/knowledge-bases 40
echo "==> Java server ready. token: TOKEN=\$(scripts/token.sh ${JAVA_PORT})"
