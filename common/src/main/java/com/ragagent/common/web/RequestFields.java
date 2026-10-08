package com.ragagent.common.web;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请求字段校验失败的 details 文案（单一实现在此，各域直接引用）。
 *
 * <p>字段名按 JSON/Java 惯例输出 camelCase——入参可以是旧写的 PascalCase，
 * 本类负责转写（{@code TenantID → tenantId}、{@code LLMModelID → llmModelId}）。</p>
 */
public final class RequestFields {

    private static final Pattern WORD = Pattern.compile("[A-Z]+(?![a-z])|[A-Z][a-z]*|[a-z0-9]+");

    private RequestFields() {
    }

    /** 字段校验文案：required / email / min / max 有专名，其余 tag 走兜底形态。 */
    public static String message(String field, String tag) {
        String name = fieldName(field);
        return switch (tag == null ? "" : tag) {
            case "required" -> "field '" + name + "' is required";
            case "email" -> "field '" + name + "' is not a valid email address";
            case "min" -> "field '" + name + "' is below the minimum";
            case "max" -> "field '" + name + "' is above the maximum";
            default -> "field '" + name + "' failed validation: " + tag;
        };
    }

    /** 类型不符：{@code field 'name' must be a string, got number} / {@code … an integer …}。 */
    public static String wrongType(String field, String expected, String actual) {
        boolean vowel = !expected.isEmpty() && "aeiou".indexOf(Character.toLowerCase(expected.charAt(0))) >= 0;
        return "field '" + fieldName(field) + "' must be " + (vowel ? "an " : "a ")
                + expected + ", got " + actual;
    }

    /** 必须是整数（小数形态）。 */
    public static String mustBeInteger(String field) {
        return "field '" + fieldName(field) + "' must be an integer";
    }

    /** 数值越界。 */
    public static String outOfRange(String field) {
        return "field '" + fieldName(field) + "' is out of range";
    }

    /** PascalCase / 缩写 → camelCase（{@code TenantID → tenantId}、{@code LLMModelID → llmModelId}）。 */
    static String fieldName(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        Matcher m = WORD.matcher(field);
        StringBuilder out = new StringBuilder(field.length());
        boolean first = true;
        while (m.find()) {
            String w = m.group();
            out.append(first ? w.toLowerCase(Locale.ROOT)
                    : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase(Locale.ROOT));
            first = false;
        }
        return out.length() == 0 ? field : out.toString();
    }
}
