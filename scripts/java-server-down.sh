#!/usr/bin/env bash
# 停掉 java-server-up.sh 起的后端。
#
# 用法：
#   scripts/java-server-down.sh            # 或 --stop（参数被忽略，兼容文档里的旧写法）
#
# 为什么需要它（2026-10-10 B216 实测）：此前**只有 up 没有 down**，而 HANDOFF §8 与 README
# 都写着 `scripts/*-down.sh --stop` ⇒ 照文档停服会静默失败（`bash: no such file`），
# 端口 8083 一直被旧进程占着。后果是下次 `java-server-up.sh` 起不来、而**就绪探测打到旧进程**上
# 照样打印 "ready" ✗ —— 于是旧进程那份"陈旧 jar"的全端点 500 会被误读成"刚改的代码炸了"。
# up 脚本现已自挡端口占用（B216）；本脚本补齐停服这一步。
#
# 实现取"按端口找进程树"：监听该端口的 JVM 就是要停的那个，
# 与它是 `java -jar` 还是 `mvn spring-boot:run` 起的无关。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-env.sh
source "${SCRIPT_DIR}/dev-env.sh"

pids_on_port() {
    lsof -ti :"${JAVA_PORT}" 2>/dev/null || true
}

PIDS="$(pids_on_port)"
if [ -z "${PIDS}" ]; then
    echo "==> 端口 ${JAVA_PORT} 空闲，无需停服"
    exit 0
fi

echo "==> 停止 :${JAVA_PORT} 上的进程：$(echo "${PIDS}" | tr '\n' ' ')"
echo "${PIDS}" | xargs kill 2>/dev/null || true

for _ in $(seq 1 15); do
    sleep 1
    if [ -z "$(pids_on_port)" ]; then
        echo "==> 已优雅停止"
        exit 0
    fi
done

echo "==> 优雅停止超时，改为强制 kill"
pids_on_port | xargs kill -9 2>/dev/null || true
sleep 1
if [ -z "$(pids_on_port)" ]; then
    echo "==> 已强制停止"
else
    echo "✗ 端口 ${JAVA_PORT} 仍被占用：$(pids_on_port | tr '\n' ' ')"
    exit 1
fi
