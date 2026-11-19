# RagAgent 架构重建设计

**日期**：2026-11-09
**状态**：待审批
**来源**：与用户的需求讨论（本次会话），参考工程 furnace-framework、infra-aia-customer、bff-aia-cms、furnace-framework-doc/standards/three-layer-arch.md

# 1. 目的

把 RagAgent 改造成面向 C 端的智能客服与营销智能体平台，代码结构要让普通 Java 工程师一眼看懂。

工作分两个阶段，见 4.3。第一阶段只重构实现，产品设计、界面与交互保持不变；第二阶段在新架构上开发 C 端新能力。

现状的分层方向是对的，问题出在写法上。代码逐行复刻了 Go 版的行为，并靠一套自研的端口层和 42 个 Python 守卫维持边界。具体有五类：

1. Go 语义被逐字节保留。主码有 78 处 `@RequestBody String rawBody` 手写绑定、121 处 `ResponseEntity<?>`、1,256 处 `Map<String, Object>`，还有 `trimGo`、`orGoZero`、`GoJsonBindError` 等工具。根源是 1,339 个契约金片钉住了 Go 的错误文案和零值语义。
2. 大类靠回引拆分。17 个类用 `new XxxOps(this)` 拆出协作者，协作者通过包可见字段反向取依赖，例如 `TenantCatalogController`、`MemoryService`、`UserService`。全仓有 76 个 Ops、25 个 Support 类。
3. 单体内部端口过重。contract 有 94 个接口，71 个只有 1 个实现，7 个 `Knowledge*Port` 只被聚合接口继承、无人直接使用。domains 下 32 个 Adapter 基本是一行转发，另有 23 个 Wiring 类。
4. 业务边界只能靠脚本守。domains 一个 Maven 模块装了 21.6 万行、19 个域，域间边界无法由编译器保证。
5. 多租户隔离靠手写。469 处手写 `tenant_id`，`TenantFilterGuard` 只在运行期探测，白名单登记了 72 条例外。后端没有任何 `messages*.properties`，650 处 `BizException` 文案是硬编码英文。

这五类问题让每加一个接口、一个跨域能力都要碰多处转发层，也让新人必须先读懂守卫脚本才知道规矩。新定位面向 C 端，还要新增客户、渠道、内容安全等多个域，在现结构上叠加只会放大问题。

# 2. 目标、非目标、反目标

## 2.1 目标

1. 产品定位：品牌方用它搭建面向 C 端的智能客服与营销智能体。营销只做被动应答：答疑、导购、推荐、留资、转人工。
2. 多租户：一套代码同时支持 SaaS 和私有部署，隔离按 SaaS 强度做。
3. 多语言：前后端所有对用户可见的文案都按请求语言返回。
4. 代码结构符合团队 Java 习惯，参照 furnace 与 aion 工程的写法。
5. 对外 API 按 REST 惯例重新设计，前端类型由 OpenAPI 生成。

## 2.2 非目标

1. 主动触达：人群圈选、模板消息与短信推送、私域运营。以后对接 CRM 或 SCRM。
2. 自建坐席工作台。转人工交给渠道原生接待或第三方客服系统。
3. 按租户分库。库级隔离靠为大客户单独部署一套。
4. 提示词多语言版本。提示词只维护一套。
5. 员工门户。终端用户是外部访客，不做面向员工的对话门户。

## 2.3 反目标

出现以下任一情况，即使功能都能跑，也算失败：

1. 迁移后 RAG 召回或回答质量下降，而没有任何测试发现。
2. 迁移后界面或交互出现了非计划内的变化。
3. 新代码里重新长出纯转发层：一行转发的 Adapter、只为凑层数的 BO、按表拆分的 Repository。
4. 架构规则又回到依赖 Python 脚本维持。

# 3. 约束

1. 系统未上线，无历史数据和线上流量需要兼容。
2. 技术基线为 JDK 21、Spring Boot 3.5.16、Jackson 2.19、PostgreSQL 17 加 pgvector、Redis 7，详见 5.9。
3. furnace 基于 Boot 2.7 和 `javax`，不能直接依赖。ragagent 的 origin 是公开的 MIT 仓库，furnace 是广汽内部基座，不能拷贝其源码，只参考设计和 API 形状重写。
4. 本项目大量配置保存在数据库里，由租户在界面上维护：`system_settings` 表，`tenants` 表的 13 个 jsonb 配置列，以及 `models`、`storage_backends`、`vector_stores`、`web_search_providers`、`im_channels`、`embed_channels`。furnace 的启动期配置模式不能覆盖这些场景。
5. Java 代码遵循用户全局规范：tab 缩进、行宽 140、中文 Javadoc、魔法字符串收口为常量或枚举、`@author jiangguifan`。
6. 第一阶段的产品设计、界面与交互保持不变。前端组件与页面不改，只替换 API 调用层。唯一例外见 4.3。

# 4. 方案选择

## 4.1 迁移方式

| 方案 | 做法 | 取舍 |
|---|---|---|
| **A 新骨架逐域迁入（选定）** | 新分支建五模块骨架与安全网，按域依赖顺序逐个用新规范重写，引擎代码改包名搬入 | 每个域迁完就能验证，AI 改动范围清楚；代价是问答主链路迁完前跑不通端到端对话 |
| B 原地渐进重构 | 保持系统可运行，逐步替换 | 新旧机制长期并存，每步兼顾两套规则；系统未上线，可运行的好处不值钱 |
| C 一次性整体重写 | AI 按文档一次生成 | 验证全部堆到最后，RAG 细节容易悄悄丢失 |

## 4.2 其他已定决策

