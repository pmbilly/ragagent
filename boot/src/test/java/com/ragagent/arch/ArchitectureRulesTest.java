package com.ragagent.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ragagent.RagAgentApplication;
import com.ragagent.common.crypto.CryptoService;
import com.ragagent.common.security.SsrfGuard;
import com.ragagent.common.storage.UploadLimits;
import com.ragagent.common.wiki.WikiLanguageSupport;
import com.ragagent.common.deployment.AppEnvLookup;
import com.ragagent.retrieval.config.RetrievalEnvLookup;
import com.ragagent.storage.config.StorageEnvLookup;
import com.ragagent.common.storage.StorageRuntimeEnv;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

/**
 * 全仓架构规则（B10：把几轮重构攒下的约定固化成 CI 红条）。
 *
 * <p><b>分工</b>：包级结构（包间环 / 分层 / 域依赖 {@code config}）由
 * {@code scripts/check-package-cycles.py}（CI 的 guards job，带基线棘轮）守；本类守
 * <b>代码级</b>规则——脚本表达不了、而这几轮真实踩过坑的：</p>
 *
 * <ol>
 *   <li><b>裸读环境变量</b>：B6 十批把 {@code System.getenv} 从 149 处清到 0，四类落点
 *       （{@code @ConfigurationProperties} / 域查找面 / 启动期值快照 / 全局查找面 + 启动钩子）
 *       已定型；本规则防止回流。</li>
 *   <li><b>{@code @ConfigurationProperties} 类必须被 {@code @ConfigurationPropertiesScan}
 *       名单覆盖</b>：漏一处的表现是<b>静默</b>的——不报错，只是值永远是默认值
 *       （B6 期间反复踩：新属性类放进未扫描的包）。</li>
 *   <li><b>配置类不得同时是 {@code @Component}/{@code @Service}</b>：属性绑定 + 组件扫描
 *       双装配，语义含糊。</li>
 *   <li><b>{@code install*} 只许装配层调用</b>：查找面/快照类的 {@code install} 是启动期
 *       一次性写入，运行期调用即「把配置当状态改」（各 holder 注释均写明此约束）。</li>
 *   <li><b>禁 {@code @Lazy} 注入</b>（A6）：循环依赖要拆（下沉/接口反转），不许懒加载
 *       掩盖设计缺陷；knowledge 域门面环在棘轮基线，解环专项落地后清空。</li>
 *   <li><b>裸 JDBC 白名单</b>（A7）：业务单表 CRUD 走 MyBatis-Plus；方言探测 / PG 专有
 *       SQL / 非业务库引擎 / 启动修复四类 MP 能力边界场景登记放行，新类须在 PR 论证。</li>
 *   <li><b>禁字符串列名 wrapper</b>（A8）：条件构造器一律 Lambda 方法引用，硬编码列名
 *       无编译期保护；存量在基线，随逐域 Lambda 化清空。</li>
 *   <li><b>{@code .last(} 只许纯字符串字面量</b>（A9）：拼接既是注入面也是方言漂移点；
 *       动态行数走分页插件或注解 SQL {@code #{}} 参数化。</li>
 *   <li><b>分层倒挂</b>（A10）：{@code service} 不得依赖 {@code controller}（方向必须
 *       controller → service → repository/mapper），{@code domain} 不得依赖
 *       {@code service}/{@code controller}（领域模型保持纯净）。</li>
 *   <li><b>Mapper 接口包约定</b>（A11）：命名以 {@code Mapper} 结尾的<b>顶层</b>接口必须落在
 *       {@code ..mapper..} 包——{@code @MapperScan("com.ragagent.**.mapper")} 只扫这些包，
 *       放错包的表现是启动期缺 Bean 或该 mapper <b>静默不注册</b>。</li>
 * </ol>
 *
 * <p>新加规则请只加<b>当前零违例</b>的规则，否则等于把存量违例变成噪声；确有存量违例要
 * 棘轮化的，走 A6-A9 的代码内基线模式——Set/Map 逐条登记（附理由）+ {@link #ratchet}
 * 双断言：基线外新增违例即红（拦增量），基线条目不再违例也红（防规则空转，强制随清理
 * 收紧）。不引入 FreezingArchRule 的存储文件；包级棘轮仍在脚本里。</p>
 */
