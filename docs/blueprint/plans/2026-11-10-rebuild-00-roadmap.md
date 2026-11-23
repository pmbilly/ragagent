# RagAgent 架构重建：路线图与执行手册

**规格来源**：`docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`（仓库根相对路径）

本文是整套计划的入口。它回答四件事：有哪几份计划、按什么顺序做、所有计划共同遵守什么、执行方会话怎么接手。各份计划里的任务细节不在这里重复。

# 1. 阅读顺序

1. 本文第 2、3、8 节。
2. spec 第 4 到 6 节。
3. 要执行的那份计划。每份计划开头的 Global Constraints 与本文第 3 节一致，冲突时以本文为准并在 `docs/migration/deviations.md` 登记。

# 2. 计划清单与顺序

计划严格按编号执行，后一份的前置是前一份的闸门全部通过。

| 编号 | 文件后缀 | 范围 | 完成闸门 |
|---|---|---|---|
| 01 | `rebuild-01-skeleton-and-safety-net` | 冻结点、五模块骨架、framework、架构规则测试、OpenAPI 流水线、界面端到端与检索回归基线、MCP 与 Nimbus 验证、自建数据库镜像 | 该计划末尾的完成判据全绿 |
| 02 | `rebuild-02-engines-in` | model-runtime、retrieval-engine、agent-engine 改包名搬入，补 `api` 与 `spi`，再按 spec 5.5 整理内部，agent-engine 过 5.10.4 三组用例 | 引擎三模块单测与集成测通过，检索回归基线对拍不劣于旧系统，ArchUnit 与 Modulith 通过 |
| 03 | `rebuild-03-base-domains` | 基础域：tenant、auth、system、storage、model、vectorstore、websearch、audit | 控制台登录、租户、模型、存储、向量库、网页搜索、系统设置各页的界面端到端在新栈转绿 |
| 04 | `rebuild-04-knowledge` | knowledge 域 | 知识库与文档相关页面转绿，入库与检索回归通过，冒烟的建库、上传、入库三步转绿 |
| 05 | `rebuild-05-wiki-datasource` | wiki、datasource | 两域相关页面转绿 |
| 06 | `rebuild-06-agent-mcp-memory-favorite` | agent、mcp、memory、favorite | 四域相关页面转绿，mcp 通过 D26 结论落地 |
| 07 | `rebuild-07-channel` | channel：合并 im 与 embedchannel | 渠道相关页面转绿 |
| 08 | `rebuild-08-session-agenttools` | session、agenttools，打通问答主链路 | 控制台问答、挂件访客对话转绿，冒烟六条全绿，检索回归与引擎对拍在整链路上通过 |
| 09 | `rebuild-09-peripheral-and-cutover` | docreader 收进仓库并合并 OCR、mcp-server 改新 API（含破坏性变更，升 2.0.0）、前端拆 apps 与 packages 并迁 pnpm、部署角色与镜像、可观测、合并 Flyway 为单份 V1、删旧模块与守卫脚本与 baseline | spec 第 7 节八项验收全部满足 |

文件名统一为 `docs/optimization/plans/2026-11-10-<后缀>.md`。第二阶段的计划不在本套内，等第 09 份闸门通过后另写。

# 3. 全局约定

所有计划继承本节。

## 3.1 分支与冻结

1. 冻结点是启动重建时 main 的 HEAD。计划 01 Task 0 打标签 `rebuild-freeze` 并建分支 `rebuild`。此后所有计划的提交都进 `rebuild`，main 只修阻断性 bug。
2. 旧系统作为被测对象与迁移依据，用 `git worktree add ../ragagent-legacy rebuild-freeze` 取出，只读，不在里面改代码。
3. 冻结后 main 上的 bug 修复登记到 `docs/migration/frozen-bugs.md`，格式为一行一条：日期、提交哈希、涉及域、一句话描述。每个域计划的第一个任务读取该文件，把本域条目带入。

