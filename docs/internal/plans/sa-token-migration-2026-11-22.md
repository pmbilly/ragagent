# WP3 Sa-Token 替换批——详细执行计划

- **日期**：2026-11-22　**状态**：已评审并拆分（2026-11-23 用户拍板）——**前半独立为 W1 批先行，Sa-Token 替换（3-B）缓做**
- **上游**：framework-optimization-roadmap-2026-11-22.md §4 WP3（WP 级方案）；蓝图 D20 / §5.3 / §5.6 / §5.9
- **性质**：触发式替换批，独立于 WP4（RLS）/WP5（JobRunr）
- **规模预估**：3~5 天，5 个提交批（WP3a~3e）

---

## 评审结论与拆分拍板（2026-11-23，先于下文一切批次）

**评审实测修正**：规则表实测 **375** 条 addRule（本文写 373）+ **291** 条 registerGin（写 290）；`TenantContext` 为 **8** 个 ThreadLocal（写 7，多 requestId）。jjwt 主码影响面仅 4 个文件（JwtService/UserSessionOps/UserService/TenantAPIPrincipalController）。蓝图 D20 的否决理由「权限规则散落难测试」在当前仓库已由集中策略表 + `check-route-policy.py` + auth 22 测试类 + 1422 金片解决。

**拍板：WP3 拆为两半，不一次全做。**

| 半 | 内容 | 状态 |
|---|---|---|
| **W1（先做，无新依赖）** | 规则面收敛：375+291 两表并一处注册 + 单一拦截器（普通 HandlerInterceptor，**不引 Sa-Token**）；framework 的 RbacInterceptor/RbacRules 删除 → 5 类迁 auth/api、tenant 包 12→7。流程复用本文 WP3b/WP3c（双锚 diff=0 → 一次切换 → 删旧） | 进行中 |
| **3-B（缓做，触发式）** | jjwt→Sa-Token（本文 WP3a/d/e：会话切轨、jjwt 清零、auth_tokens DROP、文档台账） | 触发条件：C 端客户登录立项 / 需多端会话管理 / 团队拍板与 furnace 对齐 |

**3-B 缓做的依据**：① 换 Sa-Token+Redis 会话会把「Redis 故障」影响面从 embed/限流扩大到控制台全挂（K5），当前 web JWT 无状态不受影响；② 对易读性/架构清晰的收益集中在规则面（已由 W1 获得）；③ 三账号完整体系属第二阶段 C 端域（本文 P3 已推迟）。

**W1 验收继承本文全部等价约束**：§0 行为等价矩阵（权限 diff=0 机器证明、HTTP 契约逐字、放行面原样、隐性语义含 HEAD/ASYNC/PathTenantMatch）、§5 风险 K1/K2/K3/K4/K7/K10、§6 显式不做（三 URL 组、PermissionCatalog、注解式鉴权均不做）。本文 WP3b/WP3c 的 `SaInterceptor` 在 W1 中以普通 `HandlerInterceptor` 实现，无 Sa-Token 依赖。

---

---

## 0. 行为等价的精确定义（本批第一约束）

系统未上线（D-4 拍板一次性切换），但「等价」必须钉死边界，执行中不许摇摆：

| 面 | 等价要求 |
|---|---|
| **权限判定矩阵** | 逐条等价：373 条角色规则 + 290 条能力规则（含 86 条拼接路径，不止守卫口径的 204 字面量），切换前后判定结果必须一致——用「新旧注册器 diff=0」机器证明（§4 WP3b） |
| **HTTP 契约** | 401/403/409 状态码与文案逐字等价（`insufficient workspace role` / `system administrator required` / `Forbidden: no route policy` / TENANT_REQUIRED 信封）；信封 `{code:1002}` 不动；`/auth/refresh` 端点保留（前端 `request.ts:238` 401 重放依赖它） |
| **放行面** | 白名单 14 条 + tenantless 7 组 + OPTIONS 直放 + 路径穿越 400 + embed/im-callback/r//沙箱 WS 旁路——原样继承 |
| **隐性语义** | HEAD→GET 规则兜底（`RbacInterceptorHeadTest` 5 条）；ASYNC 派发跳过鉴权（SseEmitter completeError 前提）；PathTenantMatch 先于 API-Key 短路 |
| **允许变化** | token 形态（JWT→Sa-Token）、签发/撤销内部机制（auth_tokens 表可删）、依赖（jjwt 可删）、金片中 token 字段的具体值 |