| 编号 | 设计点 | 选定 | 否决项与理由 |
|---|---|---|---|
| D1 | 线格式 | 全面放开，路径与结构按 REST 重新设计 | 保留字段结构：Go 痕迹会残留在控制器层 |
| D2 | 多语言范围 | 前后端可见文案全部多语言，提示词一套 | 多套提示词：调优和回归成本随语种线性增长 |
| D3 | 管理员配置的开场问题与欢迎语 | 多语言结构，各语言可不填，缺失回落 default | 单语言：多语言租户只能选一种；运行时翻译：开场第一句可能翻错且每次多一次调用 |
| D4 | 租户形态 | SaaS 与私有部署共用代码，按 SaaS 强度隔离 | 只做私有部署：以后补硬隔离要重过全部数据访问 |
| D5 | 隔离机制 | 租户插件自动注入，PostgreSQL 行级安全兜底 | 只用插件：`JdbcTemplate` 与解析失败的 SQL 会漏；按 schema 分库：迁移和向量引擎都要按租户复制 |
| D6 | 库级隔离 | 按部署形态，大客户单独部署 | 同集群动态分库：动态数据源会渗透事务、迁移、缓存和后台任务 |
| D7 | 基础设施 | 在 ragagent 内按 furnace 的设计与 API 风格重写 Boot 3 版 | 先给 furnace 做 Boot 3：受其排期约束；降 Boot 2.7：已停止维护且失去虚拟线程 |
| D8 | 模块结构 | 引擎保持 Maven 模块，业务域按包划分并由 Spring Modulith 管边界，contract 解散 | 每域一个模块：38 个模块且要先解循环；保留 7 模块瘦身：根源不变 |
| D9 | 文档解析 | fork WeKnora docreader 进仓库，OCR 并入，自建镜像钉版本 | 继续用上游镜像：升级受制于人；Java 重写：版面与表格识别质量下降 |
| D10 | 前端拆分 | console 与 widget 两个应用，按运行环境拆 | 员工门户：终端用户是外部访客 |
| D11 | 后端拆分 | 一个 jar，部署分 api 与 worker 两种角色 | 按管理面与消费面拆两个服务：共用同一批域服务，拆开只换来共享代码或远程调用 |
| D12 | 营销形态 | 被动应答 | 主动触达：属于 CRM 或营销自动化系统 |
| D13 | 转人工 | 对接渠道原生接待与第三方客服系统 | 自建坐席：工作量与整个改造相当且难以差异化 |
| D14 | 域内分层 | 参照 infra-aia-customer 的包约定 | 取消 Repository：与团队写法不一致；DDD 四层：大部分域出现空层 |
| D15 | 对象模型 | 两条边界，BO 按需 | 全套 VO→BO→Entity：单入口接口多一层转发；不要 BO：Entity 泄漏跨域 |
| D16 | Spring Boot 版本 | 3.5.16 加 Jackson 2，版本随 Boot BOM（3.5.16 为 2.21.4） | Boot 4.1 加 Jackson 3：用户判断生态尾部支持不足，选择留在 3.x；3.3.13：商业支持也已于 2026-06-30 结束 |
| D17 | 检索引擎 | 保留 pgvector、Elasticsearch、Milvus 三种上线，其余 6 种旧代码改包名迁入但停用（不注册、不进下拉、不测试），未来启用时按 `spi` 补回并另行规划 | 9 种全上线：迁移与隔离证明工作量最大；只留两种：用户要求保留 Milvus 应对大规模向量；直接删除 6 种：补回时要重写 |
| D18 | PostgreSQL 内关键词检索 | PGroonga 替换 ParadeDB pg_search | 两者并存：同表两套全文索引，写入成本翻倍；现 ParadeDB 索引分词器固定为 `chinese_lindera`，多国家租户共表时其他语言召回失效 |
| D19 | 后台任务队列 | PostgreSQL 存储，JobRunr 开源版 | 统一 Redis Stream：入队与业务写库不同事务，重试与监控需自研；RabbitMQ：多一个中间件且一致性问题相同 |
| D20 | 认证与权限 | Sa-Token | Spring Security：自定义访客令牌与 API Key 需写 `AuthenticationProvider`，团队需另学一套；保留手写过滤器：权限规则散落，难以用测试表达。现状并未使用 Spring Security，是手写 `OncePerRequestFilter` 加路由策略表 |
| D21 | 客户身份范围 | 全平台统一身份，只打通身份，不共享数据 | 限定租户内：失去免重复授权、统一注销与平台级风控；共享数据：需租户组与单独同意的授权流程 |
| D22 | 表结构 | 重新设计，生成新 V1 基线 | 在旧 V1 上追加：ID 类型混用长期遗留 |
| D23 | 分阶段 | 第一阶段纯重构，界面零变化，删引擎为唯一例外；第二阶段做新能力 | 零例外：要多迁 6 套引擎再删掉；一阶段同做：端到端测试失去"与旧系统一致"这一判据 |
| D24 | Agent 循环 | 保留自研 agent-engine，不采用 agentscope-core 2.0.3 | 采用 agentscope：只能删掉循环骨架，5.10.1 的 41 项仍要自研并挂在外部循环上；SSE 事件序列随之改变，违反第一阶段"行为与旧系统一致" |
| D25 | 跨域读取 | 一律走属主域的 `api`，下级域需要上级数据时由上级实现下级声明的 Provider | 只读 QueryRepository 跨域 join：Modulith 看不到 SQL 依赖，表结构变更穿透到其他域；数据库视图：多一层 schema 契约，工具链都看不到 |
| D26 | MCP 协议客户端 | 官方 Java SDK `mcp-core` 的同步客户端，前提是半天验证通过：每一跳 SSRF 校验与按用户身份注入 OAuth 令牌都能接入 | 继续自研：要自行补齐取消通知、`list_changed`、进度、结构化输出、断线续传，并跟进每版协议 |
| D27 | OAuth 客户端 | 后台 OIDC 登录与 MCP 授权共用 Nimbus `oauth2-oidc-sdk` | 两处各自手写：同一套协议步骤写两遍，共 6,000 余行 |

## 4.3 分阶段

**第一阶段：纯重构。** 界面与交互和旧系统一致，验收依据是在旧系统上录制的界面端到端测试。范围：

1. 第 5 节的全部架构改造：模块、framework、域内分层、API 重设计、多租户行级安全、表结构、技术栈替换。
2. 后端错误提示改为按请求语言渲染。界面位置与交互不变，措辞可以不同。
3. PGroonga 替换 ParadeDB。用通用配置，不依赖知识库语言属性。
4. 现有 IM 与嵌入渠道合并为 channel 域，属于内部重组，界面不变。

唯一例外：6 种检索引擎停用，界面上只是向量库设置的下拉选项减少，交互不变。

**第二阶段：新能力。** 在新架构上按新规范开发：

1. 5.4 中的新域：customer、moderation、lead、handoff，以及 channel 新增的公众号、小程序客服、微信客服。
2. 管理员开场问题与欢迎语的多语言编辑与一键翻译（5.6 第 4 条）。
3. 知识库主语言属性，以及据此为 Elasticsearch、Milvus 选择分析器、查询端按语言分词（5.9 多语言检索第 2、3 条）。
4. 按访客限流、Token 预算、FAQ 命中免调大模型（5.7 第 6 条）。
5. 单租户部署开关（5.7 第 5 条）。
6. 挂件的手机端优先改版。
7. 语言取值顺序中的"客户偏好"一级。

第 5 节描述的是两个阶段完成后的目标结构，第二阶段的条目在正文中以【第二阶段】标注。

# 5. 目标结构

## 5.1 仓库布局

```
ragagent/
├── backend/                  Maven 多模块
│   ├── framework             基础设施层，吸收现有 platform
│   ├── model-runtime
│   ├── retrieval-engine
│   ├── agent-engine
│   └── app                   业务域与启动类
├── services/
│   └── docreader/            Python，文档解析与 OCR，proto 唯一来源
├── frontend/                 pnpm 工作区
│   ├── apps/console          品牌方管理控制台
│   ├── apps/widget           C 端挂件与 H5 全屏对话页
│   └── packages/             api-client（OpenAPI 生成）、i18n、ui
├── mcp-server/               Python，独立交付，跟随新 API 改造
├── deploy/
└── docs/
```

删除：`contract/`、`platform/`、`domains/`、`boot/`（内容迁入新模块后），`otlp-proto/`（改用 OpenTelemetry 官方 SDK），`scripts/` 下的守卫脚本与 baseline（由测试取代），`docreader/` 与 scripts/ocr-server.py（已迁至 services/docreader/ocr/server.py）（迁入 `services/docreader`）。

