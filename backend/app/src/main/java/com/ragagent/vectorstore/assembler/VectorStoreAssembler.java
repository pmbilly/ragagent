package com.ragagent.vectorstore.assembler;

import com.ragagent.vectorstore.controller.vo.VectorStoreRespVo;
import com.ragagent.vectorstore.controller.vo.VectorStoreTypeVo;
import com.ragagent.vectorstore.dto.VectorStoreResponse;
import com.ragagent.vectorstore.dto.VectorStoreTypes;
import java.util.List;

/** 向量库响应 DTO 到 HTTP VO 的装配。 */
public final class VectorStoreAssembler {

    private VectorStoreAssembler() {
    }

    /** 装配向量库资源；配置已由源 DTO 掩码，此处原样透传。 */
    public static VectorStoreRespVo toVo(VectorStoreResponse src) {
        if (src == null) {
            return null;
        }
        return new VectorStoreRespVo(
                src.id,
                src.tenantId,
                src.name,
                src.engineType,
                src.connectionConfig,
                src.indexConfig,
                src.createdAt,
                src.updatedAt,
                src.deletedAt,
                src.source,
                src.readOnly);
    }

    /** 批量装配向量库，保持原有顺序。 */
    public static List<VectorStoreRespVo> toVoList(List<VectorStoreResponse> src) {
        if (src == null) {
            return List.of();
        }
        return src.stream().map(VectorStoreAssembler::toVo).toList();
    }

    /** 装配类型元数据列表，顺序即注册表声明序。 */
    public static List<VectorStoreTypeVo> toTypeVoList(List<VectorStoreTypes.TypeInfo> src) {
        if (src == null) {
            return List.of();
        }
        return src.stream().map(VectorStoreAssembler::toTypeVo).toList();
    }

    /** 装配单个类型元数据。 */
    public static VectorStoreTypeVo toTypeVo(VectorStoreTypes.TypeInfo src) {
        if (src == null) {
            return null;
        }
        return new VectorStoreTypeVo(
                src.type,
                src.displayName,
                toFieldVoList(src.connectionFields),
                toFieldVoList(src.indexFields));
    }

    private static List<VectorStoreTypeVo.FieldInfoVo> toFieldVoList(List<VectorStoreTypes.FieldInfo> src) {
        if (src == null) {
            return null;
        }
        return src.stream()
                .map(f -> f == null
                        ? null
                        : new VectorStoreTypeVo.FieldInfoVo(
                                f.name, f.type, f.required, f.sensitive,
                                f.defaultValue, f.description, f.immutable,
                                f.min, f.max, f.enumValues))
                .toList();
    }
}
