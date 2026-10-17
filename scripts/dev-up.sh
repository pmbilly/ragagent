#!/usr/bin/env bash
# 一条命令起 dev：基础设施 + 后端（可选连前端）。
#
# 用法：
#   bash scripts/dev-up.sh                  # 基础设施 + 后端
#   bash scripts/dev-up.sh --with-frontend  # 再起 vite（前台）
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

with_frontend=0
for arg in "$@"; do
    case "${arg}" in
        --with-frontend) with_frontend=1 ;;
        -h|--help)
            sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "未知参数: ${arg}（支持 --with-frontend）" >&2
            exit 2
            ;;
    esac
done

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "Docker daemon 不可用：先启动 Docker Desktop。" >&2
    exit 1
fi

if [ ! -f .env ]; then
    cp .env.example .env
    echo "==> 已生成 .env（dev 默认值；需要改端口/密钥时编辑它）"
fi

echo "==> 启动基础设施（PostgreSQL / Redis / docreader）"
if docker compose up -d --wait >/dev/null 2>&1; then
    echo "    基础设施就绪"
else
    docker compose up -d || true
    echo "    等待 15432 / 16379..."
    for _ in $(seq 1 30); do
        if nc -z 127.0.0.1 15432 >/dev/null 2>&1 \
                && nc -z 127.0.0.1 16379 >/dev/null 2>&1; then
            break
        fi
        sleep 2
    done
    if ! nc -z 127.0.0.1 15432 >/dev/null 2>&1 \
            || ! nc -z 127.0.0.1 16379 >/dev/null 2>&1; then
        echo "基础设施未就绪：若端口被旧容器占用，先停掉它们；否则看 docker compose 日志。" >&2
        exit 1
    fi
    echo "    端口已可达，复用当前基础设施"
fi

echo "==> 环境体检"
if ! bash scripts/doctor.sh; then
    echo "体检未通过：按上面的提示修复后重跑。" >&2
    exit 1
fi

echo "==> 启动后端"
if ! bash scripts/java-server-up.sh; then
    echo "后端启动失败：看 /tmp/ragagent-java-server.log；端口占用先跑 scripts/java-server-down.sh --stop" >&2
    exit 1
fi

port="$(grep -m1 '^SERVER_PORT=' .env 2>/dev/null | cut -d= -f2- | tr -d '\r' || true)"
port="${port:-8083}"
echo
echo "后端: http://localhost:${port}"
echo "健康: http://localhost:${port}/health"
echo "前端: cd frontend && npm run dev   # http://localhost:5173"

if [ "${with_frontend}" -eq 1 ]; then
    if [ ! -d frontend/node_modules ]; then
        echo "==> 安装前端依赖（首次）"
        (cd frontend && npm ci) || exit 1
    fi
    echo "==> 启动前端（Ctrl+C 结束）"
    cd frontend && exec npm run dev
fi
