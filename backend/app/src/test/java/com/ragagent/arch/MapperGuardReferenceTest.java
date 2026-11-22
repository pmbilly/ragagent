package com.ragagent.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 持久化拦截器白名单的真实性：framework 的租户/软删/全表写守卫按 <b>字符串</b> 匹配 Mapper 语句，
 * 字符串一旦与真实方法名脱钩（Mapper 改名、方法重命名），拦截会静默失效——编译期毫无提示。
 *
 * <p>本测试把那份白名单拉回可验证状态：逐条解析成「类 + 方法」，在 app 的测试类路径上反射校验。
 * 只校验"方法与类是否存在"，不改运行时机制。</p>
 */
class MapperGuardReferenceTest {

    private static final Path REPO = findRepoRoot();

    private static final List<Path> GUARD_SOURCES = List.of(
            REPO.resolve("backend/framework/src/main/java/com/ragagent/framework/persistence/TenantFilterGuard.java"),
            REPO.resolve("backend/framework/src/main/java/com/ragagent/framework/persistence/SoftDeleteFilterGuard.java"),
            REPO.resolve("backend/framework/src/main/java/com/ragagent/framework/persistence/FullTableWriteGuard.java"));

    /** 形如 "com.ragagent.auth.mapper.TenantMapper.selectById"。 */
    private static final Pattern STATEMENT_ID =
            Pattern.compile("\"(com\\.ragagent\\.[\\w.]*\\.[A-Z]\\w*\\.[a-z]\\w*)\"");

    private static Path findRepoRoot() {
        Path p = Path.of("").toAbsolutePath().normalize();
        while (p != null) {
            if (Files.isDirectory(p.resolve("backend/framework")) && Files.isDirectory(p.resolve("backend/app"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("找不到仓库根（缺 backend/framework 或 backend/app）");
    }

    @Test
    @DisplayName("拦截器白名单里的每个语句 id 都指向真实存在的 Mapper 方法")
    void guardedStatementsResolveToRealMapperMethods() throws IOException {
        List<String> unresolved = new ArrayList<>();
        int total = 0;
        for (Path source : GUARD_SOURCES) {
            for (String id : statementIds(source)) {
                total++;
                String reason = resolve(id);
                if (reason != null) {
                    unresolved.add(source.getFileName() + " → " + id + "（" + reason + "）");
                }
            }
        }
        assertThat(unresolved)
                .as("字符串白名单与真实 Mapper 脱钩后拦截会静默失效；共校验 %d 条", total)
                .isEmpty();
        assertThat(total).as("没有解析到任何语句 id，正则或文件路径可能已过期").isGreaterThan(50);
    }

    private static List<String> statementIds(Path source) throws IOException {
        String text = Files.readString(source);
        List<String> ids = new ArrayList<>();
        Matcher m = STATEMENT_ID.matcher(text);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    /** 返回 null 表示可解析；否则返回原因。 */
    private static String resolve(String statementId) {
        int methodDot = statementId.lastIndexOf('.');
        String classFqn = statementId.substring(0, methodDot);
        String methodName = statementId.substring(methodDot + 1);
        Class<?> type;
        try {
            type = Class.forName(classFqn);
        } catch (ClassNotFoundException e) {
            return "类不存在";
        }
        return Stream.concat(Arrays.stream(type.getMethods()), Arrays.stream(type.getDeclaredMethods()))
                .map(Method::getName)
                .anyMatch(methodName::equals)
                ? null
                : "方法不存在";
    }
}
