package com.ragagent.platform.persistence;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.ragagent.platform.web.JsonMappers;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * PostgreSQL jsonb 写入适配（jsonb 列的透明存取）。
 *
 * <p>MyBatis-Plus 原生 JacksonTypeHandler 绑定 Jackson 2；Jackson 3 迁移后直接继承
 * {@link AbstractJsonTypeHandler}，读写两侧统一走 {@link JsonMappers#lenient()}。</p>
 *
 * <p>PG JDBC 用 {@code setObject(OTHER)} → OID unknown，服务端按目标列类型 jsonb 强转；
 * H2 对 OTHER 的 setObject 按普通对象落 VARCHAR，测试库兼容。</p>
 */
public class PgJsonTypeHandler extends AbstractJsonTypeHandler<Object> {

    private static final ObjectMapper READER = JsonMappers.lenient();

    private final Class<?> targetType;

    public PgJsonTypeHandler(Class<?> type) {
        super(type);
        this.targetType = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, toJson(parameter), java.sql.Types.OTHER);
    }

    @Override
    public String toJson(Object obj) {
        return READER.writeValueAsString(obj);
    }

    /**
     * 读路径 jsonb 规范化：PG jsonb 不保留键序，对象键按（长度, 字节序）排序
     * （H2 的 VARCHAR 原样返回插入串，这里模拟 jsonb 行为使两侧读回一致；
     * 仅 JsonNode 目标需要——值类型配置反序列化后键序本就丢失）。
     */
    @Override
    public Object parse(String json) {
        if (!JsonNode.class.isAssignableFrom(targetType)) {
            return READER.readValue(json, targetType);
        }
        return canonicalize(READER.readTree(json));
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            var out = READER.createObjectNode();
            var names = new java.util.ArrayList<String>();
            obj.propertyNames().forEach(names::add);
            names.sort(java.util.Comparator
                    .comparingInt(String::length)
                    .thenComparing(java.util.Comparator.naturalOrder()));
            for (String name : names) {
                out.set(name, canonicalize(obj.get(name)));
            }
            return out;
        }
        if (node instanceof ArrayNode arr) {
            var out = READER.createArrayNode();
            for (var item : arr) {
                out.add(canonicalize(item));
            }
            return out;
        }
        return node;
    }
}
