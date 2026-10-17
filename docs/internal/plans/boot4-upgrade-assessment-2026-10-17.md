# Spring Boot 4 升级评估（2026-10-17）

> 2026-10-17 **v0.4.2 Jackson 3 全量迁移已通过**：4867/0，23 守卫全绿；
> 主码已无 `com.fasterxml.jackson.databind/core` import。
>
> 2026-10-17 **v0.4.1 空跑已通过**：Boot 4.1.1 + Jackson 2 兼容桥（已去掉 spring-boot-jackson2 自动配置），4867/0；
> 后续仍需独立迁移 Jackson 3（见文末）。

## 结论

**不建议“一把梭”直接升级，但也不能无限期留在 3.5。** 按 endoflife.date / 官方支持政策口径，
3.5 的 **OSS 支持已于 2026-06-30 结束**，最后的 OSS 版本是 3.5.16；没有商业支持（Tanzu / HeroDevs）
的生产环境将不再获得 OSS 安全补丁。Boot 4.1 是当前唯一 OSS 支持充裕的版本线（至 2027-07-31）。

工程上 Boot 4 不是小版本升级：默认 JSON 栈从 Jackson 2 切到 Jackson 3，主代码有
**651 个文件 / 1329 处 `com.fasterxml.jackson.databind` import** 需要迁移。应开独立分支，
把 Boot 4.1 + Jackson 3 作为**有明确排期的迁移**，不与业务批次混在一起。

## 支持周期（2026-10-17）

| 版本 | 发布 | OSS 支持 | 商业支持 | 最新 OSS |
|---|---|---|---|---|
| **4.1** | 2026-06-30 | **至 2027-07-31** | 至 2028-07-31 | 4.1.1 |
| 4.0 | 2025-11-30 | 至 2026-12-31（临近） | 至 2027-12-31 | 4.0.8 |
| 3.5 | 2025-05-31 | **已于 2026-06-30 结束** | 至 2032-06-30 | 3.5.16 |

结论：

- **新项目 / 无商业支持**：直接上 4.1.x；
- **3.5 存量**：若已购商业支持，可短期停留；否则应尽快排期 4.1，不能把 3.5 当作长期安全基线；
- **4.0**：年内规划升到 4.1，避免 OSS 补丁断供。

## 目标版本

| 组件 | 当前 | Boot 4 目标 |
|---|---|---|
| Spring Boot | 3.5.16 | **4.1.1**（当前稳定线） |
| Spring Framework | 6.2.19 | 7.0.x |
| Jackson | 2.19 | **3.x（`tools.jackson.*`）** |
| Jakarta EE / Servlet | 10 / 6.0 | **11 / 6.1**（Tomcat 11） |
| JUnit | 5.12 / Platform 1.12 | **6.x** |
| springdoc | 2.8.17 | **3.1.1**（Boot 4 线） |
| MyBatis-Plus | 3.5.17（Boot 3 starter） | **3.5.17（Boot 4 starter）** |

生态侧已就绪：`springdoc-openapi-starter-webmvc-api:3.1.1` 与
`mybatis-plus-spring-boot4-starter:3.5.17` 均已在 Maven Central 发布。

## 主要阻塞

### 1. Jackson 3 包迁移（最大项）

Boot 4 迁移指南明确：`com.fasterxml.jackson` 的 **databind/core** 改为 `tools.jackson.*`；
**jackson-annotations 例外**，仍是 `com.fasterxml.jackson.annotation`。

本仓现状：

| 面 | 文件 | import 处 |
|---|---:|---:|
| `com.fasterxml.jackson.databind` | 651 | 1329 |
| `com.fasterxml.jackson.core` | 54 | 59 |
| `com.fasterxml.jackson.annotation` | 112 | 179（**不需要改包**） |
| `com.fasterxml.jackson.datatype` | 7 | 7 |

关键收口点：

- `platform/.../web/JsonMappers.java`
- `platform/.../web/ContractJsonModule.java` + 12 个 `ContractJsonMixins*.java`
- `platform/.../web/ToolJson.java`（排序/转义/64 位浮点）
- `model-runtime/.../modelcontext/ModelJsonValues.java`（`FAIL_ON_TRAILING_TOKENS` 自定义 mapper）
- `boot/.../config/WebConfig.java` 的 `Jackson2ObjectMapperBuilderCustomizer`
  （Boot 4 已改名 `JsonMapperBuilderCustomizer`）

风险不在编译，而在 **JSON 字节契约**：键序、HTML 转义、数字归一、零值时间、
mixin 形状都已有金片/契约断言，迁移必须逐项复跑。

### 2. Spring Framework 7 / Boot 4 移除项

- Boot 3.x 标记 deprecated 的类/方法/配置属性在 4.0 已移除；
  `-Werror` 会在主码先暴露一批。
- `@MockBean` 已在 Boot 3.5 迁移为 `@MockitoBean`，这一项已提前清掉。
- `javax.inject.Inject` 6 处需迁 `jakarta.inject.Inject`；`javax.sql` / `javax.crypto`
  是 JDK 包，不受影响。
- 嵌入式可执行 jar 启动脚本、部分 Session/Spock 集成被移除；本仓未使用。
- Spring Retry 依赖管理移除；本仓未使用。

### 3. Jakarta EE 11 / Servlet 6.1

Tomcat 11、Servlet 6.1 基线；`jakarta.servlet` 现有 import 可继续用，但嵌入式容器、
Filter/Listener 行为需全量测试。当前 51 个控制器的 HTTP 契约测试可覆盖大部分。

### 4. 测试栈

JUnit 6 + Platform 6；`junit-platform-launcher` 需随 Boot 4 BOM 对齐。
ArchUnit / Mockito / AssertJ 需确认 Boot 4 管理的版本兼容性；当前 4867 条测试是回归基线。

### 5. springdoc 3 / Scalar

- springdoc 3.1.1 的 OpenAPI 3.1 输出面仍需逐字段核对；
- Scalar 只依赖 `/v3/api-docs`，页面本身不受 Jackson 包名影响；
- `SPRINGDOC_ENABLED` 开关可保留。

## 建议迁移顺序（独立分支）

1. **Boot 4 空跑**：升级 Boot 4.1.1 / springdoc 3.1.1 / MyBatis-Plus Boot4 starter，
   暂用 Jackson 2 兼容依赖，先修 Spring 7 / Servlet 6.1 / JUnit 6 编译与测试；
2. **Jackson 3 收口**：先把 `JsonMappers`、`ContractJsonModule`/mixins、`ToolJson`、
   `ModelJsonValues`、`WebConfig` 自定义器迁到 `tools.jackson`；
3. **机械迁移**：651 个 databind/core 文件分批替换，`jackson-annotations` 保持原包；
4. **金片回归**：契约 golden、录制回放、JSON 往返、OpenAPI 输出逐字节/逐结构复核；
5. **切换默认栈**：删除 Jackson 2 兼容依赖，确认 Boot 自动配置与 MVC converter 全走 Jackson 3；
6. 全量 `build` + 真实 jar 冒烟 + Scalar 页面手测。

## 估算

- 机械 import 替换：1–2 天；
- 核心 mapper/mixin/金片修复：3–5 天；
- Spring 7 / Servlet / JUnit 6 兼容：2–3 天；
- 回归与线上形态冒烟：2 天。

**至少一个独立迭代**，且不宜与业务功能同批。当前 3.5.16 是最后的 OSS 版本；
若生产没有商业支持，应把 Boot 4.1 迁移列为**近期必做**，而不是可选技术债；
若已购商业支持，可短期停留，但仍应保留独立迁移分支。
