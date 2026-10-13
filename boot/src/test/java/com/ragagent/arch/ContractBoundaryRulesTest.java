package com.ragagent.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B258（方案 §12.2 头两条）：**契约面纯度** + **跨域边界** + **当前模块布局的 DAG 方向**。
 *
 * <p>为什么单开一个类：`ArchitectureRulesTest` 已 1000+ 行（大文件棘轮盯的是主源码，但规则
 * 文件也不该无限膨胀）；这三条规则与 contracts 面同批引入，单独成篇便于演进。</p>
 */
class ContractBoundaryRulesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ragagent");

    /**
     * R1 · 契约面只依赖 JDK。
     *
     * <p>方案 §8.1：`contracts` 零 Spring / MyBatis / Redis / Jackson / Langfuse / 云 SDK——
     * wire 序列化由 `controller` / `dto` 承担。只要契约面沾上框架，跨模块的"公开 API"就
     * 变成框架耦合，模块提取（阶段 6）会一路被拖住。</p>
     */
    @Test
    @DisplayName("B258-R1：com.ragagent.contract 只依赖 JDK 与自身")
    void contractsFaceDependsOnJdkOnly() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : MAIN) {
            if (!clazz.getPackageName().startsWith("com.ragagent.contract")) {
                continue;
            }
            for (var dep : clazz.getDirectDependenciesFromSelf()) {
                String target = normalizeDescriptor(dep.getTargetClass().getName());
                boolean allowed = target.startsWith("java.") || target.startsWith("javax.")
                        || target.startsWith("com.ragagent.contract.");
                if (!allowed) {
                    violations.add(clazz.getName() + " → " + target);
                }
            }
        }
        assertThat(violations)
                .as("契约面引入框架依赖：改用 JDK 类型，序列化交给 controller/dto（方案 §8.1）")
                .isEmpty();
    }

    /**
     * R2 · 跨域边界：`client` 不得直接引用**其他域**的 `mapper`。
     *
     * <p>§12.2：业务域之间只能经 contracts；client 直连他域 mapper 等于把持久层形状当
     * 跨域接口用（域内 mapper 变更会静默波及调用方）。允许引用**本域** mapper（同域实现细节）。</p>
     */
    @Test
    @DisplayName("B258-R2：client 不得引用其他域的 mapper")
    void clientsDoNotTouchForeignMappers() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : MAIN) {
            if (!clazz.getPackageName().contains(".client")) {
                continue;
            }
            String owner = domainOf(clazz.getPackageName());
            for (var dep : clazz.getDirectDependenciesFromSelf()) {
                String targetPkg = dep.getTargetClass().getPackageName();
                if (!targetPkg.contains(".mapper")) {
                    continue;
                }
                if (!domainOf(targetPkg).equals(owner)) {
                    violations.add(clazz.getName() + " → " + dep.getTargetClass().getName());
                }
            }
        }
        assertThat(violations)
                .as("client 越过域边界直连他域 mapper：改走该域公开用例或契约 SPI（§12.2）")
                .isEmpty();
    }

    /**
     * R3 · 模块 DAG 方向（当前模块布局：common / engine / domains / boot）。
     *
     * <p>`contracts` 面的宿主是 `:common`，因此 `:common` 是底座：它**不得**反向依赖
     * `:engine` / `:domains` / `:boot`；`:engine` 是第二层，不得依赖 `:domains` / `:boot`
     * （方案 §8 的模块 DAG 在现状布局下的可执行切片）。</p>
     */
    @Test
    @DisplayName("B258-R3：模块 DAG 方向（common 不反向依赖；engine 不依赖 domains）")
    void moduleDagPointsDownward() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : MAIN) {
            String pkg = clazz.getPackageName();
            boolean commonSide = isCommonSide(pkg);
            boolean engineSide = pkg.startsWith("com.ragagent.llm")
                    || pkg.startsWith("com.ragagent.retrieval")
                    || pkg.startsWith("com.ragagent.modelcontext")
                    || pkg.startsWith("com.ragagent.rerank")
                    || pkg.startsWith("com.ragagent.webfetch")
                    || pkg.startsWith("com.ragagent.tracing");
            if (!commonSide && !engineSide) {
                continue;
            }
            for (var dep : clazz.getDirectDependenciesFromSelf()) {
                String target = dep.getTargetClass().getPackageName();
                boolean bad = commonSide && isBusinessDomain(target)
                        || engineSide && isBusinessDomain(target);
                if (bad) {
                    violations.add(clazz.getName());
                }
            }
        }
        ratchet("B258-R3 底座/引擎→业务域", DAG_DOWNWARD_BASELINE, new TreeSet<>(violations));
    }

    /**
     * R3 基线（棘轮：只许减不许增）：**当前为 0 处**——底座/引擎层对业务域的依赖已清。
     *
     * <p><b>纠错记录</b>：起草时把 `com.ragagent.embedding` 当成业务域，于是把检索引擎/
     * Langfuse 装饰器误判成 4 处违例；查实 `embedding` 是 **engine 模块自己的包**（与
     * domains 侧同名包不是一回事）。业务域清单现改为按模块实际目录派生（domains 有、
     * engine 无），避免同名包假阳性。</p>
     */
    private static final Set<String> DAG_DOWNWARD_BASELINE = Set.of();

    /** 棘轮：新违例必红；基线条目已修好也必须删条目（不允许静默过期）。 */
    private static void ratchet(String rule, Set<String> baseline, Set<String> actual) {
        Set<String> fresh = new TreeSet<>(actual);
        fresh.removeAll(baseline);
        assertThat(fresh).as(rule + "：基线外新增违例（不得放宽依赖方向）").isEmpty();
        Set<String> stale = new TreeSet<>(baseline);
        stale.removeAll(actual);
        assertThat(stale).as(rule + "：基线条目已不再违例——请删条目收紧基线").isEmpty();
    }

    /** ArchUnit 对数组类型给出 JVM 描述符（{@code [Lcom.x.Y;}）——归一化成类名再判。 */
    private static String normalizeDescriptor(String name) {
        String n = name;
        while (n.startsWith("[")) {
            n = n.substring(1);
        }
        if (n.startsWith("L") && n.endsWith(";")) {
            n = n.substring(1, n.length() - 1);
        }
        return n;
    }

    /**
     * R4 · 平台面（B259 起收纳 `com.ragagent.platform`）不得依赖业务域与三个 runtime 的实现。
     *
     * <p>§8 的 DAG：`platform → contracts`。平台放的是事件/流/任务/HTTP/持久化助手/文本与解析
     * 等**技术底座**；一旦它反向依赖业务域或 runtime，模块提取与"顶层不依赖具体实现"就同时破产。</p>
     */
    @Test
    @DisplayName("B259-R4：platform 面不得依赖业务域与 runtime 实现")
    void platformFaceStaysBelowBusiness() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : MAIN) {
            if (!clazz.getPackageName().startsWith("com.ragagent.platform")) {
                continue;
            }
            for (var dep : clazz.getDirectDependenciesFromSelf()) {
                String pkg = dep.getTargetClass().getPackageName();
                if (isBusinessDomain(pkg) || isRuntimeImplementation(pkg)) {
                    violations.add(clazz.getName() + " → " + dep.getTargetClass().getName());
                }
            }
        }
        ratchet("B259-R4 platform→业务域/运行时", Set.of(), new TreeSet<>(violations));
    }

    /** 三个 runtime 的实现包面（含 engine 模块内与领域侧同名包）。 */
    private static boolean isRuntimeImplementation(String packageName) {
        for (String r : List.of("agent", "llm", "retrieval", "modelcontext", "rerank", "webfetch",
                "embedding", "chatpipeline", "tracing.decorators", "agenttools", "agentmanagement")) {
            if (packageName.equals("com.ragagent." + r) || packageName.startsWith("com.ragagent." + r + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String domainOf(String packageName) {
        String[] parts = packageName.split("\\.");
        return parts.length >= 3 ? parts[2] : packageName;
    }

    /** `:common` 侧的包面（B258 起含契约面）。 */
    private static boolean isCommonSide(String packageName) {
        return packageName.startsWith("com.ragagent.contract")
                || packageName.startsWith("com.ragagent.platform")
                || packageName.startsWith("com.ragagent.common")
                || packageName.startsWith("com.ragagent.event")
                || packageName.startsWith("com.ragagent.platform.stream")
                || packageName.startsWith("com.ragagent.tracing.langfuse");
    }

    /** 业务域（domains 模块）包面。 */
    private static boolean isBusinessDomain(String packageName) {
        for (String d : List.of(
                "agentmanagement",
                "agenttools",
                "approval",
                "audit",
                "auth",
                "datasource",
                "embedchannel",
                "evaluation",
                "favorite",
                "im",
                "initialization",
                "knowledge",
                "mcp",
                "memory",
                "model",
                "session",
                "settings",
                "storage",
                "system",
                "tenant",
                "vectorstore",
                "websearch",
                "wiki"
        )) {
            if (packageName.equals("com.ragagent." + d) || packageName.startsWith("com.ragagent." + d + ".")) {
                return true;
            }
        }
        return false;
    }
}