## 1. 现状盘点（2026-11-22 实测）

### 1.1 执行链

```
Filter（order）:  CORS(HIGHEST) → RequestId(+10, finally 清 TenantContext) →
                  APIKeyScopeCleanup(+15, finally 清 ScopeContext) →
                  AuthFilter(+20, /*) → EmbedAuthFilter(+30, /api/v1/embed/*)
Interceptor(order): ApiResult(-100) → APIKeyGate(-1) →
                    AllowFileServe(0)/DenyAPIKey(0)/Rbac(0) → Langfuse(10)
```

- **AuthFilter**（`backend/app/.../auth/controller/filter/AuthFilter.java`，非 bean，WebConfig new）：OPTIONS 直放→穿越守卫 400→白名单（硬编码 `NO_AUTH_API` 14 条）→API 文档→embed/im-callback/r//沙箱 WS 让路→**Bearer JWT 通道**（`UserService.validateToken`：jjwt HS256 签名+exp→拒 refresh/sandbox 类型→auth_tokens DB 撤销查→`WsAuthSupport.authenticateJwtUser`：X-Tenant-ID→JWT claim→home/首 membership 解析空间、membership→跨空间超管→孤儿自愈→fail-closed 角色解析）→**X-API-Key 通道**（`APIKeyAuthChannel`：SHA-256 hash 查库→平台/租户 Key 分支→principal 三模式 api_tenant/direct_header/signed_token→成对写 TenantContext+ScopeContext）→401。
- **RbacInterceptor**（framework/web，`preHandle`）：ASYNC 跳过(:132)→PathTenantMatch(URL 租户==上下文租户，空/非正整数 400、无上下文 401 fail-closed、平台 Key 放行)→API-Key 主体短路(能力归 Gate)→规则匹配(373 条，public 55/受限 300/sysAdminOnly 18；未命中 `/api/v1/` HandlerMethod→403 no route policy)→或 sysAdmin/orSystemAdmin/角色下限判定→RBAC 开关关=记日志放行→拒绝写 DeniedAuditor durable 审计→403 双文案+信封。
- **APIKeyGateInterceptor**（order -1）：无 scope 直通；有 scope 用 `BEST_MATCHING_PATTERN_ATTRIBUTE` 查 `APIKeyRouteAuthorizer`（Map<method, TreeMap>，判定序：无策略拒→PlatformOnly→fullAccess→能力 any-of→无能力要求放→拒）。
- **策略表**：`config/RbacRoutePolicies`（373 = addRule 300 + addSystemAdminRule 18 + addPublicRule 55，**登记顺序即语义**：AntPathMatcher 首个命中生效）；`auth/apikey/.../APIKeyRoutePolicies`（290 条 registerGin，gin `:x`→Spring `{x}`；守卫正则只锚 204 条字面量，**86 条 base 拼接是守卫盲区**；刻意不登记 4 端点防 Key 自扩权）。

### 1.2 身份运行时

`TenantContext`（framework/tenant，7 个 ThreadLocal：tenantId/principal/role/systemAdmin/canAccessAllTenants/userId/embedVisitorId）；清理在 `RequestIdFilter` finally（**非 AuthFilter finally，sse 计划 §0:127 口径不精确**；`OncePerRequestFilter.shouldNotFilterAsyncDispatch()` 字节码默认 true，sse 计划写「默认 false」是笔误）。异步：`TenantContextSnapshot.capture()`→虚线程 `TenantScope.run`（QA 链 `KnowledgeQaController.java:97-112`），live 钉 `KnowledgeQaLiveContractTest`。

