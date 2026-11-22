#!/usr/bin/env bash
# dev server「陈旧」检测（B186；X4 起改看 Maven 的 target/classes）。
#
# 背景（B184 记录、B186 再次实测）：`scripts/java-server-up.sh` 起的是运行副本
# `~/.ragagent/run/app.jar`，而 `mvn package` 会就地重写 `backend/*/target/classes`
# 与 `backend/app/target/app-*.jar` ⇒ 磁盘产物已更新、进程仍是旧代码，
# 表现为"改了没生效"，历史上另有两种症状：全端点点 500（`ClassNotFoundException`）、
# 某条"少走"的功能路径卡住（`NoClassDefFoundError`，前端工具卡片一直转圈）。
#
# 本脚本判断「正在跑的 dev server 是否比刚构建的产物更旧」；`mvn verify` 收尾时由
# backend/app 模块的 exec 调用，也可随时手动跑：bash scripts/dev-stale-check.sh
# **永远退出 0**（只提醒，不阻断构建）。
set -uo pipefail

cd "$(dirname "$0")/.." || exit 0

PORT="${JAVA_PORT:-}"
if [ -z "${PORT}" ] && [ -f .env ]; then
    PORT="$(grep -E '^SERVER_PORT=' .env | head -1 | cut -d= -f2 | tr -d '[:space:]')"
fi
PORT="${PORT:-8083}"

PID="$(lsof -nP -tiTCP:"${PORT}" -sTCP:LISTEN 2>/dev/null | head -1)"
if [ -z "${PID}" ]; then
    exit 0   # 没有在跑的 dev server，无需提醒
fi

# 进程启动时刻（BSD/GNU ps 两种输出的日期前缀都吃掉，再交给 date 解析）
START_RAW="$(ps -o lstart= -p "${PID}" 2>/dev/null | sed 's/^[A-Za-z]* //')"
START_EPOCH="$(date -j -f "%b %d %T %Y" "${START_RAW}" +%s 2>/dev/null \
    || date -d "${START_RAW}" +%s 2>/dev/null)"
[ -z "${START_EPOCH}" ] && exit 0

# 最新产物时刻：五模块 target/classes 与组合根 jar，取 mtime 最新者
NEWEST_ARTIFACT="$(ls -dt backend/framework/target/classes \
    backend/model-runtime/target/classes backend/retrieval-engine/target/classes \
    backend/agent-engine/target/classes backend/app/target/classes \
    backend/app/target/app-*.jar 2>/dev/null | head -1)"
[ -z "${NEWEST_ARTIFACT}" ] && exit 0
ARTIFACT_EPOCH="$(stat -f %m "${NEWEST_ARTIFACT}" 2>/dev/null \
    || stat -c %Y "${NEWEST_ARTIFACT}" 2>/dev/null)"
[ -z "${ARTIFACT_EPOCH}" ] && exit 0

if [ "${ARTIFACT_EPOCH}" -gt "${START_EPOCH}" ]; then
    echo ""
    echo "⚠️  dev server 已陈旧（B184/B186）：它在 ${PORT} 上启动于 $(date -r "${START_EPOCH}" '+%H:%M:%S')，"
    echo "    而构建产物已更新到 $(date -r "${ARTIFACT_EPOCH}" '+%H:%M:%S')（${NEWEST_ARTIFACT}）。"
    echo "    该进程跑的是启动时的 jar 快照 ⇒ 刚构建的改动不会生效。"
    echo "    处置：bash scripts/java-server-up.sh   ← 重启即恢复（与业务代码无关）"
    echo ""
fi
exit 0