class ArchitectureRulesTest {

    /**
     * 全仓<b>主源集</b>（含所有域）。
     *
     * <p>必须按输出目录过滤：{@code importPackages("com.ragagent")} 会把 src/test 的类一起扫进来，
     * 而测试里读真实环境变量是<b>合法</b>的（例如各 connector 桩要读宿主 env 拼 SSRF 白名单），
     * 那些不是本规则的治理对象。</p>
     */
    private static final JavaClasses MAIN = new ClassFileImporter()
            // B116 多模块：**不能用 location.asURI()** —— ArchUnit 对 jar 内的类求 asURI
            // 会抛异常，而"抛异常的导入选项"被当作**排除**，于是 :common（在 :server 的
            // 类路径上以 jar 形态出现）被整段排除，A7 基线条目随即报"已不再违例"（实测踩到，
            // 探针四变体定位：asURI 版命中 0、Location.contains 版命中 1）。
            // 改用 Location.contains 排除测试类。
            // B161：testFixtures 也是测试基建（如 EmbeddedRedis 的 getenv 探测 redis-server）——
            // 拆分前它们在 src/test/java 下本就被排除，故一并排除，保持 A1 只盯 main 的语义。
            // 注意：项目依赖在 ArchUnit 眼里是 **jar**（B116 实测）⇒ testFixtures 以
            // <模块>-test-fixtures.jar 形态出现，dir 与 jar 两种写法都要排。
            .withImportOption(location -> !location.contains("/classes/java/test/")
                    && !location.contains("/classes/java/testFixtures/")   // 本模块 testFixtures（dir 形态）
                    && !location.contains("-test-fixtures.jar"))            // 跨模块 testFixtures（jar 形态）
            .importPackages("com.ragagent");

    /**
     * 启动期一次性写入的查找面/快照（各类注释：只允许装配层调用 install）。
     *
     * <p>键是 {@code 属主全名#方法名}——<b>刻意不用 {@code JavaMethod.getFullName()}</b>：
     * 后者带参数类型（{@code ...install(java.util.function.Function)}），按它建集合会让规则
     * <b>永远绿</b>（B10 实测：探针类照样通过，属"空转规则"）。</p>
     */
    private static final Set<String> INSTALL_TARGETS = Set.of(
            WikiLanguageSupport.class.getName() + "#installLanguage",
            UploadLimits.class.getName() + "#installFileSizeMb",
            CryptoService.class.getName() + "#installAesKey",
            SsrfGuard.class.getName() + "#installWhitelist",
            StorageRuntimeEnv.class.getName() + "#install",
            StorageEnvLookup.class.getName() + "#install",
            RetrievalEnvLookup.class.getName() + "#install",
            AppEnvLookup.class.getName() + "#install");

    // ── A1 禁裸读环境变量 ──────────────────────────────────────────────────

    @Test
    @DisplayName("A1：主代码不得调用 System.getenv（走四类已登记落点）")
    void noRawEnvReads() {
        // 过滤失败会静默「零类可查」→ 规则永远绿；先自证导入面正常
        assertThat(MAIN.size()).as("主源集导入为空或过少：检查 ImportOption 的输出目录过滤").isGreaterThan(500);
        noClasses()
                .should().callMethod(System.class, "getenv", String.class)
                .because("裸 getenv 绕过 @ConfigurationProperties/查找面：既不能被属性源与命令行覆盖，也"
                        + "无法在测试里注入。改用：@ConfigurationProperties 注入 / 域查找面（*EnvLookup）/ "
                        + "启动期值快照 / 全局 AppEnvLookup（启动钩子装配）")
                .check(MAIN);
        noClasses()
                .should().callMethod(System.class, "getenv")
                .because("同上（无参重载）")
                .check(MAIN);
    }