### 1.3 依赖与测试

- jjwt 0.12.6（app 独占；framework 等其余模块零鉴权依赖）；无 spring-security（仅 crypto/BCrypt）；**sa-token 全仓净地**（唯一文本命中=VocabularyManifestTest 的预留注释）。
- Redis：dev/prod compose 均标配（redis:7.0-alpine），已是硬依赖（embed ems_ 会话、三级限流）。
- 鉴权测试 ~266 @Test（auth 包 22 类 229 + 外围 37：AuthContractTest 金片 10、APIKeyGate 11、RouteAuthorizer 16、RbacDeniedAudit 8、RbacInterceptorHeadTest 5、live 契约 3 门）。
- 守卫 `scripts/check-route-policy.py`：动态提取 385 控制器路由↔373+204 规则对账+幽灵检测+baseline（unguarded 空）+`route-policy-map.md` 一致性；**这是 WP3 步骤 4 要换锚的实体**。

### 1.4 tenant 包钉链（framework 主码→7 类的边只在两个文件）

```
RbacInterceptor ─→ TenantRole ← RbacRules
RbacInterceptor ─→ TenantAPIKeyScope ↔ APIKeyScopeContext ─→ APIKeyCapability/APIKeyScopeType
TenantConfigLookup ─→ APIPrincipalConfig（返回类型载荷）
```

删除 RbacInterceptor/RbacRules 后 framework 主码对 7 类引用即归零；5 留守类（TenantContext 族 4 + StorageEngineConfig）与 7 类**双向零代码纠缠**。

## 2. 计划 vs 蓝图偏差（评审重点，共 6 条）

| # | 蓝图原文 | 实测现实 | 本计划修正 |
|---|---|---|---|
| P1 | 「tenant 8 钉链解…tenant 包清空删除」 | tenant 现为 **12 类**（WP1a 已并入 4 类）；清空会连 TenantContext（framework 自身 EventBus/RequestIdFilter 在用）一起删 | 改「**12→7 收缩**」：迁 5 类、留守 7 类；包不删 |
| P2 | 「7 类整链随迁 auth/api」 | **TenantConfigLookup 被 retrieval-engine import**（`HybridSearchService.java:10`）——re 按晶格只能依赖 framework，迁 auth/api 即晶格违规 | TenantConfigLookup+APIPrincipalConfig **留守 framework/tenant**（它本就是标准端口：实现方 app TenantService、消费方 12+ 域）。只迁 5 类：TenantRole/TenantAPIKeyScope/APIKeyScopeContext/APIKeyCapability/APIKeyScopeType |
| P3 | 三账号体系三 StpLogic（§5.3/5.6） | open 主体=每请求查库（撤销即时性是安全属性）；embed=独立 token 体系（publish/ems_）+Origin/限流；**C 端访客无账号实体**（渠道凭证+匿名标签模型），访客登录属蓝图第二阶段「客户」域 | 本批只 **web 用户会话真 Sa-Token 化**；open/embed 查库直通保留，StpLogic 立号位作语义锚（未来 C 端登录=启用号位的加法扩展，路径见 §6.1）。三账号完整形态（三 URL 组 `/api/admin/v1` 等）与第二阶段 C 端域一起推迟 |
| P4 | 「RoutePolicies 204 条」 | 实际 290 条（86 条 base 拼接不在守卫正则口径） | 转换对账按全量 290；守卫盲区在 WP3b 顺手补（拼接路径入锚） |
| P5 | tenant 8 类清单 | TenantRole 消费横跨 **12 域**（config/auth/audit/channel/knowledge/mcp/model/session/websearch/datasource/agentmanagement） | 迁移后域边增生量由 `check-domain-contracts.py` 实测登记；若边数爆炸（>10），TenantRole 单独留守 framework 的备选路线提级评审 |
| P6 | — | `OncePerRequestFilter` async 默认 true（sse 计划 §0:28 写反）；「AuthFilter finally 清 TenantContext」实为 RequestIdFilter | 台账勘误，不阻塞 |