## 5.2 Maven 模块依赖

```
app ──► agent-engine ─────┐
 │                        ├──► model-runtime ──► framework
 ├──► retrieval-engine ───┘
 └──────────────────────────────────────────────► framework
```

1. 依赖单向。framework 不认识引擎，引擎不认识 app。
2. retrieval-engine 与 agent-engine 平级。Agent 的知识检索工具在 app 中实现。
3. 引擎对外只暴露 `api` 包（入口与 DTO）和 `spi` 包（引擎声明、app 实现的接口，例如向量库配置查询、知识库归属、技能目录）。内部包对 app 不可见，由 ArchUnit 测试守住。
4. 装配只在 app 的配置类里做。

contract 解散后的归属：

| 现 contract 包 | 去向 |
|---|---|
| `model.*` | model-runtime `api` |
| `retrieval.*`、`vectorstore.*` | retrieval-engine `api`，需要查业务库的放 `spi` |
| `agent.*`、`tool.*` | agent-engine `api` 与 `spi` |
| `error.*`、`event.Event`、`pagination`、`json` | framework |
| knowledge、session、wiki、memory 等只被业务域使用的包 | app 中所属域的 `api` 包 |

## 5.3 framework

按 furnace 的设计与 API 风格重写，注解名、配置前缀、用法与 furnace 保持一致。只做 ragagent 用得到的能力：

| 能力 | 做法 |
|---|---|
| 多租户 | MyBatis-Plus 租户插件，`@IgnoreTenant` 加切面；事务开始时 `SET LOCAL` 当前租户，供行级安全策略使用 |
| 多语言 | 请求头解析 Locale，`MessageSource` 读取仓库内的 `messages*.properties`；文案随版本发布，不做 Redis 热刷新与 Nacos |
| 日志 | logback 脱敏 Appender，业务异常码转换器 |
| MyBatis | 逻辑删除唯一键、SQL 追踪、字段加解密 `TypeHandler` |
| Redis | Redisson 分布式锁注解、两级缓存、失效广播总线 |
| 序列化 | Long 转字符串、`OffsetDateTime` 时区、大小写转换等 Jackson 模块 |
| 安全 | Sa-Token 多账号体系：后台用户、C 端访客、开放接口调用方各一个 `StpLogic`；权限用 `@SaCheckPermission` 或 `SaRouter` 路由规则；OIDC 用 Nimbus 实现客户端；CORS 与安全响应头在 framework 中显式配置；渠道回调验签由各渠道 Adapter 负责 |
| Web | 统一响应外壳、分类异常处理器、可重复读请求、链路上下文过滤器 |
| OSS | 按 furnace `OssTemplate` 的接口形状收敛现有五种存储后端 |
| 脱敏 | 注解驱动的 Jackson `SerializerModifier` |

数据库字段加解密不采用 furnace 的 ShardingSphere 方案。ShardingSphere 在 JDBC 层解析所有 SQL，pgvector 的 `<=>` 与 jsonb 操作符有解析失败风险。改用 MyBatis `TypeHandler`，只作用于标注的字段。

**运行时配置模型**。能力分两类：

1. 进程级能力，启动时确定：数据库与 Redis 连接、日志、Jackson、安全过滤器链、基础 `MessageSource`。采用 `@ConfigurationProperties`。
2. 租户级能力，运行时从库里读取：OSS、模型、向量库、网页搜索、渠道、租户开关与配额。不注册为单例 Bean，统一为三件套：配置读取接口、按租户与配置版本缓存的客户端注册表、配置变更时经 Redis 广播失效。使用方式为 `ossClients.forTenant(tenantId)`。

配置覆盖顺序：yml 默认值，被 `system_settings` 覆盖，再被租户配置覆盖。

## 5.4 app 的业务域

域分三级，只允许上级依赖下级。同级互相需要时，依次考虑下沉到基础域、改用事件、合并。

| 级别 | 域 | 说明 |
|---|---|---|
| 基础域 | tenant、auth、system、storage、model、vectorstore、websearch、audit | system 吸收 initialization |
| 业务域 | knowledge、wiki、datasource、agent、mcp、memory、favorite、customer、channel、moderation、lead、handoff | agent 即现 agentmanagement；channel 合并现 im 与 embedchannel；后四个为新域 |
| 编排域 | session、agenttools | 会话问答编排知识、Agent、客户、内容安全；agenttools 为 Agent 工具的实现 |

新增 C 端域，均为【第二阶段】：

1. **customer**：全平台统一身份，只打通身份，不共享数据。
   - 全局表 `passport` 只存身份标识：手机号加密存储加一份哈希用于查找、unionid、各渠道 openid 与品牌方用户 ID 的映射。它不带 `tenant_id`、不挂行级安全，只能经客户域的系统通道（`@IgnoreTenant`）访问，其他域与租户接口都不能直接读取。
   - 租户表 `customer` 关联 `passport`，存本租户的画像、偏好（含语言偏好）、满意度反馈，受行级安全保护。对话与留资分别归 session、lead 域，同样按租户隔离。
   - 任何接口都不暴露"某人还是哪些其他租户的客户"。
   - 用户在一个租户授权过的手机号，到其他租户无需再次授权；平台级风控可以按 `passport` 识别恶意用户。
   - 注销分两级：租户级只删本租户的 `customer` 及其数据；平台级删除 `passport` 并级联删除所有租户的客户数据，均留注销记录。
   - 身份来源是品牌方网站传入的签名令牌，或渠道给的 openid、unionid，不做密码登录。
   - 跨租户共享客户数据不在本次范围。以后若有集团多品牌联合营销需求，在此之上增加租户组与单独同意的授权记录。
2. **channel**：现有 IM 与嵌入渠道合并在第一阶段完成。【第二阶段】新增公众号、小程序客服、微信客服。现有个人微信机器人渠道（`ilinkai.weixin.qq.com`）不用于营销场景。
3. **moderation**：输入与输出的内容安全审核，接口化以对接云厂商内容安全服务，另带本地敏感词与广告法极限词表。
4. **lead**：留资，记录客户主动留下的联系方式与意向。
5. **handoff**：转人工扩展接口。微信客服、公众号走渠道原生接待；网页挂件转给七鱼、智齿等第三方。转交时带上对话记录与智能体摘要。

商品、活动等营销数据留在原系统，通过 Agent 工具或 MCP 查询，不复制进 ragagent。

## 5.5 域内结构

每个域固定以下包，复杂域在 `service` 下按子功能分子包：

```
<domain>/
├── api/          对其他域公开的接口与 BO，Spring Modulith 只允许访问这里
├── controller/   出入参为 XxxReqVo、XxxRespVo
├── service/      用例与事务
├── repository/   按聚合根划分
├── mapper/       BaseMapper，自定义 SQL 写在 XML
├── entity/
├── bo/           XxxInBo、XxxOutBo，只在需要时出现
├── vo/
└── assembler/    MapStruct
```

规则，主要吸收自 three-layer-arch.md：

