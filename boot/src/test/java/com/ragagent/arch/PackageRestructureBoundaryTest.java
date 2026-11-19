package com.ragagent.arch;




import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分包重构期（2026-11-19 起）的域间边界规则。
 *
 * <p>与 {@link ArchitectureRulesTest} 的分工：那个类守住已经定型的代码级约定（A1~A20），
 * 这个类只守本次重构新增的两条——两条都是<b>棘轮</b>，基线只许减不许增。</p>
 *
 * <ol>
 *   <li><b>跨域直连</b>：A 域不得 import B 域的 {@code repository / mapper / domain /
 *       service / controller} 子包。跨域只允许走 {@code api}（对外契约）与 {@code dto}
 *       （数据形状）。基线里的两条边是本次普查的存量：
 *       {@code knowledge -> wiki.service}（入库收尾实现落在 wiki 域）与
 *       {@code session -> agentmanagement.service}（会话直接取自定义智能体实现）。
 *       第一条同时是 {@code knowledge ⇄ wiki} 循环的一半，解环时基线随之收紧。</li>
 *   <li><b>注解 SQL 棘轮</b>：主码方法上的 {@code @Select / @Insert / @Update / @Delete}
 *       只许减。注解 SQL 绕开实体 resultMap（jsonb/TypeHandler 不生效），且迁移时
 *       无法被 Wrapper 规则约束；本次不要求清零（存量 300+ 方法，属数据访问专项），
 *       只保证分包重构不会顺手再添。</li>
 * </ol>
 *
 * <p>两条规则都<b>不引入新依赖</b>：用 ArchUnit 已导入的 {@code MAIN} 集合表达，
 * 基线是代码内常量（与 A6/A8/A9 同口径），不用 FreezingArchRule 的存储文件。</p>
 *
 * @author jiangguifan
 */
class PackageRestructureBoundaryTest {

    /** 19 个业务域；`com.ragagent` 下的其它二级包（contract/platform/agent 等）不算域。 */
    private static final Set<String> DOMAINS = Set.of(
            "agentmanagement", "agenttools", "audit", "auth", "datasource", "embedchannel", "favorite",
            "im", "initialization", "knowledge", "mcp", "memory", "model", "session", "storage",
            "system", "vectorstore", "websearch", "wiki");

    /** 跨域只允许进 `api` / `dto`；以下子包直连即违规。 */
    private static final Set<String> FORBIDDEN_TARGETS =
            Set.of("repository", "mapper", "domain", "service", "controller", "adapter");

    /**
     * 跨域直连基线（2026-11-19 普查，13 处 import / 2 条边；P2-4 补第 3 条）。
     *
     * <p>格式 {@code <from> -> <to>.<子包>}。新增条目必须先在方案文档里论证，
     * 否则就是绕过 {@code api} 又开了一条暗通道。</p>
     *
     * <p>{@code system -> auth.adapter} 是 P2 移动时才暴露的：system 的两个 service 直接
     * 注入 {@code AdminUserAdapter}（具体类，字段名却叫 {@code adminUserPort}），原先藏在
     * {@code auth.api} 包里，规则看不到。根治要把它的方法提成 {@code contract.auth} 端口，
     * 涉及 {@code CreateUserResult} 上提，另批处理（见 HANDOFF §11.1 第 20 条）。</p>
     */
    private static final Set<String> CROSS_DOMAIN_BASELINE = Set.of(
            "knowledge -> wiki.service",
            "session -> agentmanagement.service",
            "system -> auth.adapter");

    /**
     * 注解 SQL 基线：主码里带 {@code @Select/@Insert/@Update/@Delete} 的方法数（2026-11-19 实测）。
     *
     * <p>只拦增量：分包重构（移动包、合并 Ops）不应该改变这个数字。</p>
     */
    private static final long ANNOTATED_SQL_BASELINE = 282L;

    private static final Set<String> SQL_ANNOTATIONS = Set.of(
            "org.apache.ibatis.annotations.Select",
            "org.apache.ibatis.annotations.Insert",
            "org.apache.ibatis.annotations.Update",
            "org.apache.ibatis.annotations.Delete");

    @Test
    @DisplayName("R-1：跨域不得直连对方的 repository/mapper/domain/service/controller（棘轮）")
    void crossDomainDirectDependency() {
        Set<String> actual = new TreeSet<>();
        for (JavaClass clazz : ArchitectureRulesTest.MAIN) {
            String from = domainOf(clazz.getPackageName());
            if (from == null) {
                continue;
            }
            for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                String targetPkg = dep.getTargetClass().getPackageName();
                String to = domainOf(targetPkg);
                if (to == null || to.equals(from)) {
                    continue;
                }
                String sub = subPackageOf(targetPkg, to);
                if (FORBIDDEN_TARGETS.contains(sub)) {
                    actual.add(from + " -> " + to + "." + sub);
                }
            }
        }
        ratchet("R-1 跨域直连", CROSS_DOMAIN_BASELINE, actual);
    }

    @Test
    @DisplayName("R-2：主码注解 SQL（@Select/@Insert/@Update/@Delete）只减不增")
    void annotatedSqlRatchet() {
        long actual = ArchitectureRulesTest.MAIN.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .filter(method -> SQL_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith))
                .count();
        assertThat(actual)
                .as("R-2 注解 SQL：基线 %d 个方法，实测 %d；新增即红（自定义 SQL 走 Mapper XML "
                        + "或 Repository），清理后同步下调基线", ANNOTATED_SQL_BASELINE, actual)
                .isEqualTo(ANNOTATED_SQL_BASELINE);
    }

    /** `com.ragagent.<域>...` → `<域>`；不在 19 域内返回 null。 */
    private static String domainOf(String packageName) {
        String prefix = "com.ragagent.";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        String second = dot < 0 ? rest : rest.substring(0, dot);
        return DOMAINS.contains(second) ? second : null;
    }

    /** `com.ragagent.<域>.<子包>...` → `<子包>`；域根包下直接放类时返回空串。 */
    private static String subPackageOf(String packageName, String domain) {
        String rest = packageName.substring(("com.ragagent." + domain).length());
        if (rest.startsWith(".")) {
            rest = rest.substring(1);
        }
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** 与 {@link ArchitectureRulesTest} 同口径的双断言棘轮：新增即红，条目不再违例也红。 */
    private static void ratchet(String rule, Set<String> baseline, Set<String> actual) {
        Set<String> fresh = new TreeSet<>(actual);
        fresh.removeAll(baseline);
        assertThat(fresh)
                .as(rule + "：基线外新增——跨域只能走对方的 api 包；确属必要先在方案文档论证并登记")
                .isEmpty();
        Set<String> stale = new TreeSet<>(baseline);
        stale.removeAll(actual);
        assertThat(stale)
                .as(rule + "：基线条目已不再违例——清理已完成，请删除条目收紧基线")
                .isEmpty();
    }
}
