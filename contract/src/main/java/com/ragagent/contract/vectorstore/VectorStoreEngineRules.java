package com.ragagent.contract.vectorstore;

import java.util.List;

/**
 * 单个向量店引擎的控制面规则：必填字段 + 需要过 SSRF 的地址字段。
 *
 * <p>规则由 {@code retrieval-engine} 的引擎注册表提供，业务域只按序执行；
 * 这样新增引擎时控制面与运行时共用同一份知识。</p>
 */
public record VectorStoreEngineRules(
        String engineType,
        List<VectorStoreRequiredField> requiredFields,
        List<VectorStoreAddressField> addressFields) {

    public VectorStoreEngineRules {
        requiredFields = requiredFields == null ? List.of() : List.copyOf(requiredFields);
        addressFields = addressFields == null ? List.of() : List.copyOf(addressFields);
    }
}