## 3.2 目录与包

1. 后端新代码只在 `backend/`，包根 `com.ragagent`。引擎包为 `com.ragagent.modelruntime`、`com.ragagent.retrieval`、`com.ragagent.agentengine`，每个只含 `api`、`spi` 与内部包。
2. 业务域包为 `com.ragagent.<domain>`，域内子包按 spec 5.5：`api`、`controller`、`service`、`repository`、`mapper`、`entity`、`bo`、`vo`、`assembler`。Mapper XML 放 `backend/app/src/main/resources/mapper/<domain>/`。
3. 装配类只放 `com.ragagent.config`。
4. 旧代码目录 `contract/`、`platform/`、`domains/`、`boot/` 与三个旧引擎在第 09 份之前原样保留，只读。

## 3.3 错误码模块号

业务错误码 10 位：`1` + 系统号 `001` + 模块号 3 位 + 序号 3 位。模块号在此统一分配，枚举放在各域 `api` 包，命名 `<Domain>Code`，域的配置类在启动时调用 `CodeCache.register`。

| 模块号 | 归属 | 模块号 | 归属 |
|---|---|---|---|
| 001 | framework | 013 | agent |
| 002 | tenant | 014 | mcp |
| 003 | auth | 015 | memory |
| 004 | system | 016 | favorite |
| 005 | storage | 017 | channel |
| 006 | model | 018 | session |
| 007 | vectorstore | 019 | agenttools |
| 008 | websearch | 020 | model-runtime |
| 009 | audit | 021 | retrieval-engine |
| 010 | knowledge | 022 | agent-engine |
| 011 | wiki | 030 到 033 | 第二阶段 customer、moderation、lead、handoff |
| 012 | datasource | | |

每个错误码必须同时出现在七种语言的文案里，见 3.4。

## 3.4 多语言文案

1. framework 的文案在 `backend/framework/src/main/resources/i18n/messages*.properties`，业务域文案统一放 `backend/app/src/main/resources/i18n/app-messages*.properties`，两组基名都由 `gac.furnace.multilang.basenames` 配置装入。
2. key 格式 `code_<错误码>`，语种为 zh-CN、zh-HK、en-US、es-ES、pt-BR、ru-RU、th-TH 加一份默认文件。
3. 每个域计划的错误码任务产出表格：模块名称、功能点、业务场景、中文、English，表头沿用用户全局规则。其余五种语言由机器翻译出初稿，同一提交里入库，标注待人工校对。
4. 每个域计划加一条测试：遍历该域全部错误码，七种语言各取一次文案，断言不等于 key 本身。

## 3.5 Flyway 与表结构

