# ragagent 服务端镜像（组合根模块 = :boot）。
# 构建：docker build -t ragagent-server .
# 运行：环境变量见 .env.example（DB_*/REDIS_*/SYSTEM_AES_KEY/DOCREADER_ADDR）。
#
# 多阶段：
#   1. gradle+JDK21 构建 bootJar（含 proto 生成、migrations 同步）
#   2. JRE 21 运行时
# 前端是独立的 nginx 容器（frontend/Dockerfile），与本镜像仅通过 HTTP/SSE 通信。
#
# ⚠️ 本文件曾经失效且从未被验证过（长期只存在于仓库、不进 CI）：模块名沿用已裁撤的
# `server`、漏拷三个模块、proto 源目录未拷 ⇒ `docker build` 必然失败。**镜像构建现已进入
# CI 的 `docker` job**（.github/workflows/ci.yml），路径腐化会被当场逮住，别再让它悄悄烂掉。

FROM gradle:8.14-jdk21 AS builder
WORKDIR /build

# 先拷贝构建脚本与依赖清单，最大化层缓存
COPY settings.gradle.kts build.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
# proto 源目录是生成代码的唯一来源（:common 的 OTLP、:domains 的 docreader）——漏拷即编译失败
COPY otlp-proto ./otlp-proto
COPY docreader ./docreader
COPY migrations ./migrations
# 七个 Gradle 模块：先拷贝各自构建脚本（源码变动不使这一层失效），再拷贝源码
COPY contract/build.gradle.kts ./contract/
COPY platform/build.gradle.kts ./platform/
COPY model-runtime/build.gradle.kts ./model-runtime/
COPY retrieval-engine/build.gradle.kts ./retrieval-engine/
COPY engine/build.gradle.kts ./engine/
COPY domains/build.gradle.kts ./domains/
COPY boot/build.gradle.kts ./boot/
COPY contract ./contract
COPY platform ./platform
COPY model-runtime ./model-runtime
COPY retrieval-engine ./retrieval-engine
COPY engine ./engine
COPY domains ./domains
COPY boot ./boot

# 产物名随 Gradle 默认规则（boot-0.0.1-SNAPSHOT.jar），用 glob 排除 -plain.jar；
# cache mount 让重复构建复用依赖与 wrapper 发行版（BuildKit）。
# -x test：镜像构建不跑测试（闸门在 CI 的 backend job）。
# spotless ratchet 自 seed tag 需 .git 上下文，而 .dockerignore 排除了 .git ⇒ 显式关掉
# （bootJar 不依赖 spotless；格式检查归 spotlessCheck）。
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :boot:bootJar --no-daemon -x test -x spotlessCheck -x spotlessJava \
    && cp "$(find boot/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -1)" /app.jar \
    && test -s /app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

# 健康检查要发 HTTP（探 TCP 端口在 Spring 上下文起来之前就会通过，等于没检）。
# 镜像基于 Ubuntu，装 curl 是最小代价；/health 无需认证（common/web/HealthController）。
RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行（-m 建 home，避免依赖 HOME 的子进程异常）
RUN useradd -m -s /bin/bash --uid 1001 ragagent

COPY --from=builder /app.jar app.jar

# Flyway 迁移：application.yml 声明 `filesystem:./build/generated-migrations`，那是**相对工作目录**
# 的路径 ⇒ 镜像里必须落到 /app/build/generated-migrations。原 Dockerfile 没拷 ⇒ 全新容器启动即无 schema
# （Flyway 静默落到空的 classpath 位置，不报错、直到第一条查询才炸）。
COPY --from=builder /build/migrations/versioned ./build/generated-migrations/

USER ragagent

# 环境变量：DB_*/REDIS_*/SYSTEM_AES_KEY/DOCREADER_ADDR/STREAM_MANAGER_TYPE/
# RETRIEVE_DRIVER/BUILTIN_MODELS_CONFIG —— 见 .env.example
ENV DB_HOST=localhost \
    DB_PORT=5432 \
    DB_NAME=WeKnora \
    REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    DOCREADER_ADDR=localhost:50051

EXPOSE 8080
# 健康检查打 /health（与 k8s 探针、docker healthcheck 同端点）。
# 注意它只证明 Spring 上下文已就绪，不证明数据库连通 —— 存活与就绪是两件事，此处只做存活。
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/health" || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
