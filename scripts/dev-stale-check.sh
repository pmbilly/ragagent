#!/usr/bin/env bash
# dev server「陈旧」检测（B186）。
#
# 背景（B184 记录、B186 再次实测）：本仓 dev server（`scripts/java-server-up.sh` 起的
# `:boot:bootRun`）类路径里是**模块 jar**（`domains-0.0.1-SNAPSHOT.jar` 等），而构建会
# **就地重写**这些 jar ⇒ 运行中的 JVM：已加载的类走旧视图、**惰性加载的类直接加载不到**
# ⇒ 两种症状：
#   · 全端点点 500（日志 `unhandled exception … ClassNotFoundException`）—— 2026-10-10 首见；
#   · 某条"少走"的功能路径卡住/中断（日志 `NoClassDefFoundError: …/tools/wiki/WikiTexts`，
#     前端表现为工具卡片**一直转圈**）—— 2026-10-10 二次实测（wiki 搜索）。
#
# 本脚本判断「正在跑的 dev server 是否比刚构建的产物更旧」；根 build 的 build 任务收尾时自动调用，
# 也可随时手动跑：bash scripts/dev-stale-check.sh
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

# 最新模块产物时刻
NEWEST_JAR="$(ls -t domains/build/libs/*.jar engine/build/libs/*.jar \
    common/build/libs/*.jar boot/build/libs/*.jar 2>/dev/null | head -1)"
[ -z "${NEWEST_JAR}" ] && exit 0
JAR_EPOCH="$(stat -f %m "${NEWEST_JAR}" 2>/dev/null || stat -c %Y "${NEWEST_JAR}" 2>/dev/null)"
[ -z "${JAR_EPOCH}" ] && exit 0

if [ "${JAR_EPOCH}" -gt "${START_EPOCH}" ]; then
    echo ""
    echo "⚠️  dev server 已陈旧（B184/B186）：它在 ${PORT} 上启动于 $(date -r "${START_EPOCH}" '+%H:%M:%S')，"
    echo "    而构建产物已更新到 $(date -r "${JAR_EPOCH}" '+%H:%M:%S')（${NEWEST_JAR#*/}）。"
    echo "    该 JVM 现在处于「惰性类加载会 NoClassDefFoundError」状态 ⇒ 可能表现为"
    echo "    全端点 500，或某条功能路径卡住（如 wiki 搜索的卡片一直转圈）。"
    echo "    处置：bash scripts/java-server-up.sh   ← 重启即恢复（与业务代码无关）"
    echo ""
fi
exit 0
