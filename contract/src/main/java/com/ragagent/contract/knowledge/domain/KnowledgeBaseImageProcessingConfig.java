package com.ragagent.contract.knowledge.domain;


/** ImageProcessingConfig：单字段 */
public class KnowledgeBaseImageProcessingConfig {

    private String modelId = "";

    public String getModelId() { return modelId; }
    public void setModelId(String v) { modelId = v == null ? "" : v; }

}
