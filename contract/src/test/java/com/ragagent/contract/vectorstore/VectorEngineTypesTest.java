package com.ragagent.contract.vectorstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引擎类型常量的一致性：全集 9 种，可注册为 DB 向量库的是 7 种，
 * 差额只允许是 env 引擎（postgres / sqlite）。
 */
class VectorEngineTypesTest {

    @Test
    @DisplayName("ALL 9 种 / CONFIGURABLE 7 种；差额恰为 postgres 与 sqlite")
    void configurableIsAllMinusEnvEngines() {
        assertEquals(9, VectorEngineTypes.ALL.size());
        assertEquals(7, VectorEngineTypes.CONFIGURABLE.size());
        assertTrue(VectorEngineTypes.ALL.containsAll(VectorEngineTypes.CONFIGURABLE),
                "可配置集合必须是全集子集");

        Set<String> envOnly = new HashSet<>(VectorEngineTypes.ALL);
        envOnly.removeAll(VectorEngineTypes.CONFIGURABLE);
        assertEquals(Set.of(VectorEngineTypes.POSTGRES, VectorEngineTypes.SQLITE), envOnly,
                "只有 postgres / sqlite 允许出现在 DB 白名单之外");
    }
}