1. 控制器只做绑定、`@Valid` 校验、调用一个 Service。
2. 对象模型只承认两条真实边界：HTTP 边界用 VO，Entity 不出域。BO 只出现在 `api` 包和多入口的用例上，例如会话问答被控制台、挂件、渠道、开放接口、Agent 工具共同调用。单入口的增删改查由 Service 直接收发 VO。
3. Repository 按聚合根划分。单表聚合继承 `ServiceImpl`，多表聚合组合注入 Mapper。出入参用 Entity，查询条件用 Condition 对象，不做缓存，注解用 `@Service`。
4. 每张表只有一个写属主。跨聚合只存对方 ID，跨表 join 只放在只读 QueryRepository。
5. 事务内不做外部调用。LLM、Embedding、向量库、渠道、第三方客服都算外部调用。提交后动作用 `AFTER_COMMIT`，可靠投递用 Spring Modulith 的事件发布登记表。
6. Entity 用 `@Getter`、`@Setter`，时间用 `OffsetDateTime`，查询用 `LambdaQueryWrapper`。
7. 取消 Ops、Support、Helper、Facade 后缀，只有对接外部系统的类用 Adapter。
8. 语言码与时区不进 VO，由框架从请求头读取。

## 5.6 API 与错误

1. URL 分三组：管理面 `/api/admin/v1`，C 端 `/api/v1`，开放接口 `/api/open/v1`，分别对应 Sa-Token 的三个账号体系。私有部署要求管理端只在内网时，在网关屏蔽 admin 前缀。
2. 请求体一律用 VO 加 `@Valid`，取消 rawBody 手写绑定与 Go 兼容工具。
3. 业务异常统一为错误码加 i18n key，由 `MessageSource` 按请求语言渲染。第一阶段语言取值顺序为 `Accept-Language`、`zh-CN`；【第二阶段】在最前面加上客户偏好。语种与前端现有 7 种一致：zh-CN、zh-HK、en-US、es-ES、pt-BR、ru-RU、th-TH。
4. 【第二阶段】内置智能体、管理员开场问题、欢迎语共用一种多语言结构 `{default, zh-CN, en-US…}`，缺失回落 default。管理端提供一键翻译，保存时调用一次 LLM 生成草稿。知识库抽取的问题保持原文。第一阶段维持现有行为：内置智能体已按此结构存储，管理员配置项仍为单语言。
5. 前端类型 100% 由 springdoc 导出的 OpenAPI 生成。
6. 流式接口用 Spring MVC `SseEmitter`，不手工拼帧，不用 WebFlux。控制器返回 emitter 即释放请求线程，流泵在独立虚拟线程中串行调用 `send`；断连由 `onCompletion`、`onError`、`onTimeout` 回调感知，超时在构造 emitter 时统一配置。"引擎写 `StreamManager`、流泵增量读取推送"的模型保留，断线续接与多副本依赖它。删除 `SseFrameWriter`、`ServletSseSink` 与 `JacksonConfig` 中为对齐 Go 版而开的全局 HTML 转义。前端 `fetch-event-source`、mcp-server、接口调试页均按 SSE 规范解析，帧格式的细微变化不影响它们。

## 5.7 多租户

1. 共享表加 `tenant_id`。应用层由租户插件自动注入条件，Java 代码不手写。
2. 所有租户表开启 PostgreSQL 行级安全，事务开始时 `SET LOCAL` 当前租户。插件漏掉的 SQL，包括 `JdbcTemplate`、原生 SQL、pgvector 检索，由数据库拦截。
3. 跨租户操作只走 `@IgnoreTenant` 标注的系统通道：认证、后台调度、跨空间成员关系、客户域对全局 `passport` 表的读写。
4. 不做跨租户共享知识库。空间分享相关的 7 张表已于 2026-09-29 随功能裁剪移除，见 `V1__baseline.sql` 头部注释，本次不恢复。
5. 【第二阶段】单租户部署开关：关闭租户创建与切换入口。
6. 【第二阶段】按租户配额与限流：Token 预算、按访客限流（现有按渠道限流保留）、高频问题命中 FAQ 时不调用大模型。
7. 外部检索引擎 Elasticsearch、Milvus 不受行级安全保护，隔离靠知识库归属校验。这一点在 retrieval-engine 的 `spi` 中显式表达。
8. 删除 `TenantFilterGuard`、72 条白名单与全部手写 `tenant_id` 条件。

## 5.7.1 表结构

重新设计，生成新的 V1 基线，不在现有 `V1__baseline.sql` 上追加。

1. 主键统一为 bigint 雪花 ID（MyBatis-Plus `ASSIGN_ID`），序列化为字符串，避免前端精度丢失。取代现有 integer 与 36 位 uuid 字符串混用。
2. 所有租户表的 `tenant_id` 为 bigint，并挂行级安全策略。
3. 时间字段统一为 `timestamptz`，对应 `OffsetDateTime`。
4. 软删字段统一，配合逻辑删除唯一键。
5. 每个域迁移时按聚合根与写属主写出本域 DDL。迁移期间各域脚本分开存放，上线前合并为一份 V1。系统未上线，无旧数据迁移。

## 5.8 部署

一个后端镜像，部署角色两种：`api` 只处理 HTTP；`worker` 跑文档入库、数据源同步、渠道长连接。小规模部署一个实例承担两个角色。另有 docreader、console 与 widget 的前端镜像、PostgreSQL、Redis。

## 5.9 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | JDK 21 | 虚拟线程用于管线并发与工具并行 |
| 框架 | Spring Boot 3.5.16 | 3.x 最后一条版本线，商业支持至 2032-06-30，日后迁 Boot 4 跨度最小。开源补丁已于 2026-06-30 停止 |
| JSON | Jackson 2，由 Boot BOM 管理，3.5.16 为 2.21.4 | 与 Boot 3.5 配套，不单独覆盖版本 |
| 模块边界 | Spring Modulith 1.4、ArchUnit | Modulith 1.4 对应 Boot 3.5 |
| 持久化 | MyBatis-Plus 3.5.x Boot 3 starter、Flyway | 沿用 |
| 对象转换 | MapStruct | 团队工程一致 |
| Redis | Redisson，`redisson-spring-data-35` | 分布式锁与两级缓存 |
| 接口文档 | springdoc 2.8.x | 前端 codegen 的来源 |
| gRPC 客户端 | grpc-java 直接使用 | 现用的 `net.devh:grpc-spring-boot-starter` 已于 2024-04 停更；docreader 客户端只需一个 channel，不引入 starter |
| 测试 | JUnit 5、AssertJ、Testcontainers PostgreSQL | 行级安全与 pgvector 必须在真实 PostgreSQL 上测 |
| 数据库镜像 | 自建：PostgreSQL 17 加 pgvector 加 PGroonga | 取代 ParadeDB 镜像 |
| 检索引擎 | pgvector 加 PGroonga（默认）、Elasticsearch、Milvus | 默认引擎与业务数据同库，受行级安全保护；ES 面向已有集群或大数据量的客户；Milvus 面向千万级以上向量 |
| 后台任务 | JobRunr 开源版，存储在 PostgreSQL | 入队与业务写库同事务，不丢任务；自带重试、失败列表、定时任务与管理面板。worker 执行前按任务参数里的租户 ID 设置租户上下文。取代现有 7 套自研队列。Redis 只保留 SSE 流分发、分布式锁与缓存 |
| 认证与权限 | Sa-Token，`sa-token-spring-boot3-starter` | 与 furnace 一致；多账号体系对应三类身份 |
| 可观测 | Langfuse 记录 LLM 调用链路；OpenTelemetry 官方 SDK 导出追踪；Actuator 加 Micrometer 暴露指标 | 沿用现有的 Langfuse 装饰器与指标面，删除自维护的 `otlp-proto` |
| 前端框架 | Vue 3.5、Vite 7、TypeScript、Pinia、vue-i18n、TDesign | 沿用 |
| 前端工作区 | pnpm workspace | console 与 widget 共享 `packages/` |
| API 客户端 | openapi-typescript 生成类型，openapi-fetch 调用 | 取代手写的 axios 封装与 276 个内联类型；只生成类型，运行时依赖最小 |
| 挂件嵌入 | 沿用现有方式：宿主页加载 `ragagent-widget.js`，以 iframe 承载对话页，经 postMessage 通信 | 天然隔离宿主页的样式、脚本与 CSP。【第二阶段】挂件页面改为手机端优先 |

