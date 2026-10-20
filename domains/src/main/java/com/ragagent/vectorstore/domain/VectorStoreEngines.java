package com.ragagent.vectorstore.domain;

import com.ragagent.contract.vectorstore.VectorEngineTypes;
import java.util.Set;

/**
 * 可注册为 DB 向量库的引擎白名单。
 *
 * <p>名单本身在契约 {@link VectorEngineTypes#CONFIGURABLE}——这里只是域侧引用，
 * 不再维护第二份字面量。postgres/sqlite 只能经 env store（RETRIEVE_DRIVER）出现。</p>
 */
public final class VectorStoreEngines {

    private VectorStoreEngines() {
    }

    public static final Set<String> VALID_ENGINE_TYPES = VectorEngineTypes.CONFIGURABLE;

    public static boolean isValidEngineType(String t) {
        return t != null && VALID_ENGINE_TYPES.contains(t);
    }
}
