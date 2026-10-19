#!/usr/bin/env bash
# java-server-up.sh / go-server-up.sh / token.sh 共用的 dev 环境。
#
# 约定：
# 1. 只按 key 读取本仓 .env（`env_value`），不整份 source——避免把容器内地址
#    或其它 shell 变量带进来。
# 2. host-run 一律用 localhost + compose 映射端口（15432 / 16379 / 50051）。
# 3. SYSTEM_AES_KEY 更换后旧密文不可解；跨语言 e2e 两侧必须用同一把 key。
# 4. 访问被 SSRF 白名单拦住的 MCP/LLM 域名时设 SSRF_WHITELIST_EXTRA
#    （逗号分隔，支持 *.example.com 通配）。

set -euo pipefail

# JDK 21：Gradle 与 bootRun 都要求 JAVA_HOME/PATH 指向 21。
# 踩过的坑：脚本里若依赖调用者已配好 PATH，换一个 shell 就会得到
# "Unable to locate a Java Runtime"（Homebrew 的 openjdk 不在默认 PATH）。
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21 ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

RAGAGENT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 只读本仓 .env；缺失时各 key 为空，由 scripts/doctor.sh 提示。
ENV_FILE="${RAGAGENT_ROOT}/.env"

# dev 环境的宿主机端口（docker-compose 映射）
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-15432}"
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-16379}"
export DOCREADER_ADDR="${DOCREADER_ADDR:-localhost:50051}"
export JAVA_PORT="${JAVA_PORT:-}"   # 留空 ⇒ 末尾统一解析（显式 env > .env 的 SERVER_PORT > 8083）
export GO_PORT="${GO_PORT:-8080}"

# 从 .env 取单个 key（不要 source 整个文件：容器内地址会覆盖 localhost）
env_value() {
  local key="$1" val=""
  if [ -f "${ENV_FILE}" ]; then
    val=$(grep -m1 "^${key}=" "${ENV_FILE}" | cut -d= -f2- | tr -d '\r' || true)
  fi
  echo "$val"
}

# 与 Go 侧一致的加密密钥——跨语言 e2e 的前提（见坑 2）
export SYSTEM_AES_KEY="${SYSTEM_AES_KEY:-$(env_value SYSTEM_AES_KEY)}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-$(env_value REDIS_PASSWORD)}"

# Go 二进制自己不读 .env（godotenv 只在容器入口用），host-run 必须显式导出。
# 这些值在 .env 里与 dev PG 一致（同一个库，只是容器/宿主地址不同），直接取用。
export DB_DRIVER="${DB_DRIVER:-$(env_value DB_DRIVER)}"
export DB_USER="${DB_USER:-$(env_value DB_USER)}"
export DB_PASSWORD="${DB_PASSWORD:-$(env_value DB_PASSWORD)}"
export DB_NAME="${DB_NAME:-$(env_value DB_NAME)}"
# 检索引擎：未配置 = 检索关闭（问答拿不到知识库上下文，且不会报错）。
export RETRIEVE_DRIVER="${RETRIEVE_DRIVER:-$(env_value RETRIEVE_DRIVER)}"

# 入库图片 OCR（本地服务）：留空 = 关闭，行为与未接入时一致。
export OCR_ENDPOINT="${OCR_ENDPOINT:-$(env_value OCR_ENDPOINT)}"
export OCR_MAX_PAGES="${OCR_MAX_PAGES:-$(env_value OCR_MAX_PAGES)}"

# Java 侧自有配置
export JWT_SECRET="${JWT_SECRET:-$(env_value JWT_SECRET)}"
export JWT_SECRET="${JWT_SECRET:-java-e2e-jwt-secret-key-0123456789abcdef}"
# 严禁 /tmp：macOS 定期清理会丢已入库文档的原始文件。
export LOCAL_STORAGE_BASE_DIR="${LOCAL_STORAGE_BASE_DIR:-$(env_value LOCAL_STORAGE_BASE_DIR)}"
export LOCAL_STORAGE_BASE_DIR="${LOCAL_STORAGE_BASE_DIR:-${HOME}/ragagent-data/files}"

# Langfuse 可观测（B228 自建栈起）。**不注入 = 静默 disabled**：启动日志只有一行
# "[Langfuse] disabled (no LANGFUSE_PUBLIC_KEY/SECRET_KEY)"，之后所有链路都无记录
# ⇒ 走查/调试时表现为"明明提了问，Langfuse 里什么都没有"（2026-10-12 实测踩到）。
# 按 key 取（沿用坑 1：不整份 source .env，避免覆盖 localhost 地址）。
export LANGFUSE_HOST="${LANGFUSE_HOST:-$(env_value LANGFUSE_HOST)}"
export LANGFUSE_PUBLIC_KEY="${LANGFUSE_PUBLIC_KEY:-$(env_value LANGFUSE_PUBLIC_KEY)}"
export LANGFUSE_SECRET_KEY="${LANGFUSE_SECRET_KEY:-$(env_value LANGFUSE_SECRET_KEY)}"
export LANGFUSE_ENVIRONMENT="${LANGFUSE_ENVIRONMENT:-$(env_value LANGFUSE_ENVIRONMENT)}"
export LANGFUSE_DEBUG="${LANGFUSE_DEBUG:-$(env_value LANGFUSE_DEBUG)}"   # true 时打导出成功日志

# 注：指令型技能自 B57 起**入库**（skills 表，平台级），宿主技能目录
# （weknora.skills.host-dirs / WEKNORA_SKILLS_HOST_DIRS）已退役，不再注入。


# 测试账号（阶段 1 建的专用租户 10002）
export TEST_EMAIL="${TEST_EMAIL:-java-phase1@weknora.test}"
export TEST_VIEWER_EMAIL="${TEST_VIEWER_EMAIL:-java-phase1-viewer@weknora.test}"
export TEST_PASSWORD="${TEST_PASSWORD:-Passw0rd!}"

# 登录并回显 token（供录制脚本使用）
login() {
  local email="${1:-${TEST_EMAIL}}"
  curl -s -X POST "http://localhost:${2:-8083}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${email}\",\"password\":\"${TEST_PASSWORD}\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])'
}

# 等待服务就绪（轮询到非 000 即认为起来了）
wait_for_port() {
  local port="$1" path="${2:-/api/v1/knowledge-bases}" tries="${3:-40}"
  for _ in $(seq 1 "${tries}"); do
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}${path}" || true)
    if [ "${code}" != "000" ]; then
      echo "ready (HTTP ${code}) on :${port}"
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT waiting for :${port}" >&2
  return 1
}

# ── 端口优先级：显式 env > .env 的 SERVER_PORT > 8083 ──────────────────────
# 放在文件末尾 ⇒ 必定生效（最后一次赋值胜出）。
_java_port_from_env="$(env_value SERVER_PORT || true)"
export JAVA_PORT="${JAVA_PORT:-${_java_port_from_env:-8083}}"
unset _java_port_from_env