**多语言检索**。系统面向多个国家，主语言各不相同：

1. PGroonga 用 bigram 加 NFKC 归一化，同一索引混存多语言，不需要先判断语言。日语可选装 MeCab。
2. 【第二阶段】知识库增加主语言属性，默认取租户默认语言。Elasticsearch 按它为每个知识库的索引选择分析器，Milvus 按它选择 BM25 分词器。
3. 【第二阶段】查询端分词按语言选择，取代现在固定使用 jieba 的 `QueryTokenizer`。第一阶段 pgvector 引擎的关键词查询交给 PGroonga 自身的查询解析。
4. 融合沿用 RRF，只看名次不看分数，换用 PGroonga 打分不影响融合逻辑。

Jackson 2 与第三方 SDK 共存不是问题，本项目统一用 Jackson 2，无需防混用。

## 5.10 agent-engine：保留自研，不换 agentscope

结论见 D24。spike 报告见 `docs/optimization/spikes/2026-11-09-agentscope-java-spike-result.md`。spike 的 5 项检查中，租户隔离、按租户配置模型、思考块与流式工具调用都通过，取消部分通过。否决不是因为检查项，而是因为 agentscope-core 2.0.3 只提供 ReAct 骨架（推理循环、工具调用、事件流）。现有 agent-engine 的主要代码量是在真实模型和真实租户下积累的工程机制，替换循环后这些机制仍要自研，且要挂到外部循环的扩展点上。

下面三张表是这一结论的依据，也是 agent-engine 内部整理时不能丢的能力清单。第一类在 agentscope 中没有对应物；第二类有对应物但不满足本项目约束；第三类是 agentscope 能接管的部分，也就是替换的全部收益。

### 5.10.1 必须自行实现（agentscope 无对应能力）

| 能力 | 现有实现 | 不做会发生什么 |
|---|---|---|
| 上下文压缩与摘要 | `compaction/` 10 个类，1,900 行 | 长会话直接报溢出错误。agentscope 只在 `maxIters` 用尽时做一次总结，不是上下文窗口管理 |
| 压缩阈值与预算计算 | `CompactionSettings`、`ContextBudget` | 无阈值判断，不知道何时该压 |
| 切点选择 | `CutPoint`，含轮次切分与 tool 消息不切开 | 切在 tool 调用与结果之间，provider 直接拒绝 |
| 摘要注入与增量更新 | `CompactionPreparation`、`Compactor` | 摘要的摘要递归退化；摘要混进 system 段 |
| 溢出错误识别 | `CompactionOverflow`，24 条厂商正则 + 5 条反向排除 | 把限流误判成溢出，或溢出时原样重试到耗尽 |
| 工具结果裁剪兜底 | `ContextBudget.trimToolResultsToBudget` | 单条 10 万字符的工具结果把窗口一次打满 |
| Token 估算 | `TokenEstimator`（jtokkit）+ usage 基线增量 | 每轮全量估算，或压缩判断完全缺失 |
| 估算漂移告警 | `ContextDebugEmitter`、`ContextDiagnostics` | 估算与账单长期不一致却无人知晓 |
| 运行中插话（steer） | `SteerIntake`、`SteerSink`、超预算透支一轮 | 用户中途补充要求只能等下一轮；产品交互缺失 |
| 工具参数类型矫正 | `ParamCaster` | 模型写 `"true"`/`"5"` 的调用全部失败。agentscope 只做严格 schema 校验，不做矫正 |
| 坏 JSON 修复 | `JsonRepair` 294 行 | 尾逗号、非法转义、流式残渣导致整批工具调用失败 |
| 截断识别 | `JsonRepair.RepairResult.truncated` + `ActPhase` 拒执 | 把半截参数补齐后执行写操作，写出半个文件还报成功 |
| 工具调用 ID 归一 | `NormalizeToolCallId` | 空 ID / 超长 ID / 中文 ID 破坏事件关联与 provider 配对 |
| 消息清洗 | `MessageSanitizer` | 连续同角色、孤儿 tool 消息、空内容被 provider 拒绝 |
| 流式 think 标签拆分 | `ThinkStreamSplitter`（跨 chunk 边界） | `<thi` + `nk>` 分片时思考内容漏进答案区 |
| 空回复重试 | `ReActIteration` 内 2 次 nudge | agentscope 有 `EMPTY_RESPONSE_REMINDER`，但只在"正文全在 reasoning 通道"时触发，不覆盖真正的空回复 |
| 重复内容卡死检测 | 同内容连续 2 轮即收束 | 模型原地打转直到迭代上限 |
| `length` 停止时拒绝全部工具调用 | `ActPhase.failTruncatedToolCalls` | 参数被从中间切断仍执行 |
| `content_filter` 终态处理 | `ObservePhase` 固定拒答文案 | 反复重试被安全策略拦截的响应 |
| 迭代上限兜底合成 | `FinalizePhase.handleMaxIterations` | 走到上限直接空手返回，用户看到空白 |
| LLM 失败后从工具结果合成 | `ThinkPhase` 合成路径 | 已经花掉的工具调用全部作废 |
| 工具执行中断语义 | `ToolBatchExecutor.joinAllCancellingOnInterrupt` | 子线程与下一轮的写并发，跨越写屏障 |
| 取消→关闭模型流 | `ThinkPhase` 分片级检查 + `stream.close()` | 停止消费不等于取消模型，连接继续读、并发许可继续被占 |
| 停顿看门狗 | 只限空闲不限总时长 | 半开连接挂死整轮；按总时长杀又会丢掉大参数调用 |
| 提示词分段与占位符 | `AgentPrompts` 10 段表单一声明、`GroundingPrompt` 按实际工具渲染 | 段顺序漂移；提示词引用未注册的工具 |
| runtimeContext 块 | `ConversationAssembler`，含本地+UTC 双时间、钉住文档范围 | 模型拿不到本轮时间与文档范围；跨时区相对时间算错 |
| 历史检索结果脱敏 | `ConversationAssembler` 把旧 KB 工具结果替换为省略提示 | 模型复用上一轮的过期检索数据当证据 |
| Level 1/2/3 技能披露 | `AgentPrompts` 目录段 + `PinnedSkillInstructions` | 全量技能正文塞进提示词，或技能无法被点名注入 |
| 技能目录与白名单 | `skills/` 7 个类，含 SKILL.md 解析、frontmatter 容错、租户可见性、引用保护 | 改名/删除让 agent 静默失去技能；跨租户技能泄露 |
| 工具并发策略 | `ExecutionPolicy` 白名单 + 读写屏障 + 并发上限 8 | 写操作与读并发，结果不可复现 |
| 结果槽非空保证 | `ToolBatchExecutor` 四种情况都落失败占位 | 收集循环 NPE 炸掉整轮 |
| MCP 工具每轮刷新与清理 | `refreshMcpTools` + `finally cleanup` | 上一轮新增的 MCP 工具下一轮不可见；异常路径资源泄漏 |
| MCP 目录鉴权先于 schema 校验 | `ToolRegistry` 目录守卫 | 参数错误的 details 暴露其他主体的工具定义 |
| 工具下线重定向 | `ToolDefinitions.retiredToolReplacement` | 模型调用已下线工具只看到裸 not found |
| 工具输出预算与自报 | `OutputLimitProvider` | 工具已裁剪的输出被通用限额二次截断 |
| 事件发射顺序契约 | 错误帧先于终态帧、终态恰好一次、Done 延后到 steer drain 之后、复用 answerId | 前端渲染两遍、错误帧丢失、轮次误判空闲 |
| 事件载荷键序稳定 | `deepSortedMap` + UTF-8 字节序 | 每次请求 payload 字节不同，回放对不上，提示词缓存失效 |
| 持久化载荷剥离 | `sanitizeToolDataForPersist` | base64 与 chunk 列表进 SSE/DB 撑爆 payload |

