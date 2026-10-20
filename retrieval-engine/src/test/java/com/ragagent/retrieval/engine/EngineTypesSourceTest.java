package com.ragagent.retrieval.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ragagent.contract.vectorstore.VectorEngineTypes;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引擎常量单一来源：{@link EngineTypes} 暴露的 {@code ENGINE_*} 集合必须与契约
 * {@link VectorEngineTypes#ALL} 完全一致。
 *
 * <p>本类别名只是稳定既有调用点；有人在本类重新写字面量（或漏掉新增类型）时这条会红。</p>
 */
class EngineTypesSourceTest {

    @Test
    @DisplayName("EngineTypes 的 ENGINE_* 常量集合 == 契约 ALL（不允许第二份字面量）")
    void engineConstantsComeFromContract() throws IllegalAccessException {
        Set<String> exposed = new HashSet<>();
        for (Field field : EngineTypes.class.getFields()) {
            if (field.getName().startsWith("ENGINE_") && field.getType() == String.class) {
                exposed.add((String) field.get(null));
            }
        }
        assertEquals(VectorEngineTypes.ALL, exposed);
    }
}
