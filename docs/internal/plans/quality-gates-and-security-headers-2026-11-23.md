# WPQ 质量硬门与全局安全响应头——详细执行计划

- **日期**：2026-11-23　**状态**：待评审（评审通过才动手）
- **上游**：蓝图计划1 评估（`docs/blueprint/plans/2026-11-10-rebuild-01-skeleton-and-safety-net.md` Task 7 / Task 11c / 路线图 §3.11）
- **性质**：独立小批，不依赖 WP3（Sa-Token）/WP4（RLS）/WP5（JobRunr），可并行排期
- **规模预估**：2~3 天，4 个提交批（WPQ1 + WPQ2a/2b/2c）
- **北极星**（不是本批目标，写进守卫 docstring 防失传）：方法 40 行 / 文件 400 行 / 参数 5 个 / 圈复杂度 10 / 嵌套 3 层 / 构造器参数 7 个

---

## 0. 现状数据（2026-11-23 dry-run 实测）

工具：checkstyle **10.21.4 CLI**（本地 classpath 手工组装，命令见附录 A），配置=蓝图案阈值，扫根聚合五模块主码（`backend/{framework,model-runtime,retrieval-engine,agent-engine,app}/src/main/java`，共 2647 文件）。

| 规则 | 蓝图阈值 | 存量违规 | 敏感性 | 本批处置 |
|---|---|---|---|---|
| NestedTryDepth | 2 | **0** | — | **硬门直接上** |
| NestedForDepth | 2 | **2** | — | 修掉 2 处后硬门上 |
| NestedIfDepth | 2 | **24** | — | 修掉 24 处后硬门上 |
| MethodLength（countEmpty=false） | 40 | 674 | 60→348；80→173 | 棘轮 150→**80** |
| ParameterNumber（方法+构造器） | 5 | 423 | 6→215；7→114；9→40 | 新棘轮 **>7** |
| CyclomaticComplexity | 10 | 932 | 15→393；20→190 | 本批不设门（拍板 D3） |
| FileLength | 400 | 184 | 600→51 | 维持现有 600 棘轮不动 |

交叉验证：FileLength 600 档 51 处与 `check-file-size.py` 口径一致；MethodLength 80 档 173 与 `_method_lengths.py` 的 170 吻合（±3 为口径差，见 §5）。

现有防线：`check-method-length.py`（>150 棘轮，存量 27）、`check-file-size.py`（>600 棘轮 + ≥800 登记）；**根 pom（唯一聚合，`backend/pom.xml` 不存在）零质量插件**，spotless 只管格式 ratchet。即：尺寸/复杂度维度目前**没有任何防新增机制**——本批要补的就是这个。

## 1. 不可协商约束

1. 零业务行为变化（WPQ1 除外：其行为变化=新增三个响应头，外部可见，单独批+三类响应冒烟护航）。
2. 闸门口径：`mvn -B clean verify` 5244/0/2 守恒（WPQ1 允许 +N 条新测试，台账对账）+ `run-guards.sh` 全绿（守卫数从 49 变 50，台账登记）。
3. 本机环境：`JAVA_HOME=/opt/homebrew/opt/openjdk@21`；后台/管道跑构建须验 PIPESTATUS（老坑）。
4. **不引入 `@SuppressWarnings("checkstyle:...")` 抑制机制**：嵌套硬门保持零例外。哪天确需抑制，必须同批带上「抑制预算 ≤10 + 前一行中文理由」守卫（蓝图 Task 11c 的设计），否则不许开闸。
5. 蓝图北极星数字只进 docstring 与本文档，不进本批阈值。

## 2. WPQ1 全局安全响应头（0.5~1 天）

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/SecurityHeadersFilter.java`
- Create: `backend/framework/src/test/java/com/ragagent/framework/web/SecurityHeadersFilterTest.java`（纯单测，不起 Spring 上下文）
- Modify: `backend/app/src/main/java/com/ragagent/config/WebConfig.java`（注册 FilterRegistrationBean，order = `Ordered.HIGHEST_PRECEDENCE + 5`，CORS 之后、RequestIdFilter 之前）
- Create: `backend/app/src/test/java/com/ragagent/web/SecurityHeadersBootTest.java`（RANDOM_PORT 启动一条，GET 任意路径断言三头，404 响应也必须带）

**行为定义：**
- 三个头，全部「已存在则不覆盖」（`containsHeader` 判断，保护 `FileTransport.java:80-195` 与 `TemporaryDocumentController.java:235` 已逐点设置的 `nosniff`/`Cache-Control`）：
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: SAMEORIGIN`
  - `Referrer-Policy: no-referrer`
