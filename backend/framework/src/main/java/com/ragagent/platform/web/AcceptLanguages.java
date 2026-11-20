package com.ragagent.platform.web;

/**
 * {@code Accept-Language} 头的首个语言标签解析。
 *
 * <p>只做 header 解析，不读 {@code RAGAGENT_LANGUAGE}：需要"启动配置优先"的场景走
 * wiki 侧的语言解析器（它还带线程上下文），本类只服务"按请求头本地化展示"的路径，
 * 让基础域不必为一个 header 工具依赖业务域的契约。</p>
 */
public final class AcceptLanguages {

    private AcceptLanguages() {
    }

    /** 无 {@code Accept-Language} 时的展示语言（与系统默认语言一致）。 */
    public static final String DEFAULT_LOCALE = "zh-CN";

    /** 首个标签；无头或空白时回落到 {@link #DEFAULT_LOCALE}，永不返回空串。 */
    public static String localeOrFallback(String acceptLanguage) {
        String tag = firstTag(acceptLanguage);
        return tag.isEmpty() ? DEFAULT_LOCALE : tag;
    }

    /** 取首个标签（去掉 {@code q} 权重与空白）；无头或空白返回 {@code ""}。 */
    public static String firstTag(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "";
        }
        String first = acceptLanguage.split(",", 2)[0].trim();
        return first.split(";", 2)[0].trim();
    }
}