1. 迁移期间所有域脚本放 `backend/app/src/main/resources/db/migration/allenv/`，不另建子目录。命名 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_{域名}_{描述}_jiangguifan.sql`，注意序号后是双下划线，Flyway 以双下划线分隔版本与描述，单下划线会把 DDL 并进版本号导致文件名无效。日期取写脚本当天，同日序号递增。DDL 与 DML 分文件。
2. 域之间只存对方 ID，不建外键，所以脚本顺序只受域内依赖约束。
3. 主键 bigint 雪花 ID，租户表带 `tenant_id bigint NOT NULL` 并开启行级安全，策略模板使用计划 01 Task 5 的 fail-closed 版本。时间字段 `timestamptz`，软删字段 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
4. 每个域计划的 DDL 任务要同时给出：表清单、每张表的写属主（聚合根）、旧表到新表的字段映射表、本域的跨租户访问被拒测试，每张租户表一条。
5. 第 09 份把所有 allenv 脚本合并为单份 V1，合并前保持分开。用户已确认（2026-11-10）：合并后的单份 V1 可同时含 DDL 与种子 DML，这是对 DDL、DML 分开规则的唯一例外，增量脚本仍分开。

## 3.6 API 与响应

1. 路径三组：管理面 `/api/admin/v1`，C 端访客 `/api/v1`，开放接口 `/api/open/v1`。后台登录用户用到的所有接口，包括控制台里的问答、会话、记忆与收藏，都归 `/api/admin/v1`。`/api/v1` 只给访客身份，也就是挂件与 H5 对话页。
2. 资源路径用名词复数，层级不超过三级，动作类接口用 `POST /<资源>/{id}/<动词>`。同一资源在不同前缀下不重复实现，由同一个 Service 服务。
3. 请求体一律 VO 加 `@Valid`。响应统一 `RestResponse`，流式接口除外。ID 序列化为字符串。
4. 错误提示语言来自 `Accept-Language`，缺失或不支持回落 zh-CN。

## 3.7 租户与安全

1. 租户 ID 来自登录会话，不信任请求头。请求头切换租户只在 tenant 域的切换策略里允许，规则由计划 03 定。
2. 所有租户表带行级安全，Repository 与 Mapper XML 不手写 `tenant_id` 条件。检索引擎里服务于索引命中的谓词例外。
3. 跨租户的系统通道只用 `@IgnoreTenant`，每处使用在代码里写一行中文注释说明原因，ArchUnit 规则限制它只出现在白名单包。白名单随域计划追加，在 `docs/migration/progress.md` 登记。
4. 凭据、令牌类字段一律用 `@Encrypted`。

## 3.8 前端 API 层切换

1. 组件与页面不改。每个域只改 `frontend/src/api/<对应文件或目录>`。
2. API 层函数保持原导出名与返回给组件的数据形状。新接口的响应与旧形状不同时，适配写在 API 层函数内部。
3. 传输用计划 01 Task 12 产出的 `@ragagent/api-client`：`createApiClient` 加类型 `paths`。它统一注入 `Authorization: Bearer`、`Accept-Language`，并把 `RestResponse` 外壳解包，失败抛 `ApiError{code,msg}`。计划 03 的第一个前端任务负责把它接进 `frontend/package.json`。
4. 每个域计划的前端任务给出一张表：旧 API 函数、旧路径、新路径、返回形状是否一致、适配点。
5. 前端类型再生成由 `npm run gen:api` 完成，提交产物，CI 的 `api-drift` 守住漂移。

## 3.9 测试分层与旧测试处置

1. 每个域的测试分四层：Repository 与 Service 集成测试用 Testcontainers PostgreSQL，Controller 用 MockMvc，跨租户拒绝测试，界面端到端与冒烟。
2. 每个域计划必须有一张旧测试处置表：旧测试类、测什么、处置。处置只有三种：移植业务断言，改写为新规范，丢弃。丢弃的理由只能是它在钉 Go 版的错误文案、零值或字节形状。
3. 多入口用例（会话问答被控制台、挂件、渠道、开放接口、Agent 工具共同调用）在 session 域写，不在各入口域重复。

## 3.10 提交、进度与偏离

1. 每个任务一次提交，提交前先跑该任务的验证命令。`git add` 只写显式路径。
2. 每个任务完成后在 `docs/migration/progress.md` 追加一行：计划编号、任务编号、提交哈希、日期。
3. 与计划不一致的决定逐条写进 `docs/migration/deviations.md`：计划编号、任务编号、原计划、实际做法、原因、是否影响后续计划。影响后续计划的，同一提交里把受影响的计划文本改掉。
4. 框架 API 与计划 01 钉住的签名不一致时，按偏离登记，并检索所有后续计划里的引用一并改。

## 3.11 代码质量标准

目标是让接手的 Java 工程师读一遍就能改。下面的数字是硬门，由 Checkstyle 在 `mvn verify` 里拦截，超标构建失败。旧仓库的守卫是方法 150 行、文件 600 行的棘轮，新标准更严，且没有基线豁免。

**尺寸**

| 项 | 上限 | 说明 |
|---|---|---|
| 方法体 | 40 行 | 不含注释与空行。超过先按“阶段”或“规则”拆成命名清楚的私有方法 |
| 类 | 400 行 | 超过按变化原因拆类，不按技术层拆 |
| 方法参数 | 5 个 | 超过收成 Condition、BO 或 record |
| 圈复杂度 | 10 | 超过用提前返回、多态或策略表消解 |
| 嵌套深度 | 3 层 | if、for、try 合计 |
| 构造器注入的依赖 | 7 个 | 超过说明类承担了两件事 |

确需例外，在方法或类上写 `@SuppressWarnings("checkstyle:<规则名>")`，紧邻一行中文写明理由。ArchUnit 测试统计全仓抑制数，超过 10 处构建失败，迫使例外保持稀少。迁入的引擎旧代码在计划 02 整理完成前，用独立的抑制文件豁免，整理任务完成时逐包删除豁免。

**命名**

1. 类名是业务名词，例如 `KnowledgeBase`、`DocumentIngestion`、`ChannelMessageRouter`。方法名以动词开头并表达业务意图，例如 `claimNextPending`、`archiveExpired`。
2. 禁用后缀与词：`Ops`、`Support`、`Helper`、`Facade`、`Util` 之外的杂物类名、`Manager`、`Common`、`Base`（框架基类除外）。`Impl` 只在同一接口有两个以上实现时使用，单实现不建接口。
3. 不描述 SQL 动作命名，例如 `updatePartial`、`findByIdAny`。不使用 `data`、`info`、`obj`、`tmp`、`flag`、`handle`、`process` 这类无信息的词作类名或方法名主体。
4. 布尔值用 `is`、`has`、`can` 开头，集合用复数，常量用业务含义命名，不用类型命名。
5. 除 `id`、`url`、`kb` 这类通行缩写外不缩写。测试方法名写成行为句，例如 `rejectsDuplicateLiveRowAfterSoftDeleteRecreate`。

**设计**

1. 一个类一个变化原因。Service 方法对应一个用例，用例的前置校验、核心步骤、提交后动作分别是可读的私有方法。
2. 同一维度出现第三个分支时用多态替代 if 与 switch 链，例如渠道适配器、检索引擎、存储后端、模型厂商、文档解析器。做法统一：声明窄接口，实现类注册为 Spring Bean，使用方注入 `Map<类型, 实现>` 或登记表按类型取用。只有两个分支时直接写 if，不预建接口。
3. 模式只用在真实存在多个实现或多个变化点的地方，不为凑设计模式建类。新增类必须有第二个调用方或第二个实现，否则并回调用方。这是 spec 反目标第 3 条的具体化。
4. 构造器注入，依赖字段 `final`，用 Lombok `@RequiredArgsConstructor`。禁止字段注入。
5. VO 与 BO 优先用 `record`，Entity 用 `@Getter`、`@Setter`。组合优于继承，继承只用于 `ServiceImpl` 与 framework 基类。
6. 异常用 `BusinessRuntimeException` 加错误码，不吞异常，不用异常做控制流。参数校验只放在边界，内部代码信任已校验的数据。
7. 注释写原因，不写做了什么。public 类与 public 方法有中文 Javadoc，沿用用户全局规范。
8. 禁止带入 Go 版写法：`rawBody`、`Map<String, Object>` 传递业务数据、`trimGo` 一类零值工具。

**样板与评审**

1. 计划 03 里第一个完成的域，其 Controller、Service、Repository、Assembler、测试的路径登记在 `docs/migration/golden-sample.md`。之后各域写法以它为参照，偏离要在 `deviations.md` 说明。
2. 每个任务提交前过质量自检：Checkstyle 全绿；问一遍能否把这段代码砍掉一半；新类有没有第二个调用方；名字能否让不看实现的人猜对用途。
3. 每份计划完成后，对整条分支用 `code-review-mr` 技能做一次多维审查，结论登记在 `progress.md`，高置信问题修完才算该计划过闸门。

# 4. 域迁入标准任务序列

计划 03 到 08 里每个域按下面八步拆任务。体量小的域可以合并相邻两步，不可以省略任何一步的验证。

| 步 | 内容 | 验证 |
|---|---|---|
| a | 读 `frozen-bugs.md` 中本域条目，核对本计划盘点表与冻结点代码一致 | 盘点表有差异时先改计划 |
| b | DDL、行级安全策略、Flyway 脚本，旧表到新表映射 | 迁移在 Testcontainers 上执行成功，每张租户表一条跨租户被拒测试通过 |
| c | Entity、Mapper、Mapper XML、Repository，按聚合根 | Repository 集成测试通过，反向验证：关掉行级安全后跨租户测试变红 |
| d | `api` 包：BO、Provider、错误码枚举、i18n 七语文案 | 错误码文案完整性测试通过 |
| e | Service：用例、事务、事件，移植旧业务断言 | Service 集成测试通过，事务内无外部调用 |
| f | Controller、VO、Assembler、springdoc 注解 | MockMvc 测试通过，OpenAPI 导出无漂移 |
| g | 前端 API 层切换与类型再生成 | 前端类型检查通过，该域旧 API 文件不再引用旧路径 |
| h | 界面端到端与冒烟转绿，Modulith 与 ArchUnit 与 Checkstyle 通过，按 3.11 做质量自检，登记 progress | 本域对应的 e2e 与冒烟用例全绿，全量 `mvn verify` 全绿 |

# 5. 计划文件的写法要求

后续计划的作者按此写，执行方发现某份计划不满足时按偏离登记。

1. 头部沿用 writing-plans 的格式，`规格来源` 行写仓库根相对路径。
2. 先写本计划的前置、交付、对外承诺三段。前置列出依赖的前序计划产物，交付列出本计划完成后别的计划能依赖的东西，对外承诺列出本计划要实现的 Provider 与 SPI。
3. 盘点表直接写进计划：旧包与文件规模、旧表、旧接口、旧测试、旧类到新类的映射。执行方不再重新盘点，只核对。
4. 任务里给出签名、测试名与断言、命令与预期输出。算法体只在签名和测试决定不了时才写。一份域计划控制在 1200 行以内。
5. 每个计划末尾有完成判据，逐条能对应到命令输出。
6. 每个计划写完后做两层独立校验：计划对齐 spec，spec 对齐框架机制。校验结论追加在计划文件末尾的“校验记录”小节。

# 6. 跨计划交接表

D25 规定下级域需要上级数据时由上级实现下级声明的 Provider。下表是各计划落盘后对账的结果，名称与实现方以各计划的「对外承诺」为准。

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| model（03） | `ModelReferenceProvider` | 删除模型前列出引用方 | knowledge（04）、agent（06） |
| vectorstore（03） | `VectorStoreReferenceProvider` | 删除向量库前统计绑定知识库数 | knowledge（04） |
| storage（03） | `StorageReferenceProvider`、`ResourceReferenceProvider`，共用 `storage.api.ResourceReferenceBo` | 删除存储后端前统计绑定数、资源归属 | knowledge（04） |
| tenant（03） | `TenantStorageApi` | 替代 storage 直接 `UPDATE tenants` | storage 调用 |
| storage（03） | `FileStorageApi` | 附件读写，计划 07 用 | 本域 |
| knowledge（04） | 五个 api：BaseQuery、Content、Question、Ingest、Search | 资源归属判定、grep 检索、按 ID 过滤存在性、分块读写 | 本域，供 session、agenttools、agent、wiki、datasource |
| model-runtime（02） | `spi.ModelCatalogReader`、`ModelFactsReader`、`ModelCredentialReader` | 引擎读模型行、事实与凭据 | model（03） |
| retrieval-engine（02） | `spi.VectorStoreCatalog` | 引擎读向量库配置并校验归属 | vectorstore（03） |
| retrieval-engine（02） | `spi.TenantRetrievalSettings` | 租户有效引擎与检索调参 | tenant（03） |
| retrieval-engine（02） | `spi.KnowledgeSearchSource` | KB 元数据、文档可达性、分块回填 | knowledge（04） |
| agent-engine（02） | `spi.SkillCatalog` | 技能披露 | agent（06），`VisibleSkillCatalog` |
| agent-engine（02） | `spi.McpToolProvider` | MCP 工具注册与调用 | agenttools（08） |
| agent-engine（02） | `spi.AgentEventSink`、`SteerSink` | 事件落库与运行中插话 | session（08） |
| channel（07） | `ChannelTurnProvider`、`ChannelVisitorApi` | 渠道与挂件调用问答 | session（08） |
| mcp（06） | `ApprovalResolutionListener` | 审批决定后恢复轮次 | session（08） |
| memory（06） | `MemoryTranscriptProvider` | 记忆抽取读会话转写 | session（08） |
| auth（03） | `CurrentPrincipal`、`UserQueryApi`、`RoleApi` | 当前主体与用户名回填 | 全部域 |

**对账发现的缺口，执行前必须处理：**

1. 计划 03 没有写 `ModelCatalogReader`、`ModelFactsReader`、`ModelCredentialReader`、`VectorStoreCatalog`、`TenantRetrievalSettings` 的实现任务，只在 vectorstore 一行提到“spi 适配类”。计划 02 的清单要求 03 实现这五个。
2. 计划 04 实现的是自己命名的 `KnowledgeBaseOwnershipResolver`，没有实现计划 02 的 `KnowledgeSearchSource`。两者职责不同，04 需增加该实现。
3. 计划 07 与计划 08 的流分发归属不一致。07 写 `StreamManager` 删除，08 写迁入 `framework.stream`。以 08 为准，计划 01 需新增 framework 流分发，见第 6.1 小节。
4. 计划 08 的 `ChannelConversationService` 没有写明实现 `ChannelTurnProvider` 与 `ChannelVisitorApi`，只写“以计划 07 为准”。07 已落盘，08 的 Task 需改为明确实现这两个接口。

## 6.1 对计划 01 的新增需求汇总

| 来源 | 需求 |
|---|---|
| 计划 02 | `BusinessRuntimeException` 补静态 `wireText(Throwable)`，agent-engine 有 7 处依赖 |
| 计划 05 | `DistributedLockKeyResolver` Bean 加 `@ConditionalOnMissingBean`，wiki 才能按 kbId 与 slug 生成锁键 |
| 计划 07 | `SaRouteRules` 为渠道回调路径加 permit 例外 |
| 计划 08 | framework 新增流分发存储 `framework.stream`、只读 SQL 执行器，放宽 `JdbcTemplate` 白名单 |

这些需求没有回写计划 01，等你确认后统一改。

# 7. 执行机环境清单

计划 01 Task 1 的 Step 0 逐项自检，缺失项先补齐。

1. JDK 21，`JAVA_HOME` 指向它，`mvn -v` 显示 Java 21。
2. Maven 与 settings 文件。用户全局规则要求命令带 `-s C:/Users/jiangguifan/.m2/settings-coding-uat.xml`。执行机上该文件不存在或路径不同时，在 `docs/migration/deviations.md` 登记实际路径并替换全部计划里的该参数。
3. Docker 可用，能拉取 `postgres`、`redis`、`minio`、`elasticsearch`、`milvus` 镜像。
4. Node 22 与 npm，Python 3 用于 `scripts/stub-llm-server.py`。计划 09 另需 pnpm、uv、pip-licenses、actionlint。
5. Playwright 的 chromium，`npx playwright install chromium`。
6. 端口约定：旧栈 PostgreSQL 15432、Redis 16379、旧后端 8083、前端 5173，新后端 8080。新栈开发库用 `deploy/db/docker-compose.yml`，端口 25432，旧栈依赖 ParadeDB，不能改它的 compose。新栈测试只用 Testcontainers 随机端口。
7. 用 `git worktree` 取出的旧系统副本能按 `scripts/dev-up.sh` 起来，管理员账号与一个渠道 ID 可用，问答用 `stub-llm-server.py` 作为模型。检索回归基线必须用真实 embedding 模型录制，stub 的 embeddings 固定返回 3 维向量不可用。用户已确认（2026-11-10）使用阿里云百炼的 embedding 模型，执行机需要百炼 API Key，模型型号、维度与调用地址写入 `tests/regression/README.md`，Key 只放环境变量，不入库。
8. 磁盘空间不少于 30 GB，Testcontainers 镜像与前端依赖占用大。

# 8. 执行方会话须知

1. 执行方先读本文，再读计划 01，用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 逐任务执行。每份计划开始前先在 `docs/migration/progress.md` 写一行开工记录。
2. 禁止自行扩大范围。计划没有的功能、没有的重构不做。发现 spec 与计划冲突时按偏离登记，必要时停下来问用户。
3. 汇报必须单列“偏离计划”段落，逐条点名，格式与 `deviations.md` 一致。
4. 旧代码只读。迁移时读冻结点的旧代码取业务规则，不照搬 Go 语义、`rawBody`、`Map<String, Object>`、`trimGo` 一类写法。
5. 每份计划完成后，由执行方跑完成判据并把输出贴进 `progress.md`，用户确认后才开下一份。
6. 失败两次未命中根因时停止试探，回到证据收集，写明已排除项再继续。
7. 第 09 份之前不删任何旧模块。

# 9. 本文中由 Claude 决定、需用户核对的事项

1. 后台用户在控制台里用到的问答、会话、记忆、收藏接口都归 `/api/admin/v1`，`/api/v1` 只给访客。spec 5.6 只说三组对应三套账号体系，没有定控制台问答归哪一组。
2. 租户 ID 来自登录会话，请求头不可信。这与计划 01 初稿读取 `X-Tenant-Id` 的做法不同，初稿已按此修订。
3. 错误码模块号分配。
4. 业务域文案统一放 app 的 `app-messages*.properties`，不按域拆文件。
5. 界面“零变化”的判据用 Playwright aria 快照加文本断言，不做像素截图，原因是像素比对在不同机器上不稳定。
6. 计划 04 单独成篇，不与 wiki、datasource 合并，原因是 knowledge 域 3 万行且是其他域的上游。
7. 3.11 的尺寸上限数字（方法 40 行、类 400 行、参数 5 个、复杂度 10、嵌套 3 层、依赖 7 个、抑制 10 处）是我定的起点，需要你核对。Checkstyle 属于 Maven 插件，不是 Python 脚本，不违反 spec 验收第 6 条，但它不是 Java 测试，这一点请确认可以接受。

# 10. 遗留事项

1. 域计划按冻结点旧代码盘点。冻结后 main 的 bug 修复靠 `frozen-bugs.md` 带入，漏登记会漏迁。
2. 计划 03 到 08 的任务量按旧代码规模估算，实际执行中某域超时要拆计划，拆分时更新本文第 2 节。
3. 第二阶段的 customer、moderation、lead、handoff 与 channel 新增渠道没有计划，等第 09 份完成后另写。
4. Elasticsearch 与 Milvus 不受行级安全保护，隔离靠知识库归属校验，见 spec 第 10 节。
5. spec 写 41 项引擎能力，5.10.1 表实际 38 行。原意已无从考证，以 38 行加计划 02 补的 3 项（两帧 done、行级安全收口、引擎只在配置类装配）为准。
6. 计划 07 把平台回调路径改为 `/api/open/v1/channels/{channelId}/callbacks`，用户已确认可接受。切换时各渠道平台控制台登记的回调地址要同步改，写入第 09 份的切换手册。