- 不加 CSP（蓝图理由沿用：挂件以 iframe 被宿主页加载，CSP 归属前端）。
- 开关 `weknora.web.security-headers.enabled`（默认 true），前缀风格随 `weknora.tenant`（TenantProperties.java:17）。配置类放 `framework/config/SecurityHeaderProperties.java` 或直接 `@Value`——见拍板 D5。
- 实现风格仿 `RequestIdFilter`（OncePerRequestFilter、无状态、framework/web 包、类级中文 Javadoc）。头在 `chain.doFilter` **之前**设置（SSE/流式响应可能在 chain 返回前已提交，事后补头无效）。`shouldNotFilterAsyncDispatch` 保持默认 true：ASYNC 派发不重复执行，头已在首次派发设置（SseEmitter 三入口行为，参考 WP3 文档 §1.1 的 ASYNC 语义）。

**测试断言（FilterTest 四条）：**
1. 普通请求响应带三头，值逐字如上；
2. 响应已含同名头时不覆盖（预设 `X-Content-Type-Options: X`，过滤后仍为 `X`）；
3. `enabled=false` 时不加任何头；
4. OPTIONS 预检响应同样带头（顺序在 CORS 之后不冲突——CORS filter 直接写 200 响应，本 filter 仍会补头，无副作用）。

**冒烟三类响应（BootTest 或手工）：** 普通 JSON、文件下载路由（原有头不被覆盖）、SSE 端点（头存在、体与 Content-Type 不变——SseEmitter 契约测试本批必须全绿即证）。

**CORS 不动**：`WebConfig.java:73` 的 `allowedOriginPatterns("*") + allowCredentials(true)` 是真实收紧点，但改它是前端跨域行为变化，需要部署事实（前端同源与否、embed 宿主页清单）。本批只把它登记进 known-gaps 台账，白名单化随部署事实另批（拍板 D4）。

## 3. WPQ2 Checkstyle 硬门 + 棘轮收紧（1.5~2 天）

### WPQ2a Checkstyle 嵌套硬门（Maven validate 拦截）

**Files:**
- Create: `config/checkstyle.xml`（见附录 B）
- Modify: 根 `pom.xml`（`maven-checkstyle-plugin`，execution 绑 `validate` 阶段 `check` goal，`includeTestSourceDirectory=false`，`failOnViolation=true`）
- Modify: 26 处存量嵌套（24 NestedIf + 2 NestedFor，位置从 dry-run log 取，附录 A 命令可复跑）

**Step 0（已完成，2026-11-23 开工时验证）：插件可用，此前为误诊。** 初稿记录的「`maven-checkstyle-plugin:3.6.0`/`3.5.0` 在 aliyun 镜像解析失败」是**坐标笔误**（写成了不存在的 `com.puppycrawl.tools:checkstyle-maven-plugin`）；真实坐标 `org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0` 解析正常，且 `check` goal 在 framework 模块实跑通过（完整 dry-run 配置报 57 条违规，口径一致）。插件直连形态落定，无需守卫 CLI 兜底；附录 A 的手工 classpath CLI 仅留作逐规则计数 dry-run 的复现工具。

**规则集**：仅 `NestedIfDepth/NestedForDepth/NestedTryDepth` 各 max=2。MethodLength/ParameterNumber/FileLength/CyclomaticComplexity **不进** checkstyle（海量存量，硬门即全红；交给棘轮，见 2b/2c）。

**存量修复**：26 处逐处消解——提前返回、卫语句、提取私有方法；**纯结构改写，不改行为**。每处修复所在类的既有测试必须绿；修复前后 `mvn -pl <module> test` 局部回归。

**反向验证（必做）**：临时在 `backend/framework/src/main/java/` 放一个三层嵌套 if 的探针类 → `mvn validate`（或守卫形态）必须 **BUILD FAILURE** 且报 `NestedIfDepth`；删探针恢复绿。本仓守卫有 16 天假绿前科，这步不许省。

### WPQ2b 方法长度棘轮 150→80

**Files:**
- Modify: `scripts/check-method-length.py`（`THRESHOLD = 150` → `80`；docstring 补北极星 40 与收紧史）
- Regenerate: 对应基线文件（`--list` 导出 173 条人工核对后入基线）

**语义保持**：五条判据（R-a 新增红 / R-b 回涨超 slack 红 / R-c 失效红 / R-d 理由必填 / R-e `--write` 只收紧不新增）原样保留。基线理由规则放宽一次：**本批收编的 173 条允许统一批量理由**「2026-11-23 阈值 150→80 收紧批收编」（拍板 D2）；此后新增条目必须逐条写具体理由。