    // ── A2/A3 属性类必须被扫描覆盖、且不双装配 ──────────────────────────────

    @Test
    @DisplayName("A2：@ConfigurationProperties 类必须落在 @ConfigurationPropertiesScan 名单覆盖的包内")
    void propertiesClassesAreScanned() {
        Set<String> scanned = scannedPackages();
        assertThat(scanned).as("RagAgentApplication 的 @ConfigurationPropertiesScan 名单不应为空").isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (JavaClass clazz : MAIN) {
            if (!clazz.isAnnotatedWith(ConfigurationProperties.class)) {
                continue;
            }
            String pkg = clazz.getPackageName();
            boolean covered = scanned.stream().anyMatch(s -> pkg.equals(s) || pkg.startsWith(s + "."));
            if (!covered) {
                missing.add(clazz.getFullName() + "（包 " + pkg + "）");
            }
        }
        assertThat(missing)
                .as("属性类漏扫描的后果是静默失效（值永远取默认）；把所在包加进 RagAgentApplication "
                        + "的 @ConfigurationPropertiesScan 名单")
                .isEmpty();
    }

    @Test
    @DisplayName("A3：@ConfigurationProperties 类不得同时是 @Component/@Service（避免双装配）")
    void propertiesClassesAreNotComponents() {
        noClasses()
                .that().areAnnotatedWith(ConfigurationProperties.class)
                .should().beAnnotatedWith(Component.class)
                .orShould().beAnnotatedWith(Service.class)
                .because("属性绑定已由 @ConfigurationPropertiesScan 负责，再加组件注解属双装配")
                .check(MAIN);
    }

    // ── A4 install* 只许装配层调用 ─────────────────────────────────────────