### 5.10.2 有替代物但不满足约束（必须适配）

| 能力 | agentscope 现状 | 本项目约束 | 处理方式 |
|---|---|---|---|
| 会话记忆 | `Memory` 接口 + `InMemoryMemory`，只有 add/get/clear | 每轮从 DB 重建历史，引擎跨轮无状态（`AgentEngine` 类注释） | 实现一个只读 Memory 适配器，把外部传入的 `llmContext` 原样返回，禁用写入 |
| 上下文窗口感知 | `Model.getContextWindowSize()` 默认返回 0 | 需要真实窗口值驱动压缩阈值 | 适配器从模型行配置读，或接入 `ModelContextWindows` 内置表 |
| 工具 schema 校验 | networknt 严格校验 | 需要先矫正再校验；且错误文案要进 SSE 且带错误码前缀 | 在工具适配层前置 `ParamCaster`，错误文案走 `BizException.wireText` |
| HTTP 传输 | 自带 `HttpTransportFactory` 单例 | 必须走 model-runtime（26 家厂商适配、SSRF、并发闸门、langfuse） | 不实现 agentscope 的 Model 里的 HTTP，只用 `LlmChatClient` 适配（spike 已验证可行） |
| 模型注册 | `ModelRegistry` 静态缓存，键含 apiKey 的 sha256，无淘汰 | 按租户配置，配置可变 | 不用 `builder().model(String)`，只传 `Model` 实例（spike 已验证） |
| 状态持久化 | `AgentStateStore` + 文件/JSON 实现 | 会话历史在 PostgreSQL，由域层管理 | 不配 `stateStore`，只用无状态调用 |
| 取消 | Flux dispose → 停止消费上游 | 需要模型流真正关闭、工具线程协作退出 | 模型侧用 `ChatStream.close()` 接上（spike 已验）；工具侧沿用现有 `ToolCancellation` 机制 |
| 工具审批与 MCP OAuth | 有 `REQUIRE_USER_CONFIRM` 事件族与 `PermissionEngine` | 已有 `ApprovalBridge` 与 `McpOAuthPromptSupport` 一套完整实现，接线在 domains | 二选一，不能两套并存。倾向保留现有实现，agentscope 的 HITL 事件映射到现有事件，避免前端重做 |
| 技能 | `skill/` 包有 `SkillBox`、`SkillRegistry`、`DynamicSkillMiddleware`，走文件系统仓库 | 现有技能在 `skills` 表，有租户可见性与引用保护 | 保留现有 `skills/` 子系统与提示词段，不用 agentscope 的 skill 包 |

### 5.10.3 可直接删除的自研部分

| 现在的东西 | 由谁接管 |
|---|---|
| `ReActIteration` 主循环骨架 | `ReActAgent` |
| `ThinkPhase` 的模型调用与重试编排 | `ReActAgent.doCall` + `ExecutionConfig` |
| `ActPhase` 的工具调用分发 | `Toolkit` + `ToolExecutor` |
| 工具注册表的基本增删 | `Toolkit` |
| `ObservePhase` 的终态裁决 | `ReActAgent` 的 `isFinished`（但 `content_filter` 分支需保留，见 5.10.1） |
| `AgentState` 的轮次/步骤容器 | `Msg` + `AgentEvent`（但 `agent_steps` 的持久化 jsonb 形状不变） |

### 5.10.4 引擎整理的验收

第 6 节第 3 步整理 agent-engine 内部时，5.10.1 每一项都要有对应用例，且用例先在冻结点的旧引擎上跑绿。分三组：

1. **回归对拍**。同一批问题在旧引擎与新引擎上跑，逐条比对 SSE 事件序列（事件类型、顺序、载荷键序）。差异必须能解释。
2. **故障注入**。用 `stub-llm-server.py` 的 `early-error`、`early-close` 场景，加上 spike 里扩展的 `slow`、`slowtool` 场景，验证看门狗、空回复、坏 JSON、截断、取消这些路径。
3. **长会话**。构造超过上下文窗口的会话，验证压缩触发、切点、摘要增量、溢出恢复各一次，且 `contextCompacted` 事件带齐原因与前后 token。

### 5.10.5 否决理由与重新评估条件

1. 收益小。5.10.3 能删掉的只有循环骨架与工具分发；5.10.1 的 41 项要在 agentscope 的 Hook 与中间件上重写，steer、事件顺序契约、取消关闭模型流都要侵入循环内部。
2. 违反第一阶段约束。D23 要求第一阶段行为与旧系统一致，验收靠回归对拍。换循环后事件序列必然变化，回归对拍失去判据。
3. 编程模型不一致。agentscope 基于 Reactor，现有引擎与 framework 基于虚拟线程加阻塞调用。引入后租户上下文要额外走 context-propagation，调试要读第三方源码。
4. 取消能力倒退。现有 `ToolCancellation`、`ToolRegistry` 执行前检查、`ToolBatchExecutor` 中断收束会丢失或要重接。

spike 中可复用的结论保留：model-runtime 流终态是两帧 done，先工具调用后用量，任何新的消费方都要排空尾帧；行级安全在事务级 `set_config` 之后的查询会报 `invalid input syntax for type bigint: ""`，framework 要把它转成明确的租户上下文缺失错误。

