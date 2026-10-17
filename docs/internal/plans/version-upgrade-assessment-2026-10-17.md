# 版本升级评估（O18，2026-10-17）

> 2026-10-17 已按本文的 O18a 落地：Boot 3.5.16 / springdoc 2.8.17 / MyBatis-Plus 3.5.17，
> 全量 build 与 4867 测试通过；本文保留为升级记录。

## 结论

在临时 worktree（基线 `e70690aad`）把 Boot 升到 **3.5.16**、springdoc 升到 **2.8.17**、
MyBatis-Plus 升到 **3.5.17** 后：

- 主码/测试码**编译通过**；
- 测试 **4867 条 / 4 失败 / 4 跳过**；
- 4 条失败全部是 Jackson 2.19 对非法 JSON 错误消息的 **column 4 → 1** 变化，
  不是业务逻辑回归；
- 升级路径可行，预计落地 0.5–1 人日。

**本次未修改主工作区版本**，只记录评估结果与所需改动。

## 目标版本

| 依赖 | 当前 | 评估目标 |
|---|---|---|
| Spring Boot | 3.3.5 | **3.5.16**（3.5.x 最新） |
| springdoc | 2.6.0 | **2.8.17**（Boot 3.5 对应线） |
| MyBatis-Plus | 3.5.7 | **3.5.17** |
| Spring Framework | 6.1.x | 6.2.19（由 Boot 3.5 管理） |
| JUnit Jupiter / Platform | 5.10 / 1.10 | 5.12 / 1.12（由 Boot 3.5 管理） |

Java 21、Gradle 8.14.3 均满足 Boot 3.5 要求。

## 必需改动（已在临时 worktree 验证）

### 1. MyBatis-Plus JSQLParser 拆分

3.5.9+ 把 JSQLParser 支持拆成独立 artifact；`platform` 的 SQL 守卫直接 import
`net.sf.jsqlparser.*`，升级后会编译失败。

```toml
mybatis-plus-jsqlparser = { module = "com.baomidou:mybatis-plus-jsqlparser", version.ref = "mybatis-plus" }
```

```kotlin
// platform/build.gradle.kts
api(libs.mybatis.plus.jsqlparser)
```

### 2. JUnit Platform launcher 对齐

Boot 3.5 管理 JUnit 5.12；Gradle 8.14 自带 launcher 是旧版，`platform` /
`model-runtime` / `retrieval-engine` 会出现
`OutputDirectoryProvider not available`、整个测试执行器失败。

在 `ragagent.test-conventions` 统一加（版本由 Boot BOM 管理）：

```kotlin
dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

### 3. Jackson 2.19 `fields()` 弃用（`-Werror` 下是错误）

- `platform/.../web/ToolJson.java`：`obj.fields()` → `entries.addAll(obj.properties())`
- `platform/.../prompt/PromptTemplateCatalog.java`：`i18nNode.fields()` → `i18nNode.properties()`

### 4. Spring 6.2 方法校验 API 弃用

`GlobalExceptionHandler` 的 `getAllValidationResults()` 标记为 removal；
改为 `getParameterValidationResults()`（实现完全等价，保留分组语义）。

### 5. 4 条契约断言需重录

Jackson 2.19 对 `not json` 的错误消息从 `column: 4` 变成 `column: 1`：

| 测试 | 位置 |
|---|---|
| `AgentContractTest` | `domains/src/testFixtures/resources/contracts/ag-create-badbody.json` |
| `FavoriteContractTest` | `domains/src/testFixtures/resources/contracts/fav-add-bad-body.json` |
| `TenantMemberContractTest` | 测试内联 expected（apcPut 校验分支） |
| `SteerContractTest` | 测试内联 expected（bad JSON 分支） |

这是**线格式变化**：前端若展示 `data/message` 原文，会看到列号变化；需在升级提交里显式记录。

## 非阻塞项

- `@MockBean` 1 处（`DataSourceHttpContractTest`）在 Boot 3.5 仍可用但已 deprecated，
  Boot 4.0 移除；建议升级同批改为 `@MockitoBean`。
- `AntPathMatcher` 13 处仍存在，Boot 3.5 未移除；不阻塞。
- 13 处 `javax.*` 实际为历史注释/兼容引用，编译期无问题。

## 验证方式（临时 worktree）

```bash
git worktree add --detach /tmp/ragagent-boot35 HEAD
# 改版本 + 上述 1~4
./gradlew compileJava compileTestJava compileTestFixturesJava   # 通过
./gradlew test                                                   # 4867 / 4 fail
```

4 条失败均为上述 Jackson 列号，重录后预期全绿。

## 建议落地顺序

1. **O18a**：版本目录 + jsqlparser + launcher + 两处 deprecation + 4 条断言；
2. 全量 `./gradlew build` 与 dev 冒烟（Swagger + `/health`）；
3. 单独提交 `@MockBean → @MockitoBean`，不与版本升级混在一起；
4. 保留本评估文档作为升级记录，完成后归档到 `docs/internal/handoff/`。
