package com.ragagent.contract.knowledge.support;

import com.ragagent.contract.json.JsonValues;
import java.util.Locale;

/**
 * chat 解析引擎的规则解析。
 * <p>两份规则来源语义一致：agent 配置（上传时回落）与租户配置（parse 时最终回落）。
 * 对 {@code engine} 去首尾空白；都不命中或无规则 → 类型默认（仅 ppt/pptx → markitdown，
 * Java 侧不设该钩子。</p>
 */
public final class ParserEngineRules {

    private ParserEngineRules() {
    }

    /** 去首尾空白 + 小写 + 去前导点。 */
    public static String normalize(String fileType) {
        String s = fileType == null ? "" : trimSpace(fileType).toLowerCase(Locale.ROOT);
        return s.startsWith(".") ? s.substring(1) : s;
    }

    /** defaultParserEngineByType 仅 ppt/pptx → markitdown。 */
    public static String defaultEngine(String fileType) {
        String ft = normalize(fileType);
        return "ppt".equals(ft) || "pptx".equals(ft) ? "markitdown" : "";
    }

    /**
 * 规则数组形态（agent config 的 chat_parser_engine_rules）。
 *
 * <p><b></b>：入参由 {@code JsonNode} 改为**纯 JDK 值**（对象→Map、数组→List），
 * 读面走契约侧 {@link JsonValues}（语义与 {@code JsonNode} 逐条对齐，见其类注释）；
 * 调用方传 JsonNode 时先经 {@code JsonMappers#toJavaValue} 归一。</p>
 */
    public static String resolve(Object rules, String fileType) {
        if (JsonValues.isArray(rules)) {
            String normalized = normalize(fileType);
            for (Object rule : JsonValues.elements(rules)) {
                if (!JsonValues.isObject(rule)) {
                    continue;
                }
                String engine = trimSpace(JsonValues.asText(JsonValues.get(rule, "engine"), ""));
                Object fileTypes = JsonValues.get(rule, "file_types");
                if (!JsonValues.isArray(fileTypes)) {
                    continue;
                }
                for (Object candidate : JsonValues.elements(fileTypes)) {
                    if (normalize(JsonValues.asText(candidate, "")).equals(normalized)) {
                        return engine;
                    }
                }
            }
        }
        return defaultEngine(fileType);
    }

    static String trimSpace(String s) {
        if (s == null) {
            return "";
        }
        int start = 0;
        int end = s.length();
        while (start < end && isUnicodeWhitespace(s.codePointAt(start))) {
            start += Character.charCount(s.codePointAt(start));
        }
        while (end > start && isUnicodeWhitespace(s.codePointBefore(end))) {
            end -= Character.charCount(s.codePointBefore(end));
        }
        return s.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r'
                || c == 0x85 || c == 0xA0
                || Character.isSpaceChar(c)
                || Character.getType(c) == Character.SPACE_SEPARATOR
                || Character.getType(c) == Character.LINE_SEPARATOR
                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR;
    }
}
