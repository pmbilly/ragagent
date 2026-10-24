#!/usr/bin/env bash
# 开发环境体检：逐项检查 clone → 起服的前置条件，失败时给出修复命令。
#
# 用法：bash scripts/doctor.sh
# 退出码：0 = 无阻断项（warning 不影响）；1 = 有阻断项。
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

fail=0
warn=0

ok()   { printf '  [ok]   %s\n' "$1"; }
bad()  { printf '  [fail] %s\n         → %s\n' "$1" "$2"; fail=$((fail + 1)); }
note() { printf '  [warn] %s\n         → %s\n' "$1" "$2"; warn=$((warn + 1)); }

env_value() {
    local key="$1" val=""
    if [ -f .env ]; then
        val=$(grep -m1 "^${key}=" .env 2>/dev/null | cut -d= -f2- | tr -d '\r' | sed 's/[[:space:]]*$//' || true)
    fi
    printf '%s' "${val}"
}

port_open() {
    nc -z 127.0.0.1 "$1" >/dev/null 2>&1
}

echo "== ragagent 开发环境体检 =="

# ── 工具链 ──────────────────────────────────────────────────────────────────
# 依次尝试：JAVA_HOME → Homebrew 21（默认不进 PATH）→ PATH 上的 java
java_bin=""
java_ver=""
for candidate in "${JAVA_HOME:+${JAVA_HOME}/bin/java}" \
        "/opt/homebrew/opt/openjdk@21/bin/java" \
        "$(command -v java 2>/dev/null || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] || continue
    candidate_ver="$("${candidate}" -version 2>&1 | head -1)"
    case "${candidate_ver}" in
        *'"21'*) java_bin="${candidate}"; java_ver="${candidate_ver}"; break ;;
        *) [ -z "${java_ver}" ] && java_ver="${candidate_ver}" ;;
    esac
done
if [ -n "${java_bin}" ]; then
    ok "JDK 21: ${java_ver}"
elif [ -n "${java_ver}" ]; then
    bad "JDK 版本不是 21: ${java_ver}" "安装 JDK 21 并让 JAVA_HOME 指向它（Maven 构建与运行都用）"
else
    bad "找不到 java" "安装 JDK 21（macOS: brew install openjdk@21），并设置 JAVA_HOME"
fi

if command -v node >/dev/null 2>&1; then
    node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
    if [ "${node_major}" -ge 22 ] 2>/dev/null; then
        ok "Node $(node -v)"
    else
        bad "Node 版本过低: $(node -v)" "升级到 Node 22+（前端 vite 7 要求）"
    fi
else
    bad "找不到 node" "安装 Node 22+"
fi

if ! command -v docker >/dev/null 2>&1; then
    bad "找不到 docker" "安装 Docker Desktop（或 colima）"
elif ! docker info >/dev/null 2>&1; then
    bad "Docker daemon 未运行" "启动 Docker Desktop 后重试"
else
    ok "Docker daemon 可用"
    if docker compose version >/dev/null 2>&1; then
        ok "docker compose 可用"
    else
        bad "找不到 docker compose" "升级 Docker Desktop（需要 compose v2）"
    fi
fi

# ── 配置 ────────────────────────────────────────────────────────────────────
if [ -f .env ]; then
    ok ".env 已存在"
else
    bad ".env 不存在" "cp .env.example .env"
fi

aes_key="$(env_value SYSTEM_AES_KEY)"
[ -n "${aes_key}" ] && ok "SYSTEM_AES_KEY 已设置" \
    || bad "SYSTEM_AES_KEY 为空" "在 .env 里设置 32 字节密钥（模型凭证加密依赖它）"

jwt_secret="$(env_value JWT_SECRET)"
[ -n "${jwt_secret}" ] && ok "JWT_SECRET 已设置" \
    || note "JWT_SECRET 为空" "应用会随机生成密钥，重启后全员掉登录；在 .env 里设固定值"

retrieve_driver="$(env_value RETRIEVE_DRIVER)"
[ -n "${retrieve_driver}" ] && ok "RETRIEVE_DRIVER=${retrieve_driver}" \
    || note "RETRIEVE_DRIVER 为空" "检索引擎关闭，问答拿不到知识库上下文；在 .env 里设 RETRIEVE_DRIVER=postgres"

# ── 基础设施 ────────────────────────────────────────────────────────────────
if port_open 15432; then
    ok "PostgreSQL 可达 (localhost:15432)"
else
    note "PostgreSQL 未就绪 (localhost:15432)" "docker compose up -d"
fi

if port_open 16379; then
    ok "Redis 可达 (localhost:16379)"
    redis_password="$(env_value REDIS_PASSWORD)"
    redis_container="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i redis | head -1 || true)"
    if [ -n "${redis_container}" ]; then
        if [ -n "${redis_password}" ]; then
            redis_out="$(docker exec "${redis_container}" redis-cli -a "${redis_password}" ping 2>&1 || true)"
        else
            redis_out="$(docker exec "${redis_container}" redis-cli ping 2>&1 || true)"
        fi
        case "${redis_out}" in
            *PONG*) ok "Redis 认证通过" ;;
            *NOAUTH*|*"invalid password"*)
                bad "Redis 认证失败" "本仓 dev Redis 无密码：注释掉 .env 的 REDIS_PASSWORD；或给 compose 的 redis 加 requirepass" ;;
            *) note "Redis 认证状态未知: ${redis_out}" "确认 REDIS_PASSWORD 与 Redis 服务端配置一致" ;;
        esac
    fi
else
    note "Redis 未就绪 (localhost:16379)" "docker compose up -d"
fi

if port_open 50051; then
    ok "文档解析服务可达 (localhost:50051)"
else
    note "文档解析服务未就绪 (localhost:50051)" "docker compose up -d（只有上传/解析文档时需要）"
fi

if lsof -ti :8083 >/dev/null 2>&1; then
    note "端口 8083 已被占用" "若是旧后端：bash scripts/java-server-down.sh --stop"
else
    ok "端口 8083 空闲"
fi

# ── 存储与前端 ──────────────────────────────────────────────────────────────
storage_dir="${LOCAL_STORAGE_BASE_DIR:-$(env_value LOCAL_STORAGE_BASE_DIR)}"
case "${storage_dir}" in
    "~/"*) storage_dir="${HOME}/${storage_dir#\~/}" ;;
esac
storage_dir="${storage_dir:-${HOME}/ragagent-data/files}"
if mkdir -p "${storage_dir}" 2>/dev/null && [ -w "${storage_dir}" ]; then
    ok "本地存储目录可写: ${storage_dir}"
else
    bad "本地存储目录不可写: ${storage_dir}" "换到有写权限的持久目录，并同步 .env 的 LOCAL_STORAGE_BASE_DIR"
fi

if [ -d frontend/node_modules ]; then
    ok "前端依赖已安装"
else
    note "前端依赖未安装" "cd frontend && npm ci"
fi

echo
if [ "${fail}" -gt 0 ]; then
    echo "体检未通过：${fail} 个阻断项，${warn} 个提醒。按上面的 → 逐项修复。"
    exit 1
fi
echo "体检通过：${warn} 个提醒（提醒不阻断启动）。"