台账对账：收紧后 `--list` 计数 = 173（Checkstyle 口径）/170（Python 口径），差 3 条在 §5 口径差说明里登记。

### WPQ2c 参数数量棘轮（新守卫，含 MODULES 死路径清理）

**Files:**
- Create: `scripts/check-parameter-count.py` + `scripts/parameter-count.baseline.json`
- Modify: `scripts/_method_lengths.py:11`（MODULES 删已不存在的 `backend/contract` 死路径；同查 `_source_roots.py` 是否同病）
- Modify: `scripts/run-guards.sh`（登记新守卫）

**实现**：复用 `_method_lengths.py` 的词法遮罩与 `DECL` 捕获（遮罩后字符串/注解里的逗号不会虚增计数），对参数表数**顶层逗号**得参数数（泛型尖括号需配对跳过——遮罩后仍需一个小状态机，参考 DECL 的既有写法）。方法与构造器同查，统一阈值 **>7 红**（拍板 D6：不引 ArchUnit 构造器专项，也不引 ArchUnit ViolationStore freeze 机制）。

**判据语义**：抄 `check-method-length.py` 五条，一字不改（含「`--write` 不新增条目」防掩盖设计）。初始基线 = dry-run 口径换算约 114 条（Checkstyle 参数口径含构造器；Python 口径重建后以 `--list` 实数为准，差异登记台账）。新守卫同样需要反向验证：临时写一个 8 参方法探针 → 红；删探针 → 绿。

### WPQ2d 圈复杂度棘轮——本批不做（拍板 D3）

理由：932 处存量；词法近似器与 Checkstyle 权威口径必然漂移（switch/箭头 switch/lambda/record 紧凑式），近似器只能做「不再变差」而数字不可对外；且复杂度违规方法大概率同时超 80 行，2b 已间接压制。若季度后 80 档棘轮收敛良好仍有诉求，再以 max=15（存量 393）起步单独立项。

## 4. 拍板项（6 项，均有推荐）

| # | 议题 | 推荐 | 备选 |
|---|---|---|---|
| D1 | Checkstyle 挂载形态 | Maven 插件直连（2026-11-23 实测可用，原「镜像解析失败」为坐标笔误已修正） | 守卫 CLI 形态（已不需要） |
| D2 | 棘轮收编批的理由规则 | 173 条允许一次批量理由，此后逐条 | 逐条写理由（更严，但 173 条人工成本不匹配收益） |
| D3 | 圈复杂度棘轮 | 本批不做，观察 2b 效果 | max=15 起步同步做（+0.5 天近似器开发+口径差登记） |
| D4 | CORS `*`+credentials 收紧 | 本批只登记 known-gaps，随部署事实白名单化 | 本批同步白名单化（需先确认前端部署拓扑与 embed 宿主页清单，超出本批输入） |
| D5 | 安全头可配置性 | 仅 `enabled` 开关，固定三头（YAGNI） | 头清单可配置（无消费方，先不做） |
| D6 | 构造器参数 7 门形态 | 并入 Python 棘轮统一管（不分构造器/方法） | ArchUnit 规则 + freeze 冻结存量（引新机制，不推荐） |

## 5. 坑清单

1. **插件坐标必须写全 groupId**：`org.apache.maven.plugins:maven-checkstyle-plugin`——本计划初稿曾误写 `com.puppycrawl.tools` 并误诊为镜像问题（教训：解析失败先查坐标再看镜像）。
2. **口径差是常态不是 bug**：MethodLength Checkstyle 674 vs Python 786（countEmpty 与文本块处理差异）、80 档 173 vs 170。**规则：每个指标只有一个权威口径**——嵌套归 Checkstyle、方法长度/参数归 Python 棘轮，两套数字不并轨、不互相校准。
3. **X-Frame-Options 与挂件**：后端 API 不承载 iframe 页面（embed.html 在前端域），`SAMEORIGIN` 不影响挂件嵌入；若将来后端直出 HTML 需复核此头。
4. **安全头必须 setIfAbsent**：覆盖语义会打破 FileTransport 的下载头契约（那里设过 `nosniff` 与业务 `Cache-Control`）。
5. **SSE 响应头时机**：头必须在 `chain.doFilter` 前设置；SseEmitter 契约测试（Content-Type 逐字钉）全绿是本批验收的一部分。
6. **棘轮 `--write` 只收紧**的语义在 2b 阈值改动时容易顺手破坏（重建基线时误把新增当收编）——重建用 `--list` 导出+人工核对，不用 `--write`。
7. **spotless 与 checkstyle 并存**：一个管格式（import 顺序/空白 ratchet）、一个管结构（嵌套），互不重叠；不要在 checkstyle.xml 里加格式类规则，避免双引擎打架。
8. 探针反向验证后**必须删除探针文件**再提交（B168 教训：守卫扫描面残留死段）。