## 3. 拍板项（8 项，均有推荐）

| # | 议题 | 推荐 | 备选 |
|---|---|---|---|
| D1 | **open/embed 是否套 Sa-Token 会话** | **不套**：open 保留查库直通（撤销即时），embed 保留 EmbedAuthFilter；StpLogic 仅语义锚（P3） | 全套会话（open login(keyId)）：接受撤销延迟+会话膨胀，换蓝图字面完整 |
| D2 | **会话存储** | `sa-token-redis-jackson`（Redis 已硬依赖，重启不掉线，多副本就绪） | 内存（单机 OK，重启全员掉登录——现状 JWT_SECRET 未配时同款行为） |
| D3 | **jjwt 去留** | **全删**：access/refresh 归 Sa-Token；沙箱 WS 票据改 `SaTempUtil`（2 分钟短票，写 SaTempInterface 校验实现）；external-user 校验本就手写 HMAC 不依赖 jjwt | jjwt 留守仅沙箱票据（少一个 SaTempInterface，留一个自研签发面） |
| D4 | **策略层形态** | **单一 SaInterceptor + 统一注册器**：373+290 两表并入 `auth/route/SaRoutePolicies` 一处集中注册（蓝图「SaRouter 集中注册」原文），lambda 内按主体分派（API-Key→Authorizer 判定器复用/web→角色规则）；RbacInterceptor+APIKeyGateInterceptor 双件合一删除 | 两 interceptor 各表原样换壳（改动更小，策略面仍两处） |
| D5 | **7 类落点** | 5 类迁 `auth/api` + TenantConfigLookup/APIPrincipalConfig 留守（P2/P5） | 蓝图原案 7 类全迁（需给 retrieval 留 indirection，新增一层） |
| D6 | **auth_tokens 表** | DROP（migration 版本化）：撤销/登出归 Sa-Token 会话+kickout | 留表做登录审计（当前无审计消费方，不建议） |
| D7 | **refresh 端点** | 保留 `/auth/refresh` 契约（前端 401 重放不动）：内部实现改 Sa-Token 置换/续期；`/auth/login` 响应结构**保持字段名**（access_token/refresh_token 语义位），值改 Sa-Token token——金片只改值不改形 | 改响应结构（前端同批大改，无收益） |
| D8 | **403 文案与信封** | 逐字保留（golden 零改），含 `Forbidden: no route policy` 兜底文案 | 顺手统一文案（破坏 golden，禁止） |

## 4. 批次切分（5 批，每批独立提交+验收）

### WP3a 依赖+web 会话切轨（1~1.5 天）

1. 根 pom：`sa-token.version=1.46.0`（Boot3 starter 正系，最新稳定）入 properties 版本表 + dependencyManagement；app 引 `sa-token-spring-boot3-starter` + `sa-token-redis-jackson`（D2）。
2. **自动装配显式核查**（坑：starter 只注入 bean 不自动拦截——写一条启动冒烟测试证明无路径被自动保护/自动放行）。
3. 三 StpLogic 语义锚：`StpWebUtil`/`StpOpenUtil`/`StpEmbedUtil`（loginType web/open/embed；token-name 分别 `Authorization`/`X-API-Key`/`X-Embed-Token` 只读配置，open/embed 不 login）。
4. 会话切轨：login→`StpWebUtil.login(userId)`+会话存 email/homeTenant；logout→`StpWebUtil.logout()`；refresh→置换实现（D7）；`AuthFilter` Bearer 通道→`StpWebUtil.checkLogin/getLoginId`，`WsAuthSupport` 装配链保留（空间/角色解析输入从 JWT claims 改会话+DB，resolveTenantRole/resolveTargetTenant 逻辑不动）；X-API-Key 通道与白名单/穿越守卫/OPTIONS 原样。
5. 删：JwtService access/refresh 签发面、UserSessionOps 轮换/撤销、auth_tokens 读写（表 DROP 留 WP3d migration，代码先停写）。沙箱票据暂留 jjwt（WP3d 再换）。
6. 金片更新：AuthContractTest 族 token 值字段（台账登记 diff）+ 前端 request.ts/authRefresh.ts 验证不破。
- **验收**：auth 契约测试族绿（金片值更新台账）+ 全量闸门 + 手工冒烟（login→带 token 访问→logout→401→refresh）。

