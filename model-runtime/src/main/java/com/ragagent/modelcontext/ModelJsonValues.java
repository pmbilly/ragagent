package com.ragagent.modelcontext;

import com.ragagent.platform.web.ContractJsonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * modelcontext 包内的 JSON 语义小工具：解析容错与 double 归一。
 * 编码统一走 agent.tools 的 ToolJson（map 键排序 + HTML 转义 + 64 位浮点）。
 */
final class ModelJsonValues {

    static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            // 上下文预算读写的载荷含契约类（TokenUsage 等）：线格式在 mixin 模块里
            .addModule(new ContractJsonModule())
            .build();

    private ModelJsonValues() {
    }

    /** 解析失败返回 null。 */
    static JsonNode parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 一切 JSON 数字都按 double 读入。凡是要重新序列化的树，
     * 先把整型/大数/Decimal 节点归一成 DoubleNode，编码字节才稳定
     * （大整数超出 double 精度时按同样规则损失精度）。
     */
    static JsonNode numbersAsDouble(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            ObjectNode out = MAPPER.createObjectNode();
            for (var e : obj.properties()) {
                out.set(e.getKey(), numbersAsDouble(e.getValue()));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode item : arr) {
                out.add(numbersAsDouble(item));
            }
            return out;
        }
        if (node.isNumber() && !node.isFloatingPointNumber()) {
            return new DoubleNode(node.doubleValue());
        }
        return node;
    }

    /**
     * JSON 值等价比较：数字一律按 double 语义、对象键序无关的深比较。
     * 任一侧解析失败 → 退回字符串相等。
     */
    static boolean jsonEquivalent(String left, String right) {
        JsonNode lv = parse(left);
        JsonNode rv = parse(right);
        if (lv == null || rv == null) {
            return left.equals(right);
        }
        return deepEquals(lv, rv);
    }

    private static boolean deepEquals(JsonNode a, JsonNode b) {
        if (a.isObject() && b.isObject()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (var e : a.properties()) {
                JsonNode bv = b.get(e.getKey());
                if (bv == null || !deepEquals(e.getValue(), bv)) {
                    return false;
                }
            }
            return true;
        }
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!deepEquals(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.doubleValue() == b.doubleValue();
        }
        if (a.isString() && b.isString()) {
            return a.asString().equals(b.asString());
        }
        return a.equals(b);
    }
}