    @Test
    @DisplayName("A4：install*（启动期写入查找面/快照）只许装配层（*.config 包）调用")
    void installOnlyFromWiring() {
        // 注意：这里必须是 classes().should(customCondition)，不能用 noClasses().should(...)
        // ——后者会把条件取反，自定义条件里手写的 violation 会被反转成通过（B10 实测踩坑：
        // 该规则曾一度「永远绿」，靠探针类才暴露）。
        classes()
                .should(new ArchCondition<>("调用启动期 install* 且不在装配层") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (isWiringPackage(item.getPackageName())) {
                            return;
                        }
                        item.getMethodCallsFromSelf().stream()
                                .filter(call -> INSTALL_TARGETS.contains(call.getTarget().getOwner().getFullName()
                                        + "#" + call.getTarget().getName()))
                                .forEach(call -> events.add(SimpleConditionEvent.violated(item,
                                        item.getFullName() + " 调用了启动期写入 "
                                                + call.getTarget().getFullName()
                                                + "——只允许 *.config 装配层调用；运行期改配置属状态变更，"
                                                + "应另行设计（见各 holder 的类注释）")));
                    }
                })
                .check(MAIN);
    }

    // ── A5 源码不得含裸 NUL 字节 ────────────────────────────────────────────

    @Test
    @DisplayName("A5：源文件不得含裸 NUL 字节（会让 grep/ripgrep 判为二进制并静默跳过该文件）")
    void noRawNulBytesInSources() throws java.io.IOException {
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (java.nio.file.Path root : java.util.stream.Stream.of(
                backendSourceRoots("main/java"), backendSourceRoots("test/java"))
                .flatMap(java.util.List::stream).toList()) {
            if (!java.nio.file.Files.isDirectory(root)) {
                continue;
            }
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
                for (java.nio.file.Path file : walk.filter(java.nio.file.Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java")).toList()) {
                    byte[] raw = java.nio.file.Files.readAllBytes(file);
                    for (byte b : raw) {
                        if (b == 0) {
                            offenders.add(file.toString());
                            break;
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("裸 NUL（常见于把 \\0 哨兵直接写成字节）会让文本工具跳过整个文件，"
                        + "审计因此静默漏文件；改写为 Java 的 \\0 八进制转义即可（B12 实测）")
                .isEmpty();
    }

    // ── A6 禁 @Lazy 注入 ────────────────────────────────────────────────────

    /**
     * 棘轮基线：已清空（2026-10-05，M2 解环专项落地）。拆法：
     * 门面 helper 下沉 {@code KnowledgeAccessHelper}（子服务不再回注门面）；
     * enqueue 端口外提为 {@code KnowledgeProcessingQueue}（提交方不再依赖 worker 类型）；
     * worker 的摘要触发改同步领域事件 {@code KnowledgeProcessedEvent}（订阅方
     * KnowledgeSummaryService）。本规则现为零基线纯禁令。
     */
    private static final Set<String> LAZY_BASELINE = Set.of();

    @Test
    @DisplayName("A6：构造器参数/字段不得标 @Lazy（循环依赖要拆，不许懒加载掩盖）")
    void noLazyInjection() {
        ratchet("A6 @Lazy", LAZY_BASELINE, violatingClasses(ArchitectureRulesTest::hasLazyInjectionPoint));
    }

    private static boolean hasLazyInjectionPoint(JavaClass clazz) {
        for (JavaConstructor ctor : clazz.getConstructors()) {
            for (var annos : ctor.getParameterAnnotations()) {
                for (JavaAnnotation<?> anno : annos) {
                    if (anno.getRawType().getName().equals(Lazy.class.getName())) {
                        return true;
                    }
                }
            }
        }
        for (var field : clazz.getFields()) {
            if (field.isAnnotatedWith(Lazy.class)) {
                return true;
            }
        }
        return false;
    }

    // ── A7 裸 JDBC 白名单 ───────────────────────────────────────────────────

    /** 命中即算裸 JDBC 的目标类型（按声明类型判定，含子类）。 */
    private static final List<Class<?>> JDBC_TARGET_TYPES = List.of(
            org.springframework.jdbc.core.JdbcTemplate.class,
            org.springframework.jdbc.core.simple.JdbcClient.class,
            java.sql.DriverManager.class,
            java.sql.Connection.class,
            java.sql.Statement.class,
            java.sql.PreparedStatement.class,
            javax.sql.DataSource.class);

    /**
     * 裸 JDBC 棘轮基线（类 → 理由）。业务单表 CRUD 走 MyBatis-Plus；以下三类是 MP 的
     * 能力边界，登记放行。新类加进来必须在 PR 里论证属于同一类：
     * <ul>
     *   <li><b>PG/方言专有 SQL</b>——jsonb/向量操作符、批量写、清理任务等 wrapper
     *       表达不了的语句；</li>
     *   <li><b>非业务库引擎</b>——DuckDB / Doris / SQLite / pgvector，不在 MP 管辖；</li>
     *   <li><b>启动期修复</b>——StartupTaskRecovery。</li>
     * </ul>
     * <p>方言探测类副本已于 B70 归一到 {@code DatabaseDialects.isPostgres}（原
     * detectPostgres 八处复制清零，相应白名单条目随之摘除）。</p>
     */
    private static final Map<String, String> JDBC_BASELINE = Map.ofEntries(
            Map.entry("com.ragagent.common.jdbc.DatabaseDialects", "方言探测（收敛点）"),
            Map.entry("com.ragagent.config.StartupTaskRecovery", "启动期任务修复"),
            Map.entry("com.ragagent.agent.skills.SkillCatalogService", "PG 专有 SQL（技能目录）"),
            Map.entry("com.ragagent.agent.tools.data.AnalysisDuckDbJdbc", "非业务库引擎（DuckDB）"),
            Map.entry("com.ragagent.knowledge.repository.KnowledgeSpanRepository", "PG 专有 SQL（span 批量写）"),
            Map.entry("com.ragagent.knowledge.service.HousekeepingService", "PG 专有 SQL（超时清理）"),
            Map.entry("com.ragagent.knowledge.storage.TenantStorageService", "PG 专有 SQL（存储用量）"),
            Map.entry("com.ragagent.retrieval.engine.PgVectorEngineRepository", "非业务库引擎（pgvector 管理）"),
            Map.entry("com.ragagent.retrieval.engine.PgVectorRetrieveRepository", "非业务库引擎（pgvector 检索）"),
            Map.entry("com.ragagent.retrieval.engine.VectorStoreService", "非业务库引擎（pgvector 读写）"),
            Map.entry("com.ragagent.retrieval.engine.doris.DorisRetrieveRepository", "非业务库引擎（Doris）"),
            Map.entry("com.ragagent.retrieval.engine.doris.JdbcDorisSqlExecutor", "非业务库引擎（Doris 连接池）"),
            Map.entry("com.ragagent.retrieval.engine.sqlite.SqliteRetrieveRepository", "非业务库引擎（SQLite）"),
            Map.entry("com.ragagent.retrieval.engine.sqlite.SqliteSearchOps", "非业务库引擎（SQLite）"),
            Map.entry("com.ragagent.retrieval.engine.sqlite.SqliteWriteOps", "非业务库引擎（SQLite）"),
            Map.entry("com.ragagent.session.service.AgentToolBackends", "PG 专有 SQL（工具后端）"),
            Map.entry("com.ragagent.session.service.AgentToolKbBackends", "PG 专有 SQL（工具知识库后端）"),
            Map.entry("com.ragagent.session.service.AgentWebPages", "PG 专有 SQL（网页缓存）"),
            Map.entry("com.ragagent.session.service.SessionKnowledgeQaService", "PG 专有 SQL（多表 JOIN 标签检索）"),
            Map.entry("com.ragagent.system.service.SystemInfoService", "PG 专有 SQL（系统元数据）"),
            Map.entry("com.ragagent.vectorstore.service.VectorStoreConfigService", "PG 专有 SQL（向量库配置）"),
            Map.entry("com.ragagent.memory.mapper.MemoryVectorStore", "列存在性探测（JDBC 元数据）"),
            Map.entry("com.ragagent.storage.mapper.ResourceRepository", "PG 专有 SQL（存储资源查询，JdbcClient）"),
            Map.entry("com.ragagent.storage.mapper.StorageBackendRepository", "PG 专有 SQL（存储后端元数据，JdbcClient）"));

    @Test
    @DisplayName("A7：裸 JDBC（JdbcTemplate/java.sql 连接与语句/DataSource）只许白名单类")
    void rawJdbcOnlyFromWhitelist() {
        Set<String> actual = new TreeSet<>();
        for (JavaClass clazz : MAIN) {
            if (isRawJdbcUser(clazz)) {
                actual.add(topLevel(clazz).getName());
            }
        }
        ratchet("A7 裸 JDBC", JDBC_BASELINE.keySet(), actual);
    }

    /**
     * MyBatis TypeHandler 是框架回调扩展点——PreparedStatement/ResultSet 由框架传入，
     * 属于 MyBatis 体系内，不算裸 JDBC。匿名/内部类归属其顶层类（同一源文件同责）。
     */
    private static boolean isRawJdbcUser(JavaClass clazz) {
        if (clazz.isAssignableTo(org.apache.ibatis.type.BaseTypeHandler.class)) {
            return false;
        }
        for (JavaCall<?> call : clazz.getMethodCallsFromSelf()) {
            for (Class<?> type : JDBC_TARGET_TYPES) {
                if (call.getTarget().getOwner().isAssignableTo(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JavaClass topLevel(JavaClass clazz) {
        JavaClass cur = clazz;
        while (cur.getEnclosingClass().isPresent()) {
            cur = cur.getEnclosingClass().get();
        }
        return cur;
    }

    // ── A8 禁字符串列名 wrapper ─────────────────────────────────────────────

    /** 字符串列名 wrapper 的类型（应改用 LambdaQueryWrapper / LambdaUpdateWrapper）。 */
    private static final Set<String> STRING_WRAPPER_TYPES = Set.of(
            "com.baomidou.mybatisplus.core.conditions.query.QueryWrapper",
            "com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper",
            "com.baomidou.mybatisplus.core.conditions.query.QueryChainWrapper",
            "com.baomidou.mybatisplus.core.conditions.update.UpdateChainWrapper");

    /** {@code Wrappers} 上产出字符串 wrapper 的工厂方法（lambdaQuery/lambdaUpdate 不在内）。 */
    private static final Set<String> STRING_WRAPPER_FACTORY_METHODS = Set.of("query", "update", "emptyWrapper");

    /**
     * 棘轮基线：唯一保留项 {@code MessageRepository.update}——字符串 UpdateWrapper 是
     * 作者实测后的刻意选择：jsonb 列必须靠三参 {@code set(col, val, "typeHandler=…")}
     * 显式挂处理器，方法注释里记载了 H2 下退化形态的具体报错。其余存量（含全限定名
     * 形式，grep 之前漏数的）已于 2026-10-05 分三批 + knowledge 硬文件全部 Lambda 化。
     */
    private static final Set<String> STRING_WRAPPER_BASELINE = Set.of(
            "com.ragagent.session.mapper.MessageRepository");

    @Test
    @DisplayName("A8：禁字符串列名 wrapper（new QueryWrapper/UpdateWrapper、Wrappers.query/update/emptyWrapper）")
    void noStringColumnWrappers() {
        ratchet("A8 字符串 wrapper", STRING_WRAPPER_BASELINE, violatingClasses(clazz -> {
            for (JavaConstructorCall call : clazz.getConstructorCallsFromSelf()) {
                if (STRING_WRAPPER_TYPES.contains(call.getTarget().getOwner().getName())) {
                    return true;
                }
            }
            for (JavaCall<?> call : clazz.getMethodCallsFromSelf()) {
                if (call.getTarget().getOwner().getName().equals(com.baomidou.mybatisplus.core.toolkit.Wrappers.class
                        .getName())
                        && STRING_WRAPPER_FACTORY_METHODS.contains(call.getTarget().getName())) {
                    return true;
                }
            }
            return false;
        }));
    }

    // ── A9 .last( 只许纯字符串字面量 ────────────────────────────────────────

    /**
     * 棘轮基线：已清空（2026-10-05）——13 文件 23 处拼接全部迁 {@code PageRequests}：
     * 手搓分页 → {@code range}、任意 offset → {@code atOffset}、行帽 → {@code cap}。
     * 本规则现为零基线纯禁令；{@code .last("LIMIT 1")} 等纯字面量仍合法。
     */
    private static final Map<String, String> LAST_CONCAT_BASELINE = Map.of();

    /**
     * 后端源码根（B116 多模块）：本类在 {@code :server} 里运行，工作目录是 {@code server/}，
     * 故共享内核（{@code common}/{@code event}，已抽到 {@code :common}）用相对路径指过去。
     * 不存在的根由调用方跳过 ⇒ 单模块布局下仍然可用。
     * 包级可见：{@link ClasspathResourcesTest} 的反漂移扫描复用同一份根清单（B118）。
     */
    static java.util.List<java.nio.file.Path> backendSourceRoots(String sourceSet) {
        return SourceRoots.backend(sourceSet);   // B165：仓库根定位（多模块夹具）
    }

    @Test
    @DisplayName("A9：.last(...) 参数必须是纯字符串字面量（拼接=注入面+方言漂移）")
    void lastOnlyConstantStrings() throws java.io.IOException {
        Set<String> actual = new TreeSet<>();
        for (java.nio.file.Path root : backendSourceRoots("main/java")) {
            if (!java.nio.file.Files.isDirectory(root)) {
                continue;
            }
            try (var walk = java.nio.file.Files.walk(root)) {
                for (java.nio.file.Path file : walk.filter(java.nio.file.Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java")).toList()) {
                    boolean[] inBlockComment = {false};
                    for (String line : java.nio.file.Files.readAllLines(file)) {
                        if (isConcatLast(stripComments(line, inBlockComment))) {
                            actual.add(file.toString().replace('\\', '/'));
                            break;
                        }
                    }
                }
            }
        }
        ratchet("A9 .last 拼接", LAST_CONCAT_BASELINE.keySet(), actual);
    }

    /** 剥掉块注释/行注释（保留字符串字面量），注释里的 {@code .last(} 样例不算违例。 */
    private static String stripComments(String line, boolean[] inBlockComment) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            if (inBlockComment[0]) {
                int end = line.indexOf("*/", i);
                if (end < 0) {
                    return sb.toString();
                }
                inBlockComment[0] = false;
                i = end + 2;
            } else if (i + 1 < line.length() && line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
                inBlockComment[0] = true;
                i += 2;
            } else if (i + 1 < line.length() && line.charAt(i) == '/' && line.charAt(i + 1) == '/') {
                return sb.toString();
            } else if (line.charAt(i) == '"') {
                // 字符串字面量原样保留（含其中的 // 与 /*）；不做转义处理——本仓 SQL 字面量无嵌套引号
                int close = line.indexOf('"', i + 1);
                if (close < 0) {
                    return sb.append(line, i, line.length()).toString();
                }
                sb.append(line, i, close + 1);
                i = close + 1;
            } else {
                sb.append(line.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /** 行内出现 {@code .last(} 且参数不是单个纯字符串字面量 → 违例（注释里的样本同样命中，宁严勿漏）。 */
    private static boolean isConcatLast(String line) {
        int idx = line.indexOf(".last(");
        if (idx < 0) {
            return false;
        }
        String rest = line.substring(idx + ".last(".length()).trim();
        if (!rest.startsWith("\"")) {
            return true;
        }
        int close = rest.indexOf('"', 1);
        if (close < 0) {
            return true; // 字面量跨行：按违例处理
        }
        String tail = rest.substring(close + 1).trim();
        return !tail.isEmpty() && !tail.startsWith(")");
    }

    // ── 棘轮辅助 ────────────────────────────────────────────────────────────

    private static Set<String> violatingClasses(Predicate<JavaClass> detector) {
        Set<String> out = new TreeSet<>();
        for (JavaClass clazz : MAIN) {
            if (detector.test(clazz)) {
                out.add(clazz.getName());
            }
        }
        return out;
    }

    /**
     * 棘轮双断言：基线外新增违例=拦增量；基线条目不再违例=基线过期（也红——若检测逻辑
     * 空转，所有条目同时过期，规则不可能静默变绿）。与 A4 的 INSTALL_TARGETS、包级棘轮
     * 脚本同一套哲学。
     */
    private static void ratchet(String rule, Set<String> baseline, Set<String> actual) {
        Set<String> fresh = new TreeSet<>(actual);
        fresh.removeAll(baseline);
        assertThat(fresh)
                .as(rule + "：基线外新增违例——修掉，或论证属于同类场景后登记基线（附理由）")
                .isEmpty();
        Set<String> stale = new TreeSet<>(baseline);
        stale.removeAll(actual);
        assertThat(stale)
                .as(rule + "：基线条目已不再违例——清理已完成，请删除条目收紧基线")
                .isEmpty();
    }

    private static boolean isWiringPackage(String packageName) {
        return "com.ragagent.config".equals(packageName) || packageName.endsWith(".config");
    }

    private static Set<String> scannedPackages() {
        ConfigurationPropertiesScan scan =
                RagAgentApplication.class.getAnnotation(ConfigurationPropertiesScan.class);
        if (scan == null) {
            return Set.of();
        }
        Set<String> packages = new LinkedHashSet<>();
        packages.addAll(List.of(scan.value()));
        packages.addAll(List.of(scan.basePackages()));
        return packages;
    }


    // ── A10 分层倒挂 / A11 Mapper 包约定（2026-10-08 B91 补，当前零违例）─────────

    @Test
    @DisplayName("A10：service 不得依赖 controller；domain 不得依赖 service/controller")
    void noLayerInversions() {
        assertThat(MAIN.size()).as("主源集导入为空或过少：检查 ImportOption 的输出目录过滤").isGreaterThan(500);
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("依赖方向必须 controller → service → repository/mapper；"
                        + "service 回头引用 controller 即分层倒挂（B91 实测为 0，保持住）")
                .check(MAIN);
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
                .because("domain 是数据/领域模型，不得反向依赖 service/controller")
                .check(MAIN);
    }

    @Test
    @DisplayName("A11：*Mapper 接口必须落在 ..mapper.. 包（@MapperScan 范围）")
    void mapperInterfacesLiveInMapperPackages() {
        // 只约束顶层接口：@MapperScan 注册的是顶层接口，嵌套的 helper 接口（如
        // DorisSqlExecutor$RowMapper，JDBC 行映射用）不在其语义内。
        classes().that().areInterfaces().and().areNotNestedClasses().and().haveSimpleNameEndingWith("Mapper")
                .should().resideInAPackage("..mapper..")
                .because("@MapperScan(\"com.ragagent.**.mapper\") 只扫 mapper 包；"
                        + "放错包 = 启动期缺 Bean 或该 mapper 静默不注册")
                .check(MAIN);
    }

    @Test
    @DisplayName("A13：@EventListener/@PostConstruct/@PreDestroy 方法必须声明在 Spring 扫描到的类上")
    void lifecycleHooksMustLiveOnScannedBeans() {
        // 判据来自一次真实事故（B127 → B128）：把 @EventListener/@PreDestroy 随方法一起
        // 搬进"由门面 new 出来的协作者类"——那个类不是 Spring bean ⇒ 注解**静默失效**
        // （渠道不再随应用启动、停机钩子不再执行），而编译与 4,700+ 全量测试**全部照绿**。
        // 这类失效没有任何运行期报错，只能靠本规则兜住。
        //
        // 口径：方法所在类必须被 Spring 扫到。（探针实测修正过一次判据：只认 @Component 派生会把
        // @ConfigurationProperties 类误报——它们经 @ConfigurationPropertiesScan 注册，同样是 bean，
        // 且 A2 已保证这类类落在扫描名单覆盖的包内，两条规则正好接上。）
        // 由 @Bean 方法返回的生命周期对象不在此列——那是 SmartLifecycle 语义，不是方法注解。
        Set<String> actual = new TreeSet<>();
        for (JavaClass clazz : MAIN) {
            if (clazz.isMetaAnnotatedWith(Component.class)
                    || clazz.isAnnotatedWith(ConfigurationProperties.class)) {
                continue;
            }
            for (JavaMethod method : clazz.getMethods()) {
                if (method.isAnnotatedWith(EventListener.class)
                        || method.isAnnotatedWith(PostConstruct.class)
                        || method.isAnnotatedWith(PreDestroy.class)) {
                    actual.add(topLevel(clazz).getName() + "#" + method.getName());
                }
            }
        }
        assertThat(actual)
                .as("A13 钩子方法不在 Spring 扫描到的类上 ⇒ 注解静默失效（无报错、测试也绿）。"
                        + "修法：把钩子挪到 bean（如域门面）上，或把该类变成 bean")
                .isEmpty();
    }
}