### WP3b 策略统一注册器+守卫双锚（0.5~1 天，不切入执行链）

1. 新 `auth/route/SaRoutePolicies`：脚本从 RbacRoutePolicies/APIKeyRoutePolicies 转换 373+290 条（保留三类语义与 registerGin 路径归一化；**顺序即语义**照搬）；转换脚本留 `scripts/`（一次性，不入守卫）。
2. `SaInterceptor` 子类：**首行 ASYNC 跳过**（逐字搬 RbacInterceptor.java:129-135 注释+逻辑）+ 单测直钉（当前无直接单测，补上）；lambda 分派序：ASYNC→PathTenantMatch→API-Key 短路（Authorizer 判定器逻辑复用迁入）→web 角色规则→denyUnmatched→审计+文案+信封（D8）。
3. **守卫双锚**：`check-route-policy.py` 增加 parse 新注册器分支；新锚全量对账（385 路由）+ **新旧锚逐条 diff=0**（行为等价机器证明）；顺手把 86 条拼接路径入锚消盲区（P4）。
4. `route-policy-map.md` 双锚重生成。
- **验收**：守卫双锚绿（diff=0）+ 新注册器单测（判定序矩阵：抽取 373 条中每类边界用例各 N 条参数化重放，新旧判定器同输入同输出）。

### WP3c 切换+删旧（0.5 天，鉴权真空零窗口）

1. WebConfig 一次切换：注册 SaInterceptor（路径=现 Rbac 30 前缀 ∪ APIKeyGate 覆盖面 − 排除面），删 RbacInterceptor/APIKeyGateInterceptor/RbacRules/RbacRoutePolicies/APIKeyRouteAuthorizer 注册（Authorizer 判定逻辑已并入 lambda 则连类删，否则降级为内部件）。
2. 同 commit 删 framework `RbacInterceptor/RbacRules`（framework→7 类引用归零点）；`RbacInterceptorHeadTest` 改锚新件（HEAD 兜底 5 条语义必须全绿）；`RbacDeniedAuditTest` 改锚。
3. 守卫切单锚（旧锚删除段显式报错——B168 教训的守卫版，同 02446a4ab 手法）。
- **验收**：385 路由守卫单锚绿 + 全鉴权测试绿 + `KnowledgeQaLiveContractTest` 409 门绿（ASYNC 钉）+ 测试差值台账。

### WP3d 钉链解+jjwt 清零（0.5~1 天）

1. 5 类迁 `auth/api`（D5）：单轮显式 FQN 改写禁通配（Ruling 24）；`check-domain-contracts.py` 实测域边增生并登记（P5 预案）；`VocabularyManifestTest.TENANT_ALLOW` 12→7 + 注释改写；`tenant/package-info.java` 摘「API Key 作用域」叙事；`JsonFaceVocabularyTest` import 改。
2. jjwt 全删（D3）：沙箱票据→`SaTempUtil`+SaTempInterface 校验实现（WsAuthSupport 对接）；根 pom jjwt 版本条目删。
3. auth_tokens 表 DROP migration（D6）+ V 增量；`deploy/env.prod.example` JWT_SECRET 段更新为 sa-token 配置说明。
- **验收**：全量闸门 + 守卫 49/49 + schema 对账守卫绿。

### WP3e 文档+台账（0.5 天）

