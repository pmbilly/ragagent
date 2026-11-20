package com.ragagent.session.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态段 · 实体：抽取出的实体、实体所属知识库与实体知识映射。
 *
 * <p>写入口是 {@code QUERY_UNDERSTAND} 的实体抽取；检索段（含并发分支）只读。</p>
 */
public final class EntityContext {

    private List<String> entity;
    private List<String> entityKbIds;
    private Map<String, String> entityKnowledge;

    public List<String> getEntity() {
        return entity;
    }

    public void setEntity(List<String> v) {
        entity = v;
    }

    public List<String> getEntityKbIds() {
        return entityKbIds;
    }

    public void setEntityKbIds(List<String> v) {
        entityKbIds = v;
    }

    public Map<String, String> getEntityKnowledge() {
        return entityKnowledge;
    }

    public void setEntityKnowledge(Map<String, String> v) {
        entityKnowledge = v;
    }

    /** 克隆：容器换新，避免并发分支共享可变列表/映射。 */
    public void copyFrom(EntityContext other) {
        entity = other.entity == null ? new ArrayList<>() : new ArrayList<>(other.entity);
        entityKbIds = other.entityKbIds == null
                ? new ArrayList<>() : new ArrayList<>(other.entityKbIds);
        entityKnowledge = other.entityKnowledge == null
                ? new HashMap<>() : new LinkedHashMap<>(other.entityKnowledge);
    }
}