第二阶段之后满足以下任一条件时重新评估，不在本次范围：

1. 产品砍掉技能、MCP 与中途插话，5.10.1 中 60% 以上不再需要。
2. agentscope 把上下文压缩、消息清洗、参数矫正纳入 core。

## 5.11 数据访问层

**现状实测**：49 个 Mapper，53 个 Repository 共 12,780 行，561 个公开方法。SQL 分散在四处：注解 SQL 281 条，其中 72 条 `<script>`；Wrapper 246 处；`JdbcTemplate` 14 个文件；`JdbcClient` 3 个文件。注解 SQL 中手写 `tenant_id` 207 处、`deleted_at IS NULL` 117 处。11 个非 Repository 类直接注入 Mapper。`knowledge_bases` 被 6 个域直接查询，storage 域直接 `UPDATE tenants`。

**"MyBatis-Plus 不支持 PostgreSQL"的说法大部分不成立**。代码注释给出的理由逐条核对如下：

| 注释里的理由 | 真实原因 | 新做法 |
|---|---|---|
| Wrapper 的 `set()` 不套实体 typeHandler，jsonb 列只能手写全字段 UPDATE | MyBatis 通用行为，与 PostgreSQL 无关；且全字段 UPDATE 本可以用 `updateById` | 默认 `updateById`；部分更新用三参 `set` 或 XML |
| 自定义 `@Select` 不套实体 typeHandler | 注解 SQL 没有复用 resultMap | 实体开 `autoResultMap`，XML 引用同一个 resultMap |
| null 字段被省略，落到库默认值 | `insertStrategy` 默认 `NOT_NULL` | 全局配置字段策略，个别列显式标注 |
| jsonb 数组判断写成 `CAST AS VARCHAR` 加 `LIKE` | 为了让同一条 SQL 在 H2 上也能跑 | 测试改用 Testcontainers PostgreSQL，去掉 H2 与 `DatabaseDialects` 的 14 处方言分支 |
| 返回 `Map<String, Object>`、全字段手写 | 从 Go 版逐行翻译，追求逐字节一致 | 返回实体或投影对象 |

确实需要手写 SQL 的只有这几类：`ON CONFLICT` 写入或更新（MyBatis-Plus 没有 upsert）、`FOR UPDATE` 与 `SKIP LOCKED`、jsonb 运算符查询、pgvector 与 PGroonga 检索、统计聚合。

**规则**：

1. 写入默认用 `ServiceImpl` 的 `save`、`updateById`、`removeById`。只有上表最后一段列出的场景才手写 SQL，并且一律写在 Mapper XML 中，主码注解 SQL 清零。
2. 简单查询用 `LambdaQueryWrapper`，只能出现在 Repository 内部。
3. `JdbcTemplate` 只出现在 retrieval-engine 的 pgvector 检索中。
4. 租户条件由 `TenantLineInnerInterceptor` 注入，并由行级安全兜底；软删由全局 `@TableLogic` 处理。SQL 中不手写这两类条件。
5. 自定义方法的命名表达业务含义，例如 `markParsing`、`claimNextPending`、`archiveExpired`。不用 `updatePartial`、`updateRow`、`updateByIdNonNull`、`setOwnerId`、`findByIdAny` 这类描述 SQL 动作的名字。单表聚合继承来的通用增删改查不在此限。
6. 出入参用 Entity 或 Condition，不返回 `Map<String, Object>`。
7. 事务放在 Service 的公开方法上。行锁由 Repository 提供 `lockXxx`，Service 在事务内调用。`MemoryTxTemplate` 这类专为绕开自调用而建的 bean 不再出现。
8. 跨域读取一律走属主域的 `api`，不跨域 join，不建跨域视图。
9. 改名：retrieval-engine 的 `XxxRetrieveRepository` 改为 `XxxVectorEngine`，Redis 状态类改为 `XxxCacheRepository`；任务进度类的 Store 随 JobRunr 一起删除。
10. `DatabaseQueryTool` 执行模型生成的 SQL，改用只读数据库账号，加行级安全与 `statement_timeout`，删除字符串拼接的租户条件。
11. ArchUnit 守四条：Mapper 只被 Repository 引用；主码无 `@Select`、`@Update`、`@Insert`、`@Delete`；`JdbcTemplate` 只在白名单包；跨域不引用对方的 `repository`、`mapper`、`entity`。

**跨域查询 `knowledge_bases` 的去向**：

| 域 | 目的 | 新做法 |
|---|---|---|
| model | 删除模型前，列出哪些知识库引用了它，作为"模型被占用"错误的详情 | 基础域不能依赖业务域。model 在 `api` 中声明 `ModelReferenceProvider`，由 knowledge、agent 实现，model 收集所有实现的结果 |
| vectorstore | 删除向量库前，统计还绑定着的知识库数 | 同上，用 `VectorStoreReferenceProvider` |
| storage | 删除存储后端前统计绑定数；按资源绑定判定资源属于哪个知识库 | 绑定数同上，用 `StorageReferenceProvider`。资源归属判定移到 knowledge 的 `api`，storage 只存绑定关系 |
| storage | 读写 `tenants.default_storage_backend_id` | 改为调用 tenant 的 `api` |
| agent | 推荐问题时，按知识库 ID 查所属租户再分组 | 这是共享知识库时代的残留。共享已删除，行级安全保证只能读到本租户，改为调用 knowledge 的 `api` 过滤掉不存在的 ID |
| session、agenttools | Agent 的 grep 工具直接 join `chunks` 与 `knowledges` 做正则检索；`DatabaseQueryTool` 的提示词里有示例 SQL | grep 检索移到 knowledge 的 `api`；提示词示例不是代码层面的依赖，保持不变 |

## 5.12 MCP

**现状实测**：`mcp` 包 170 个文件、18,111 行，超过 agent-engine。协议客户端（3,413 行）、OAuth 客户端（3,647 行）、工具包装（3,308 行）、审批门（1,622 行）全部自研。声明协议版本 `2025-11-25`，只实现 `initialize`、`tools/list`、`tools/call`、`resources/list`、`resources/read`，没有取消通知、`list_changed`、进度、结构化输出、断线续传。

**问题**：

1. 用户停止后只中断本地线程，服务端不知道，工具继续执行。
2. 审批门 `Gate.requestAndWait` 阻塞线程等人工决定，等待者只在发起实例的内存里。重启即丢；跨实例靠 Redis Pub/Sub 加回复频道与 nonce，另有环境变量控制的 fail-open 开关。
3. `McpHttp` 用 `static volatile SsrfGuard` 加 setter，`McpExposure` 全是静态方法；`McpClientManager` 不受 Spring 管理，手写读写锁与平台线程清理。
4. 职责错位：协议客户端是外部适配器，工具包装属于 agenttools，mcp 域真正的职责只有服务配置、凭据与令牌。
5. stdio 类型、列与 DTO 字段保留，传输层永久拒绝。

**目标**：

