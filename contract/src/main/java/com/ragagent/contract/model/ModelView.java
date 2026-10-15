package com.ragagent.contract.model;

import java.time.OffsetDateTime;

/**
 * 模型行**线格式视图**（契约面载荷）：管理/初始化面（`InitResponses` 等）与
 * {@link ModelCatalogPort} 的读写面都以此为口径。
 *
 * <p><b>为什么是它</b>：实体 {@code Model} 属 model 域内部（HANDOFF §12.0 判据 1）；
 * 本视图**访问器名与实体逐一相同**（含 `isIsDefault()`/`isIsBuiltin()`）⇒ 消费方零改动、
 * 且 **JSON 逐字节等价**（由 `init-post-initialize.json` / `model-*.json` 金片钉住）。
 * 字段集＝实体参与 JSON 的全部字段（实体无 {@code @JsonIgnore}）。</p>
 *
 * <p><b>可写说明</b>：DTO——适配器填充、管理面构造；与 {@code ChunkView} 同款。</p>
 */
public class ModelView {

    private String id;
    private Long tenantId;
    private String name;
    private String displayName;
    private String type;
    private String source;
    private String description;
    private ModelParameters parameters;
    private boolean isDefault;
    private boolean isBuiltin;
    private String managedBy;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ModelParameters getParameters() {
        return parameters;
    }

    public void setParameters(ModelParameters parameters) {
        this.parameters = parameters;
    }

    public boolean isIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isIsBuiltin() {
        return isBuiltin;
    }

    public void setIsBuiltin(boolean isBuiltin) {
        this.isBuiltin = isBuiltin;
    }

    public String getManagedBy() {
        return managedBy;
    }

    public void setManagedBy(String managedBy) {
        this.managedBy = managedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