HANDOFF/overview 鉴权叙事重写、route-policy-map 终态、本计划 §7 台账回填（金片 diff/域边增量/测试差值/偏差裁决）。

## 5. 风险与坑（执行速查）

| # | 风险 | 对策 |
|---|---|---|
| K1 | 鉴权真空（Ruling 23） | b 批只建注册器+守卫双锚（不进执行链），c 批单 commit 一次切换——不存在新旧都死的窗口 |
| K2 | ASYNC 重鉴权→409 信封全变 403 | SaInterceptor 子类首行跳过+**新增直接单测**（当前仅 live 间接钉）+ live 409 门三重保障 |
| K3 | HEAD 自动派生绕过角色下限 | HEAD 兜底逻辑照搬+Head 测试改锚必须 5/5 绿 |
| K4 | PathTenantMatch 顺序（必须先于 API-Key 短路） | lambda 分派序单测钉 |
| K5 | Redis 故障=鉴权全挂（比现状 JWT 无状态更脆） | D2 代价显式登记；deploy 检查项+CI redis job 已有；kickout/降级行为写 package-info |
| K6 | Sa-Token 自动装配意外生效/失效 | WP3a 启动冒烟测试双向钉（该拦的拦、不该拦的不拦） |
| K7 | 373 条转换手误 | 脚本转换+新旧 diff=0 机器证明，人只审脚本不审 373 条 |
| K8 | TenantRole 域边爆炸 | P5 预案：>10 条新边则 TenantRole 留守提级评审，勿硬迁 |
| K9 | 金片 token 值更新连带前端 | D7 保字段名；前端 817 测试+契约键守卫双闸 |
| K10 | 双 interceptor 同跑双重判定 | b/c 分离：b 批新件不注册进 WebConfig |

## 6. 显式不做（登记遗留）

- 三 URL 组重排（/api/admin/v1、/api/open/v1）——第二阶段重写目标态，破坏行为等价，待 C 端新域开工批。
- OIDC 实现替换为 Nimbus——现状 OIDC 可用（OidcContractTest 4 条绿），登录成功后发 Sa-Token 会话即融入，签发端不动。
- `@SaCheckRole/@SaCheckPermission` 注解式——D20 拍板理由即「规则散落难测试」，坚持集中注册表。
- WP4（RLS）/WP5（JobRunr）——独立轨道。

### 6.1 未来 C 端用户登录的扩展路径（评审补充，钉结构防误判）

蓝图第二阶段「客户」域给 C 端发登录能力时，扩展动作全部是**加法**，与本批决定零冲突：

1. 客户账号实体（第二阶段新域落库，**D21 两层：passport 全局身份不带 tenant_id 走系统通道、customer 租户表挂 RLS——数据严格按租户隔离，身份只打通不共享**）；2. 登录端点调 `StpEmbedUtil.login(customerId)`——**启用本批已立的第三号位锚**，token/会话/续期/踢下线设施现成；3. 统一注册器加一段 C 端规则；4. `TenantContext.PrincipalTypes` 加 `embed_customer` 类型常量。匿名渠道访问与客户登录访问可并存于同一挂件（独立 token-name、独立会话空间）。

**结构约定（执行约束）**：SaInterceptor 主体分派 lambda 按主体类型显式分支、注册器按体系分段——**新账号体系加入时只新增分支与段落，不改既有分支**。匿名历史并入客户账号是 session 域业务数据迁移，与鉴权框架无关，届时单独立项。

## 7. 台账（执行后回填）

- [ ] 金片 diff 清单（WP3a）
- [ ] 新旧注册器 diff=0 证据（WP3b）
- [ ] 测试差值（删/改/新增计数）（WP3c/d）
- [ ] 域边增量清单 + check-domain-contracts 基线变化（WP3d）
- [ ] 偏差终裁（P1~P6 实际执行结果）（WP3e）
- [ ] jjwt/auth_tokens 删除证据（WP3d）