1. mcp 域只管服务配置、凭据、OAuth 令牌与审批记录。协议与 OAuth 客户端放 `mcp/adapter`；工具包装、目录与发现工具迁入 agenttools。
2. 协议客户端用官方 SDK 同步客户端（D26）。开工前做半天验证：SSRF 校验能否作用到每一跳重定向；OAuth 令牌能否按用户身份逐请求注入。任一项不通过，退回自研并补齐缺口。SDK 内部依赖 Reactor，对外只用阻塞 API，不与 D24 冲突。
3. 用户停止时向服务端发 `notifications/cancelled`。
4. OAuth 用 Nimbus（D27），自研部分只留状态存储与回调接口。验证时一并确认 Nimbus 对受保护资源元数据（RFC 9728）的支持，不支持的部分在适配层补齐。
5. 审批改为持久化加事件驱动：待审批写入 `mcp_tool_approvals`，本轮挂起不占线程；用户决定后经 steer 通道注入运行中的轮次，轮次已结束则开后续轮次继续。删除 Redis Pub/Sub 与 fail-open 开关。审批卡片与按钮的界面、交互不变。
6. SSRF 校验、连接池改为 Spring Bean。连接池用 Caffeine，键为服务 ID、用户身份、配置版本，过期回调里关闭连接。
7. 删除 stdio 的字段与列。控制台若有该选项，保留为不可选，界面不变。

# 6. 迁移路径

**main 分支冻结**：重建开始后 main 停止功能开发，只修阻断性 bug。每个 bug 修复同时登记到迁移清单，迁到对应域时一并带入。各域以冻结点的代码为迁移依据。

1. **骨架**：五模块、framework、Testcontainers、springdoc 与前端 codegen 流水线、Spring Modulith 与 ArchUnit 测试。
2. **安全网**：
   - 界面端到端测试：在冻结点的旧系统上，用 Playwright 录制控制台与挂件的主要界面流程，作为第一阶段的验收依据。新系统上同一组流程的界面与行为必须一致，错误提示措辞除外。
   - 新 API 的端到端冒烟测试，覆盖登录、建知识库、上传文档、入库、问答、挂件访客对话。
   - 检索回归集：固定一组问题与期望命中的分块，迁移前在旧系统上录基线，迁移后对比召回。这是迁移用的测试资产，不是产品功能。
3. **引擎迁入**：分两步。先改包名搬入，contract 类型按 5.2 归位，补 `api` 与 `spi`，此时行为不变。检索回归集基线就位后，再按 5.5 的命名与对象规则整理引擎内部：retrieval-engine 有 23 个 Ops、Support 类，model-runtime 有 4 个，agent-engine 有 1 个。检索 SQL 里服务于索引命中的 `tenant_id`、`knowledge_base_id` 谓词保留，它们是查询条件，不算手写隔离；隔离仍由行级安全兜底。agent-engine 的内部整理以 5.10.4 的三组用例为门槛，5.10.1 的能力一项不丢。
4. **按域迁入**：基础域，业务域，编排域。每个域重写控制器、Service、Repository，前端对应页面只替换 API 调用层，组件与交互不动。旧代码只读参考。
5. **外围**：docreader 收进仓库并合并 OCR；mcp-server 改用新 API；删除旧模块、守卫脚本与 baseline。第一阶段到此结束。
6. **第二阶段**：按 4.3 的清单在新架构上开发。

每个域迁完的验收：Spring Modulith 校验通过、该域相关的界面端到端测试与 API 冒烟通过、检索回归不劣于基线（涉及检索的域）。

# 7. 验收标准

1. 仓库中不存在 contract 模块，主码无 Ops、Support、Helper、Facade 后缀类。
2. 主码无 `@RequestBody String`、无 Go 兼容工具方法。
3. app 主码无手写 `tenant_id` 查询条件，所有租户表开启行级安全，并有跨租户访问被拒的测试。retrieval-engine 中服务于索引的谓词例外，见第 6 节第 3 步。
4. 所有业务异常带错误码与 i18n key，后端 messages 覆盖前端同等语种。译文由机器翻译出初稿，经人工校对后入库。
5. 前端 API 类型全部由 OpenAPI 生成。
6. 架构规则全部由 Java 测试表达，`scripts/check-*.py` 清零。
7. 检索回归集召回不低于迁移前基线，端到端冒烟全部通过。
8. 第一阶段：旧系统上录制的界面端到端测试在新系统上全部通过。

# 8. 留给实施者的决定

1. 各域内部的具体类划分与子包。
2. Spring Modulith、Testcontainers、MapStruct、Redisson、JobRunr、Sa-Token 的具体版本，以兼容 Boot 3.5.16 为准。
3. 检索回归集与界面端到端测试的具体用例与规模。
4. 内容安全对接哪家云厂商，第三方客服首个对接哪家。

# 9. 本文中由 Claude 决定、需用户核对的事项

1. retrieval-engine 直接依赖 model-runtime。替代做法是 retrieval-engine 在 `spi` 声明 Embedding 接口、由 app 实现，代价是多一层转发。
2. 域的三级划分与新域命名（5.4）。
3. URL 三组前缀（5.6 第 1 条）。
4. 检索回归集作为迁移安全网。用户此前否决了"评测"产品功能，这里只作为测试资产。
5. 配置覆盖顺序与租户级能力三件套（5.3）。
6. 删除 `otlp-proto/`，改用 OpenTelemetry 官方 SDK。
7. 5.9 中可观测、前端框架、pnpm 工作区、openapi-typescript 加 openapi-fetch、挂件沿用 iframe 这几项选型。
8. 第一阶段的范围划分（4.3）：错误提示多语言、PGroonga 替换、IM 与嵌入渠道合并都放在第一阶段；第一阶段 PGroonga 使用通用配置，不按语言分词。
9. 用 Playwright 在旧系统上录制界面端到端测试，作为第一阶段的验收依据。
10. 不采用 agentscope-core，保留自研 agent-engine（D24、5.10.5）。spike 判定规则把第 4 项部分未通过留给架构方决定，这里的否决依据是 5.10 的能力清单与 D23，不是检查项本身。

# 10. 遗留事项

1. Elasticsearch 与 Milvus 不受行级安全保护，隔离只靠应用层的知识库归属校验，属于已知的较弱环节。
2. 迁移期间问答主链路最后打通，前期无法做完整的效果验证，只能依赖检索回归集。检索回归集需要覆盖多种语言。
3. 现有前端 82 个超过 400 行的组件，本次只随 API 切换修改调用层，组件拆分不在本次范围。
4. Spring Boot 3.5 与 Jackson 2 已无开源安全补丁。需要自行跟踪 Spring、Jackson、Tomcat 的 CVE 并升级补丁版本，或购买商业支持。
5. 阻塞工具在客户端断开后会继续跑到结束，现有引擎与 agentscope 都如此。断连只降级为无头监控，不触发取消。需要"断连即停"的工具要自行实现协作取消，本次不改变现有行为。
6. 托管 PostgreSQL 对 PGroonga 的支持因云厂商而异，AWS RDS 的扩展清单中没有 PGroonga。SaaS 部署若计划使用托管数据库，需先核实目标云厂商，否则改用自建 PostgreSQL。