## 6. 执行台账（执行方填写）

| 批 | 提交 | 日期 | 验收证据 | 备注 |
|---|---|---|---|---|
| WPQ1 | 08b4d336d | 2026-11-23 | framework 单测 4/4 + 装配测试 2/2；w5c 金片 9/9（头金片 27 份按新契约补行重录） | 头集合变化属有意契约变更；CORS 缺口登记 HANDOFF §11.1 #21 |
| WPQ2a | 见 git log | 2026-11-23 | 4 层探针 validate 红/删后绿；full verify 4282/0/0；26 处修复后 0 违规 | 嵌套语义实测：max=2 允许三层、第 4 层才违规；SqlSelectDeepChecker 拆分尝试因表达式校验器内容错位被回滚，改用注释压缩（607→600）+ 行为等价由 15+10 条录制测试护航 |
| WPQ2b | 见 git log | 2026-11-23 | 85 行探针 R-a 红/删后绿；基线 170 条（27 保留 + 143 收编） | Python 口径 170 vs Checkstyle 口径 173（±3，§5 口径差规则） |
| WPQ2c | 见 git log | 2026-11-23 | 8 参探针 R-a 红/删后绿；基线 86 条；run-guards 50/50 | MODULES 死路径（backend/contract）两处清理；hygiene-budget 的 ctorsOver8 计数保留（双重信号非冲突） |
| 终验 | | 2026-11-23 | `mvn -B clean verify` BUILD SUCCESS · 4282/0/0 · 50 守卫全绿（`ZZ_PG_JDBC=jdbc:postgresql://localhost:15432/RagAgent` 环境口径） | tag v0.11.2 后共 5 个提交 |

## Review Focus（评审重点，四问）

1. 26 处嵌套修复是否真的零行为变化？（抽查 5 处 diff，确认只有控制流外提与返回提前）
2. 80 行棘轮会不会误伤「规则/数据多」型长方法？（docstring 已声明此类方法该不该拆由人工判断，棘轮只管不新增——确认这个立场没被推翻）
3. 安全头三件套有没有遗漏的部署面（反代/nginx 是否已设同名头导致双重头）？——生产 compose 的 nginx 配置需在执行时核对一次。
4. D1 插件直连已实测落定，本问失效；改问：checkstyle 核心钉 10.21.4（与 dry-run 口径一致）是否认可？

---

## 附录 A：dry-run 复现命令

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
M2=$HOME/.m2/repository
CS=$M2/com/puppycrawl/tools/checkstyle/10.21.4/checkstyle-10.21.4.jar
CP="$CS:$M2/info/picocli/picocli/4.7.6/picocli-4.7.6.jar:\
$M2/org/antlr/antlr4-runtime/4.13.2/antlr4-runtime-4.13.2.jar:\
$M2/commons-beanutils/commons-beanutils/1.10.1/commons-beanutils-1.10.1.jar:\
$M2/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar:\
$M2/org/reflections/reflections/0.10.2/reflections-0.10.2.jar:\
$M2/commons-logging/commons-logging/1.3.1/commons-logging-1.3.1.jar:\
$M2/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar"
# config 见附录 B；逐模块跑，grep 汇总规则名计数
for m in framework model-runtime retrieval-engine agent-engine app; do
  $JAVA_HOME/bin/java -cp "$CP" com.puppycrawl.tools.checkstyle.Main \
    -c config/checkstyle-dryrun.xml backend/$m/src/main/java
done
```

## 附录 B：dry-run 用的完整规则配置（本批上线的裁剪版只留三条 Nested*）

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
  <module name="FileLength"><property name="max" value="400"/></module>
  <module name="TreeWalker">
    <module name="MethodLength"><property name="max" value="40"/><property name="countEmpty" value="false"/></module>
    <module name="ParameterNumber"><property name="max" value="5"/></module>
    <module name="CyclomaticComplexity"><property name="max" value="10"/></module>
    <module name="NestedIfDepth"><property name="max" value="2"/></module>
    <module name="NestedForDepth"><property name="max" value="2"/></module>
    <module name="NestedTryDepth"><property name="max" value="2"/></module>
  </module>
</module>
```
